package com.botoni.avaliacaodepreco.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.domain.Recomendacao;

import java.util.List;

public class RecomendacaoAdapter extends RecyclerView.Adapter<RecomendacaoAdapter.ViewHolder> {
    private final List<Recomendacao> recomendacoes;
    public RecomendacaoAdapter(List<Recomendacao> recomendacoes) {
        this.recomendacoes = recomendacoes;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recomendacao, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(recomendacoes.get(position));
    }

    @Override
    public int getItemCount() {
        return recomendacoes.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewQuantidade;
        private final TextView textViewTipoTransporte;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewQuantidade = itemView.findViewById(R.id.quantidade_text);
            textViewTipoTransporte = itemView.findViewById(R.id.tipo_transporte_text);
        }

        public void bind(Recomendacao recomendacao) {
            textViewQuantidade.setText(String.valueOf(recomendacao.getQtdeRecomendada()));
            textViewTipoTransporte.setText(recomendacao.getTipoTransporte());
        }
    }
}