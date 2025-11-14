package com.botoni.avaliacaodepreco.data.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "frete")
public class Frete {
    @PrimaryKey(autoGenerate = true)
    private long id;
    @ColumnInfo(name = "tipo_transporte")
    private TipoTransporte tipoTransporte;

    @ColumnInfo(name = "distancia_km")
    private int distanciaKm;

    @ColumnInfo(name = "valor_calculado")
    private double valorCalculado;

    @ColumnInfo(name = "data_frete")
    private long dataFrete;

    @ColumnInfo(name = "observacoes")
    private String observacoes;

    public Frete(TipoTransporte tipoTransporte, int distanciaKm, double valorCalculado) {
        this.tipoTransporte = tipoTransporte;
        this.distanciaKm = distanciaKm;
        this.valorCalculado = valorCalculado;
        this.dataFrete = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public TipoTransporte getTipoTransporte() {
        return tipoTransporte;
    }

    public void setTipoTransporte(TipoTransporte tipoTransporte) {
        this.tipoTransporte = tipoTransporte;
    }

    public int getDistanciaKm() {
        return distanciaKm;
    }

    public void setDistanciaKm(int distanciaKm) {
        this.distanciaKm = distanciaKm;
    }

    public double getValorCalculado() {
        return valorCalculado;
    }

    public void setValorCalculado(double valorCalculado) {
        this.valorCalculado = valorCalculado;
    }

    public long getDataFrete() {
        return dataFrete;
    }

    public void setDataFrete(long dataFrete) {
        this.dataFrete = dataFrete;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }
}