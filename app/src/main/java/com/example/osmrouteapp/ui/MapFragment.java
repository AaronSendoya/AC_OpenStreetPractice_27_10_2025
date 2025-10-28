// app/src/main/java/com/example/osmrouteapp/ui/MapFragment.java
package com.example.osmrouteapp.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapFragment extends Fragment {

    private MapView mapView;
    private IMapController mapController;
    private FloatingActionButton btnMyLocation;
    private FloatingActionButton btnRoute;
    private final List<Marker> markers = new ArrayList<Marker>();
    private Polyline currentRoadOverlay;

    static final int REQ_LOCATION = 1001;

    public interface OnSuggestionsReceived {
        void onSuggestionsReceived(List<String> suggestions);
    }

    private OnSuggestionsReceived suggestionsCallback;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Importante para osmdroid/osmbonuspack
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
        GeoPoint startPoint = new GeoPoint(-17.7833, -63.1833); // Santa Cruz de la Sierra
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
                    Toast.makeText(getSafeContext(), "Agregue al menos 2 marcadores primero", Toast.LENGTH_SHORT).show();
                } else {
                    traceRoute();
                }
            }
        });

        return view;
    }

    private Context getSafeContext() {
        return (getContext() != null) ? getContext() : requireActivity();
    }

    /** MainActivity la usará para recibir las sugerencias y pasarlas al formulario */
    public void setSuggestionsCallback(OnSuggestionsReceived callback) {
        this.suggestionsCallback = callback;
    }

    /** Llamado por MainActivity desde SearchFormFragment */
    public void addAddressMarker(String addressText) {
        if (addressText == null) return;
        String address = addressText.trim();
        
        // Usar un thread pool executor en lugar de AsyncTask (deprecated)
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                geocodeAndAddMarker(address);
            }
        });
    }
    
    private void geocodeAndAddMarker(String addressText) {
        String q = addressText;
        String errorMessage = "";
        String userAgent = getString(R.string.user_agent);
        GeoPoint gp = null;
        
        // 1) Intento con bounding box local
        try {
            GeocoderNominatim geocoder = new GeocoderNominatim(userAgent);
            geocoder.setService("https://nominatim.openstreetmap.org/");

            double[] bb = getBoundingBox();
            List<Address> list = geocoder.getFromLocationName(q, 1, bb[0], bb[1], bb[2], bb[3], true);
            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);
                gp = new GeoPoint(a.getLatitude(), a.getLongitude());
            }
        } catch (Exception e) { 
            errorMessage = "Error en búsqueda local: " + e.getMessage();
        }

        // Si no se encontró, intentar con prefijo "Santa Cruz de la Sierra"
        if (gp == null) {
            try {
                GeocoderNominatim geocoder2 = new GeocoderNominatim(userAgent);
                geocoder2.setService("https://nominatim.openstreetmap.org/");

                List<Address> list2 = geocoder2.getFromLocationName("Santa Cruz de la Sierra, " + q, 1);
                if (list2 != null && !list2.isEmpty()) {
                    Address a = list2.get(0);
                    gp = new GeoPoint(a.getLatitude(), a.getLongitude());
                }
            } catch (Exception e) { 
                errorMessage += " Error en búsqueda con prefijo: " + e.getMessage();
            }
        }
        
        // Último intento: buscar sin ciudad
        if (gp == null) {
            try {
                GeocoderNominatim geocoder3 = new GeocoderNominatim(userAgent);
                geocoder3.setService("https://nominatim.openstreetmap.org/");

                List<Address> list3 = geocoder3.getFromLocationName(q, 1);
                if (list3 != null && !list3.isEmpty()) {
                    Address a = list3.get(0);
                    gp = new GeoPoint(a.getLatitude(), a.getLongitude());
                }
            } catch (Exception e) { 
                errorMessage += " Error en búsqueda general: " + e.getMessage();
            }
        }

        // Capturar variables finales para el Runnable del UI thread
        final GeoPoint finalGp = gp;
        final String finalErrorMessage = errorMessage;
        final String finalQ = q;

        // Actualizar UI en el hilo principal
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Context ctx = getSafeContext();
                    if (finalGp != null && mapView != null && mapController != null) {
                        Marker m = new Marker(mapView);
                        m.setPosition(finalGp);
                        m.setTitle(finalQ);
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                        mapView.getOverlays().add(m);
                        markers.add(m);

                        mapController.setCenter(finalGp);
                        mapController.setZoom(17.0);
                        mapView.invalidate();

                        Toast.makeText(ctx, "Marcador agregado: " + finalQ, Toast.LENGTH_SHORT).show();
                    } else {
                        String message = "Dirección no encontrada";
                        if (!finalErrorMessage.isEmpty()) {
                            message += ": " + finalErrorMessage;
                        }
                        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    /** Llamado por MainActivity desde SearchFormFragment */
    public void fetchSuggestions(String query) {
        if (query == null) return;
        String searchQuery = query.trim();
        
        // Usar un thread pool executor en lugar de AsyncTask (deprecated)
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                fetchAndReturnSuggestions(searchQuery);
            }
        });
    }
    
    private void fetchAndReturnSuggestions(String query) {
        List<String> suggestions = new ArrayList<String>();
        String userAgent = getString(R.string.user_agent);
        
        try {
            GeocoderNominatim geocoder = new GeocoderNominatim(userAgent);
            geocoder.setService("https://nominatim.openstreetmap.org/");

            double[] bb = getBoundingBox();
            List<Address> list = geocoder.getFromLocationName(query, 5, bb[0], bb[1], bb[2], bb[3], true);
            if (list != null) {
                for (Address a : list) {
                    String line = a.getAddressLine(0);
                    if (line == null || line.length() == 0) {
                        String name = (a.getFeatureName() != null) ? a.getFeatureName() : "";
                        String street = (a.getThoroughfare() != null) ? a.getThoroughfare() : "";
                        line = (street != null && street.length() > 0 && !street.equals(name)) ? (name + " | " + street) : name;
                    }
                    if (line != null && line.length() > 0) suggestions.add(line);
                }
            }

            if (suggestions.isEmpty()) {
                List<Address> list2 = geocoder.getFromLocationName("Santa Cruz de la Sierra, " + query, 5);
                if (list2 != null) {
                    for (Address a : list2) {
                        String line = a.getAddressLine(0);
                        if (line == null || line.length() == 0) {
                            String name = (a.getFeatureName() != null) ? a.getFeatureName() : "";
                            String street = (a.getThoroughfare() != null) ? a.getThoroughfare() : "";
                            line = (street != null && street.length() > 0 && !street.equals(name)) ? (name + " | " + street) : name;
                        }
                        if (line != null && line.length() > 0) suggestions.add(line);
                    }
                }
            }
        } catch (Exception e) { 
            // Ignorar errores de geocodificación
        }
        
        final List<String> finalSuggestions = suggestions;
        
        // Actualizar UI en el hilo principal
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (suggestionsCallback != null) {
                        suggestionsCallback.onSuggestionsReceived(finalSuggestions);
                    }
                }
            });
        }
    }

    public void clearAll() {
        if (mapView != null) {
            mapView.getOverlays().clear();
            mapView.invalidate();
        }
        markers.clear();
        currentRoadOverlay = null;
    }

    /** Bounding box ~0.5° alrededor de Santa Cruz (ajustable) */
    private double[] getBoundingBox() {
        return new double[]{
                -18.2833,  // minLat
                -63.6833,  // minLon
                -17.2833,  // maxLat
                -62.6833   // maxLon
        };
    }

    private void requestLocationPermissionAndCenter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Context ctx = getSafeContext();
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                return;
            }
        }
        centerOnMyLocation();
    }

    private void centerOnMyLocation() {
        Context ctx = getSafeContext();
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);

        Location last = null;
        try {
            if (lm != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
                if (last == null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                    last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            }
        } catch (SecurityException ignored) { }

        if (last != null) {
            GeoPoint gp = new GeoPoint(last.getLatitude(), last.getLongitude());
            mapController.setZoom(17.0);
            mapController.setCenter(gp);
            Toast.makeText(ctx, "Centrado en su ubicación", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(ctx, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show();
        }
    }

    private void traceRoute() {
        int size = markers.size();
        if (size >= 2) {
            Marker a = markers.get(size - 2);
            Marker b = markers.get(size - 1);

            final GeoPoint startPoint = a.getPosition();
            final GeoPoint endPoint = b.getPosition();

            // Usar un thread pool executor en lugar de AsyncTask (deprecated)
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    traceRouteInBackground(startPoint, endPoint);
                }
            });
        }
    }
    
    private void traceRouteInBackground(GeoPoint startPoint, GeoPoint endPoint) {
        Road road = null;
        
        try {
            ArrayList<GeoPoint> waypoints = new ArrayList<GeoPoint>();
            waypoints.add(startPoint);
            waypoints.add(endPoint);
            
            RoadManager rm = new OSRMRoadManager(getSafeContext());
            ((OSRMRoadManager) rm).setService("https://router.project-osrm.org/route/v1/");
            road = rm.getRoad(waypoints);
        } catch (Exception e) {
            // Ignorar errores de ruteo
        }
        
        final Road finalRoad = road;
        
        // Actualizar UI en el hilo principal
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Context ctx = getSafeContext();
                    if (finalRoad != null && finalRoad.mStatus == Road.STATUS_OK && mapView != null) {
                        if (currentRoadOverlay != null) {
                            mapView.getOverlays().remove(currentRoadOverlay);
                        }
                        currentRoadOverlay = RoadManager.buildRoadOverlay(finalRoad);
                        mapView.getOverlays().add(currentRoadOverlay);
                        mapView.invalidate();
                        Toast.makeText(ctx, "Ruta trazada", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ctx, "Error al trazar la ruta", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                centerOnMyLocation();
            } else {
                Toast.makeText(getSafeContext(), "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }
}
