package com.mapbox.mapboxsdk.testapp.activity.location;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ListPopupWindow;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.LocationComponentOptions;
import com.mapbox.mapboxsdk.location.OnCameraTrackingChangedListener;
import com.mapbox.mapboxsdk.location.OnLocationCameraTransitionListener;
import com.mapbox.mapboxsdk.location.OnLocationClickListener;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.testapp.R;

import java.util.ArrayList;
import java.util.List;

public class LocationModesActivity extends AppCompatActivity implements OnMapReadyCallback,
  OnLocationClickListener, OnCameraTrackingChangedListener {

  private MapView mapView;
  private Button locationModeBtn;
  private Button locationTrackingBtn;

  private PermissionsManager permissionsManager;

  private LocationComponent locationComponent;
  private MapboxMap mapboxMap;
  private boolean defaultStyle = false;

  private static final String SAVED_STATE_CAMERA = "saved_state_camera";
  private static final String SAVED_STATE_RENDER = "saved_state_render";
  private static final String SAVED_STATE_LOCATION = "saved_state_location";

  @CameraMode.Mode
  private int cameraMode = CameraMode.TRACKING;

  @RenderMode.Mode
  private int renderMode = RenderMode.NORMAL;

  private Location lastLocation;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_location_layer_mode);

    mapView = findViewById(R.id.mapView);

    locationModeBtn = findViewById(R.id.button_location_mode);
    locationModeBtn.setOnClickListener(v -> {
      if (locationComponent == null) {
        return;
      }
      showModeListDialog();
    });

    locationTrackingBtn = findViewById(R.id.button_location_tracking);
    locationTrackingBtn.setOnClickListener(v -> {
      if (locationComponent == null) {
        return;
      }
      showTrackingListDialog();
    });


    if (savedInstanceState != null) {
      cameraMode = savedInstanceState.getInt(SAVED_STATE_CAMERA);
      renderMode = savedInstanceState.getInt(SAVED_STATE_RENDER);
      lastLocation = savedInstanceState.getParcelable(SAVED_STATE_LOCATION);
    }

    mapView.onCreate(savedInstanceState);

    if (PermissionsManager.areLocationPermissionsGranted(this)) {
      mapView.getMapAsync(this);
    } else {
      permissionsManager = new PermissionsManager(new PermissionsListener() {
        @Override
        public void onExplanationNeeded(List<String> permissionsToExplain) {
          Toast.makeText(LocationModesActivity.this, "You need to accept location permissions.",
            Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPermissionResult(boolean granted) {
          if (granted) {
            mapView.getMapAsync(LocationModesActivity.this);
          } else {
            finish();
          }
        }
      });
      permissionsManager.requestLocationPermissions(this);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  @SuppressLint("MissingPermission")
  @Override
  public void onMapReady(@NonNull MapboxMap mapboxMap) {
    this.mapboxMap = mapboxMap;

    mapboxMap.setStyle(Style.MAPBOX_STREETS, style -> {
      locationComponent = mapboxMap.getLocationComponent();
      locationComponent.activateLocationComponent(
        LocationComponentActivationOptions
          .builder(this, style)
          .useDefaultLocationEngine(true)
          .locationEngineRequest(new LocationEngineRequest.Builder(750)
            .setFastestInterval(750)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build())
          .build());

      toggleStyle();
      locationComponent.setLocationComponentEnabled(true);
      locationComponent.addOnLocationClickListener(this);
      locationComponent.addOnCameraTrackingChangedListener(this);
      locationComponent.setCameraMode(cameraMode);
      setRendererMode(renderMode);
      locationComponent.forceLocationUpdate(lastLocation);
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_location_mode, menu);
    return true;
  }

  @SuppressLint("MissingPermission")
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (locationComponent == null) {
      return super.onOptionsItemSelected(item);
    }

    int id = item.getItemId();
    if (id == R.id.action_style_change) {
      toggleStyle();
      return true;
    } else if (id == R.id.action_map_style_change) {
      toggleMapStyle();
      return true;
    } else if (id == R.id.action_component_disable) {
      locationComponent.setLocationComponentEnabled(false);
      return true;
    } else if (id == R.id.action_component_enabled) {
      locationComponent.setLocationComponentEnabled(true);
      return true;
    } else if (id == R.id.action_gestures_management_disabled) {
      disableGesturesManagement();
      return true;
    } else if (id == R.id.action_gestures_management_enabled) {
      enableGesturesManagement();
      return true;
    } else if (id == R.id.action_component_throttling_enabled) {
      locationComponent.setMaxAnimationFps(5);
    } else if (id == R.id.action_component_throttling_disabled) {
      locationComponent.setMaxAnimationFps(Integer.MAX_VALUE);
    } else if (id == R.id.action_component_animate_while_tracking) {
      locationComponent.zoomWhileTracking(17, 750, new MapboxMap.CancelableCallback() {
        @Override
        public void onCancel() {
          // No impl
        }

        @Override
        public void onFinish() {
          locationComponent.tiltWhileTracking(60);
        }
      });
      if (locationComponent.getCameraMode() == CameraMode.NONE) {

        Toast.makeText(this, "Not possible to animate - not tracking", Toast.LENGTH_SHORT).show();
      }
    }

    return super.onOptionsItemSelected(item);
  }

  private void toggleStyle() {
    if (locationComponent == null) {
      return;
    }

    defaultStyle = !defaultStyle;
    LocationComponentOptions options = LocationComponentOptions.createFromAttributes(
      this, defaultStyle ? R.style.mapbox_LocationComponent : R.style.CustomLocationComponent);

    if (defaultStyle) {
      int[] padding;
      if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
        padding = new int[] {0, 750, 0, 0};
      } else {
        padding = new int[] {0, 250, 0, 0};
      }

      options = options.toBuilder()
        .padding(padding)
        .build();
    }

    locationComponent.applyStyle(options);
  }

  private void toggleMapStyle() {
    if (locationComponent == null) {
      return;
    }

    mapboxMap.getStyle(style -> {
      String styleUrl = Style.DARK.equals(style.getUri()) ? Style.LIGHT : Style.DARK;
      mapboxMap.setStyle(new Style.Builder().fromUri(styleUrl));
    });
  }

  private void disableGesturesManagement() {
    if (locationComponent == null) {
      return;
    }

    LocationComponentOptions options = locationComponent
      .getLocationComponentOptions()
      .toBuilder()
      .trackingGesturesManagement(false)
      .build();
    locationComponent.applyStyle(options);
  }

  private void enableGesturesManagement() {
    if (locationComponent == null) {
      return;
    }

    LocationComponentOptions options = locationComponent
      .getLocationComponentOptions()
      .toBuilder()
      .trackingGesturesManagement(true)
      .build();
    locationComponent.applyStyle(options);
  }

  @Override
  protected void onStart() {
    super.onStart();
    mapView.onStart();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mapView.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mapView.onPause();
  }

  @Override
  protected void onStop() {
    super.onStop();
    mapView.onStop();
  }

  @SuppressLint("MissingPermission")
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mapView.onSaveInstanceState(outState);
    outState.putInt(SAVED_STATE_CAMERA, cameraMode);
    outState.putInt(SAVED_STATE_RENDER, renderMode);
    if (locationComponent != null) {
      outState.putParcelable(SAVED_STATE_LOCATION, locationComponent.getLastKnownLocation());
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mapView.onDestroy();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }

  @Override
  public void onLocationComponentClick() {
    Toast.makeText(this, "OnLocationComponentClick", Toast.LENGTH_LONG).show();
  }

  private void showModeListDialog() {
    List<String> modes = new ArrayList<>();
    modes.add("Normal");
    modes.add("Compass");
    modes.add("GPS");
    ArrayAdapter<String> profileAdapter = new ArrayAdapter<>(this,
      android.R.layout.simple_list_item_1, modes);
    ListPopupWindow listPopup = new ListPopupWindow(this);
    listPopup.setAdapter(profileAdapter);
    listPopup.setAnchorView(locationModeBtn);
    listPopup.setOnItemClickListener((parent, itemView, position, id) -> {
      String selectedMode = modes.get(position);
      locationModeBtn.setText(selectedMode);
      if (selectedMode.contentEquals("Normal")) {
        setRendererMode(RenderMode.NORMAL);
      } else if (selectedMode.contentEquals("Compass")) {
        setRendererMode(RenderMode.COMPASS);
      } else if (selectedMode.contentEquals("GPS")) {
        setRendererMode(RenderMode.GPS);
      }
      listPopup.dismiss();
    });
    listPopup.show();
  }

  private void setRendererMode(@RenderMode.Mode int mode) {
    renderMode = mode;
    locationComponent.setRenderMode(mode);
    if (mode == RenderMode.NORMAL) {
      locationModeBtn.setText("Normal");
    } else if (mode == RenderMode.COMPASS) {
      locationModeBtn.setText("Compass");
    } else if (mode == RenderMode.GPS) {
      locationModeBtn.setText("Gps");
    }
  }

  private void showTrackingListDialog() {
    List<String> trackingTypes = new ArrayList<>();
    trackingTypes.add("None");
    trackingTypes.add("None Compass");
    trackingTypes.add("None GPS");
    trackingTypes.add("Tracking");
    trackingTypes.add("Tracking Compass");
    trackingTypes.add("Tracking GPS");
    trackingTypes.add("Tracking GPS North");
    ArrayAdapter<String> profileAdapter = new ArrayAdapter<>(this,
      android.R.layout.simple_list_item_1, trackingTypes);
    ListPopupWindow listPopup = new ListPopupWindow(this);
    listPopup.setAdapter(profileAdapter);
    listPopup.setAnchorView(locationTrackingBtn);
    listPopup.setOnItemClickListener((parent, itemView, position, id) -> {
      String selectedTrackingType = trackingTypes.get(position);
      locationTrackingBtn.setText(selectedTrackingType);
      if (selectedTrackingType.contentEquals("None")) {
        setCameraTrackingMode(CameraMode.NONE);
      } else if (selectedTrackingType.contentEquals("None Compass")) {
        setCameraTrackingMode(CameraMode.NONE_COMPASS);
      } else if (selectedTrackingType.contentEquals("None GPS")) {
        setCameraTrackingMode(CameraMode.NONE_GPS);
      } else if (selectedTrackingType.contentEquals("Tracking")) {
        setCameraTrackingMode(CameraMode.TRACKING);
      } else if (selectedTrackingType.contentEquals("Tracking Compass")) {
        setCameraTrackingMode(CameraMode.TRACKING_COMPASS);
      } else if (selectedTrackingType.contentEquals("Tracking GPS")) {
        setCameraTrackingMode(CameraMode.TRACKING_GPS);
      } else if (selectedTrackingType.contentEquals("Tracking GPS North")) {
        setCameraTrackingMode(CameraMode.TRACKING_GPS_NORTH);
      }
      listPopup.dismiss();
    });
    listPopup.show();
  }

  private void setCameraTrackingMode(@CameraMode.Mode int mode) {
    locationComponent.setCameraMode(mode, 1200, 16.0, null, 45.0,
      new OnLocationCameraTransitionListener() {
        @Override
        public void onLocationCameraTransitionFinished(@CameraMode.Mode int cameraMode) {
          Toast.makeText(LocationModesActivity.this, "Transition finished", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onLocationCameraTransitionCanceled(@CameraMode.Mode int cameraMode) {
          Toast.makeText(LocationModesActivity.this, "Transition canceled", Toast.LENGTH_SHORT).show();
        }
      });
  }

  @Override
  public void onCameraTrackingDismissed() {
    locationTrackingBtn.setText("None");
  }

  @Override
  public void onCameraTrackingChanged(int currentMode) {
    this.cameraMode = currentMode;
    if (currentMode == CameraMode.NONE) {
      locationTrackingBtn.setText("None");
    } else if (currentMode == CameraMode.NONE_COMPASS) {
      locationTrackingBtn.setText("None Compass");
    } else if (currentMode == CameraMode.NONE_GPS) {
      locationTrackingBtn.setText("None GPS");
    } else if (currentMode == CameraMode.TRACKING) {
      locationTrackingBtn.setText("Tracking");
    } else if (currentMode == CameraMode.TRACKING_COMPASS) {
      locationTrackingBtn.setText("Tracking Compass");
    } else if (currentMode == CameraMode.TRACKING_GPS) {
      locationTrackingBtn.setText("Tracking GPS");
    } else if (currentMode == CameraMode.TRACKING_GPS_NORTH) {
      locationTrackingBtn.setText("Tracking GPS North");
    }
  }
}