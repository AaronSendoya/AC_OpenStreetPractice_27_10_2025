package com.example.osmrouteapp.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.osmrouteapp.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.location.GeocoderNominatim;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment {

    private MapView mapView;
    private IMapController mapController;
    private FloatingActionButton btnMyLocation;
    private FloatingActionButton btnRoute;
    private List<Marker> markers = new ArrayList<>();
    private Polyline currentRoadOverlay;

    static final int REQ_LOCATION = 1001;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Configuration.getInstance().setUserAgentValue(context.getString(R.string.user_agent));
    }

    @Nullable
    @Override
    public android.view.View onCreateView(@NonNull LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle savedInstanceState) {
        android.view.View view = inflater.inflate(R.layout.fragment_map, container, false);

        mapView = view.findViewById(R.id.map);
        btnMyLocation = view.findViewById(R.id.btnMyLocation);
        btnRoute = view.findViewById(R.id.btnRoute);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        mapController = mapView.getController();
        mapController.setZoom(15.0);
        GeoPoint startPoint = new GeoPoint(-17.7833, -63.1833);
        mapController.setCenter(startPoint);

        btnMyLocation.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                requestLocationPermissionAndCenter();
            }
        });

        btnRoute.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                if (markers.size() < 2) {
                    Toast.makeText(getContext(), "Agregue al menos 2 marcadores primero", Toast.LENGTH_SHORT).show();
                } else {
                    traceRoute();
                }
            }
        });

        return view;
    }

    public void addAddressMarker(String addressText) {
        new GeocodeTask().execute(addressText);
    }

    public void clearAll() {
        mapView.getOverlays().clear();
        markers.clear();
        currentRoadOverlay = null;
        mapView.invalidate();
    }

    private void requestLocationPermissionAndCenter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                return;
            }
        }
        centerOnMyLocation();
    }

    private void centerOnMyLocation() {
        LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        
        Location lastKnownLocation = null;
        if (locationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
            } else {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        }

        if (lastKnownLocation == null) {
            if (locationManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }
                } else {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            }
        }

        if (lastKnownLocation != null) {
            GeoPoint myLocation = new GeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
            mapController.setZoom(17.0);
            mapController.setCenter(myLocation);
            Toast.makeText(getContext(), "Centrado en su ubicación", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show();
        }
    }

    private void traceRoute() {
        int size = markers.size();
        if (size >= 2) {
            Marker marker1 = markers.get(size - 2);
            Marker marker2 = markers.get(size - 1);

            ArrayList<GeoPoint> waypoints = new ArrayList<>();
            waypoints.add(marker1.getPosition());
            waypoints.add(marker2.getPosition());

            new RouteTask().execute(waypoints);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                centerOnMyLocation();
            } else {
                Toast.makeText(getContext(), "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class GeocodeTask extends AsyncTask<String, Void, GeoPoint> {
        @Override
        protected GeoPoint doInBackground(String... params) {
            String address = params[0];
            try {
                GeocoderNominatim geocoder = new GeocoderNominatim(getString(R.string.user_agent));
                geocoder.setService("https://nominatim.openstreetmap.org/");
                List<Address> list = geocoder.getFromLocationName(address, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    GeoPoint gp = new GeoPoint(a.getLatitude(), a.getLongitude());
                    return gp;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(GeoPoint geoPoint) {
            if (geoPoint != null) {
                Marker marker = new Marker(mapView);
                marker.setPosition(geoPoint);
                marker.setTitle("Dirección encontrada");
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                mapView.getOverlays().add(marker);
                markers.add(marker);

                mapController.setCenter(geoPoint);
                mapController.setZoom(15.0);

                mapView.invalidate();
                Toast.makeText(getContext(), "Marcador agregado", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Dirección no encontrada", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class RouteTask extends AsyncTask<ArrayList<GeoPoint>, Void, Road> {
        @Override
        protected Road doInBackground(ArrayList<GeoPoint>... params) {
            ArrayList<GeoPoint> waypoints = params[0];
            try {
                RoadManager roadManager = new OSRMRoadManager(getContext());
                ((OSRMRoadManager) roadManager).setService("https://router.project-osrm.org/route/v1/");
                Road road = roadManager.getRoad(waypoints);
                return road;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Road road) {
            if (road != null && road.mStatus == Road.STATUS_OK) {
                if (currentRoadOverlay != null) {
                    mapView.getOverlays().remove(currentRoadOverlay);
                }

                currentRoadOverlay = RoadManager.buildRoadOverlay(road);
                mapView.getOverlays().add(currentRoadOverlay);

                mapView.invalidate();
                Toast.makeText(getContext(), "Ruta trazada", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Error al trazar la ruta", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }
}
