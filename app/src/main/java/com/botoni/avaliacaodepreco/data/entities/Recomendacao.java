package com.botoni.avaliacaodepreco.data.entities;

public class Recomendacao {
    private int qtdeRecomendada;
    private String tipoTransporte;

    public Recomendacao(int qtdeRecomendada, String tipoTransporte) {
        this.qtdeRecomendada = qtdeRecomendada;
        this.tipoTransporte = tipoTransporte;
    }

    public int getQtdeRecomendada() {
        return qtdeRecomendada;
    }

    public void setQtdeRecomendada(int qtdeRecomendada) {
        this.qtdeRecomendada = qtdeRecomendada;
    }

    public String getTipoTransporte() {
        return tipoTransporte;
    }

    public void setTipoTransporte(String tipoTransporte) {
        this.tipoTransporte = tipoTransporte;
    }
}
