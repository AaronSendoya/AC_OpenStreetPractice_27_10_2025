// app/src/main/java/com/example/osmrouteapp/ui/SearchFormFragment.java
package com.example.osmrouteapp.ui;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.osmrouteapp.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class SearchFormFragment extends Fragment {

    public interface OnSearchListener {
        void onSearchAddressSubmit(String addressText);
        void onClearMarkersRequested();
        void onSuggestionRequest(String query);
    }

    private OnSearchListener listener;
    private AutoCompleteTextView etAddress;
    private MaterialButton btnBuscarAgregar;
    private MaterialButton btnLimpiar;

    private MaterialCardView cardSuggestions;
    private RecyclerView rvSuggestions;
    private SuggestionAdapter suggestionAdapter;

    private final Handler debounceHandler = new Handler();
    private Runnable debounceRunnable;

    @Override
    public void onAttach(android.content.Context context) {
        super.onAttach(context);
        if (context instanceof OnSearchListener) {
            listener = (OnSearchListener) context;
        } else {
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
        cardSuggestions = view.findViewById(R.id.cardSuggestions);
        rvSuggestions = view.findViewById(R.id.rvSuggestions);

        rvSuggestions.setLayoutManager(new LinearLayoutManager(getContext()));
        suggestionAdapter = new SuggestionAdapter();
        rvSuggestions.setAdapter(suggestionAdapter);

        etAddress.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                final String text = s.toString().trim();
                if (debounceRunnable != null) {
                    debounceHandler.removeCallbacks(debounceRunnable);
                }
                debounceRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (text.length() >= 3 && listener != null) {
                            listener.onSuggestionRequest(text);
                        } else if (text.length() < 3 && cardSuggestions != null) {
                            cardSuggestions.setVisibility(View.GONE);
                        }
                    }
                };
                debounceHandler.postDelayed(debounceRunnable, 300);
            }
        });

        btnBuscarAgregar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String addressText = etAddress.getText().toString().trim();
                if (addressText.isEmpty()) {
                    Toast.makeText(getContext(), "Por favor ingrese una dirección", Toast.LENGTH_SHORT).show();
                } else {
                    if (listener != null) {
                        // Mandamos la dirección COMPLETA (clave para que marque bien)
                        listener.onSearchAddressSubmit(addressText);
                        etAddress.setText(""); // Limpiar después de enviar
                    }
                    if (cardSuggestions != null) {
                        cardSuggestions.setVisibility(View.GONE);
                    }
                }
            }
        });

        btnLimpiar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onClearMarkersRequested();
            }
        });

        return view;
    }

    public void updateSuggestions(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            if (cardSuggestions != null) cardSuggestions.setVisibility(View.GONE);
        } else {
            suggestionAdapter.updateSuggestions(suggestions);
            if (cardSuggestions != null) cardSuggestions.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (debounceRunnable != null) {
            debounceHandler.removeCallbacks(debounceRunnable);
        }
    }

    // ----------------- Adapter -----------------
    private class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.ViewHolder> {
        private final List<String> suggestions = new ArrayList<String>();

        public void updateSuggestions(List<String> newSuggestions) {
            suggestions.clear();
            suggestions.addAll(newSuggestions);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_suggestion, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final String suggestion = suggestions.get(position);

            // Mostrar título/subtítulo si viene “ | ”
            String[] parts = suggestion.split(" \\| ");
            holder.tvTitle.setText(parts.length > 0 ? parts[0] : suggestion);
            holder.tvSubtitle.setText(parts.length > 1 ? parts[1] : "");

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Usar la dirección COMPLETA
                    etAddress.setText(suggestion);
                    if (cardSuggestions != null) cardSuggestions.setVisibility(View.GONE);
                    if (listener != null) listener.onSearchAddressSubmit(suggestion);
                }
            });
        }

        @Override
        public int getItemCount() {
            return suggestions.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle;
            TextView tvSubtitle;
            ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            }
        }
    }
}
