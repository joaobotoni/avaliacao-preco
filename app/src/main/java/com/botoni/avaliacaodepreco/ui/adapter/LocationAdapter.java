package com.botoni.avaliacaodepreco.ui.adapter;

import android.location.Address;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.R;

import java.util.List;

public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.ViewHolder> {
    private List<Address> addresses;

    public LocationAdapter(List<Address> addresses) {
        this.addresses = addresses;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_localizacao, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(addresses.get(position));
    }

    @Override
    public int getItemCount() {
        return addresses.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView tvCidade;
        private TextView tvEstado;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            tvCidade = itemView.findViewById(R.id.tv_cidade);
            tvEstado = itemView.findViewById(R.id.tv_estado);
        }

        public void bind(Address address){
            tvCidade.setText(address.getLocality());
            tvEstado.setText(address.getAdminArea());
        }
    }
}
