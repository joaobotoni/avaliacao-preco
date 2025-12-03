package com.botoni.avaliacaodepreco.ui.adapter;

import android.location.Address;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.R;

import java.util.List;

public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.ViewHolder> {
    public interface OnClickListener {
        void onItemClick(Address address);
    }

    private final List<Address> addresses;
    private final OnClickListener listener;

    public LocationAdapter(List<Address> addresses, OnClickListener listener) {
        this.addresses = addresses;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_localizacao, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(addresses.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return addresses.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewCidade;
        private final TextView textViewEstado;
        private final View separador;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewCidade = itemView.findViewById(R.id.cidade_text);
            textViewEstado = itemView.findViewById(R.id.estado_text);
            separador = itemView.findViewById(R.id.cidade_estado_separador);
        }

        public void bind(Address address, OnClickListener listener) {
            String cidade = sanitize(address.getLocality());
            String estado = sanitize(address.getAdminArea());

            setTextAndVisibility(textViewCidade, cidade);
            setTextAndVisibility(textViewEstado, estado);
            separador.setVisibility(isValid(cidade) ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> listener.onItemClick(address));
        }

        private String sanitize(String text) {
            return text != null ? text.trim() : "";
        }

        private boolean isValid(String text) {
            return text != null && !text.isEmpty();
        }

        private void setTextAndVisibility(TextView textView, String text) {
            textView.setText(text);
            textView.setVisibility(isValid(text) ? View.VISIBLE : View.GONE);
        }
    }
}