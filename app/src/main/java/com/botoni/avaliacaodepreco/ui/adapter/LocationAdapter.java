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
    private List<Address> addresses;
    private OnClickListener listener;

    public LocationAdapter(List<Address> addresses, OnClickListener listener) {
        this.addresses = addresses;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_localizacao, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Address address = addresses.get(position);
        holder.bind(address);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(address));
    }

    @Override
    public int getItemCount() {
        return addresses.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView tvCidade;
        private TextView tvEstado;
        private View vSeparador;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            tvCidade = itemView.findViewById(R.id.tv_cidade);
            tvEstado = itemView.findViewById(R.id.tv_estado);
            vSeparador = itemView.findViewById(R.id.v_separador);
        }

        public void bind(Address address) {
            String cidade = address.getLocality();
            String estado = address.getAdminArea();

            if (cidade != null) {
                cidade = cidade.trim();
            }
            if (estado != null) {
                estado = estado.trim();
            }

            boolean hasCidade = cidade != null && !cidade.isEmpty();

            if (hasCidade) {
                tvCidade.setText(cidade);
                tvCidade.setVisibility(View.VISIBLE);
                vSeparador.setVisibility(View.VISIBLE);
            } else {
                tvCidade.setVisibility(View.GONE);
                vSeparador.setVisibility(View.GONE);
            }

            tvEstado.setText(estado != null && !estado.isEmpty() ? estado : "");
        }
    }
}