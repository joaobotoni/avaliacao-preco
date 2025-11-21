package com.botoni.avaliacaodepreco.data.entities;

public class Recomendacao {
    private final int qtdeRecomendada;
    private final String tipoTransporte;

    public Recomendacao(int qtdeRecomendada, String tipoTransporte) {
        this.qtdeRecomendada = qtdeRecomendada;
        this.tipoTransporte = tipoTransporte;
    }

    public int getQtdeRecomendada() {
        return qtdeRecomendada;
    }
    public String getTipoTransporte() {
        return tipoTransporte;
    }
}
