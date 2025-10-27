package com.example.osmrouteapp.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.osmrouteapp.R;

public class SearchFormFragment extends Fragment {

    public interface OnSearchListener {
        void onSearchAddressSubmit(String addressText);
        void onClearMarkersRequested();
    }

    private OnSearchListener listener;
    private EditText etAddress;
    private Button btnBuscarAgregar;
    private Button btnLimpiar;

    @Override
    public void onAttach(android.content.Context context) {
        super.onAttach(context);
        try {
            listener = (OnSearchListener) context;
        } catch (ClassCastException e) {
            throw new IllegalStateException(context.toString() + " must implement OnSearchListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search_form, container, false);

        etAddress = view.findViewById(R.id.etAddress);
        btnBuscarAgregar = view.findViewById(R.id.btnBuscarAgregar);
        btnLimpiar = view.findViewById(R.id.btnLimpiar);

        btnBuscarAgregar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String addressText = etAddress.getText().toString().trim();
                if (addressText.isEmpty()) {
                    Toast.makeText(getContext(), "Por favor ingrese una dirección", Toast.LENGTH_SHORT).show();
                } else {
                    if (listener != null) {
                        listener.onSearchAddressSubmit(addressText);
                    }
                    etAddress.setText("");
                }
            }
        });

        btnLimpiar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClearMarkersRequested();
                }
            }
        });

        return view;
    }
}
