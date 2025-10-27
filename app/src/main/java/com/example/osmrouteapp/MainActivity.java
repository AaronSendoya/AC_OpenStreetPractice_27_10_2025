package com.example.osmrouteapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.example.osmrouteapp.R;

public class MainActivity extends AppCompatActivity implements com.example.osmrouteapp.ui.SearchFormFragment.OnSearchListener {

    private com.example.osmrouteapp.ui.MapFragment mapFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onSearchAddressSubmit(String addressText) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        mapFragment = (com.example.osmrouteapp.ui.MapFragment) fragmentManager.findFragmentById(R.id.fragment_map_container);
        
        if (mapFragment != null) {
            mapFragment.addAddressMarker(addressText);
        }
    }

    @Override
    public void onClearMarkersRequested() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        mapFragment = (com.example.osmrouteapp.ui.MapFragment) fragmentManager.findFragmentById(R.id.fragment_map_container);
        
        if (mapFragment != null) {
            mapFragment.clearAll();
        }
    }
}
