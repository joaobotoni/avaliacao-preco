package com.botoni.avaliacaodepreco.data.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tabela_frete")
public class TabelaFrete {
    @PrimaryKey(autoGenerate = true)
    private long id;
    @ColumnInfo(name = "tipo_transporte")
    private TipoTransporte tipoTransporte;

    @ColumnInfo(name = "faixa_distancia_id")
    private long faixaDistanciaId;

    @ColumnInfo(name = "capacidade_transporte_id")
    private long capacidadeTransporteId;

    @ColumnInfo(name = "valor_frete")
    private double valorFrete;

    public TabelaFrete(TipoTransporte tipoTransporte, long faixaDistanciaId, long capacidadeTransporteId, double valorFrete) {
        this.tipoTransporte = tipoTransporte;
        this.faixaDistanciaId = faixaDistanciaId;
        this.capacidadeTransporteId = capacidadeTransporteId;
        this.valorFrete = valorFrete;
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

    public long getFaixaDistanciaId() {
        return faixaDistanciaId;
    }

    public void setFaixaDistanciaId(long faixaDistanciaId) {
        this.faixaDistanciaId = faixaDistanciaId;
    }

    public long getCapacidadeTransporteId() {
        return capacidadeTransporteId;
    }

    public void setCapacidadeTransporteId(long capacidadeTransporteId) {
        this.capacidadeTransporteId = capacidadeTransporteId;
    }

    public double getValorFrete() {
        return valorFrete;
    }

    public void setValorFrete(double valorFrete) {
        this.valorFrete = valorFrete;
    }
}

