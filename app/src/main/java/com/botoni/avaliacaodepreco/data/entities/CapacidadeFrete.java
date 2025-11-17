package com.botoni.avaliacaodepreco.data.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "xgp_capacidade_frete")
public class CapacidadeFrete {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_capacidade_frete")
    private long id;
    @ColumnInfo(name = "id_tipo_veiculo_frete")
    private Integer idTipoVeiculoFrete;
    @ColumnInfo(name = "qtde_inicial")
    private Double qtdeInicial;
    @ColumnInfo(name = "qtde_final")
    private Double qtdeFinal;

    public CapacidadeFrete() {
    }
    public CapacidadeFrete(Integer idTipoVeiculoFrete, Double qtdeInicial, Double qtdeFinal) {
        this.idTipoVeiculoFrete = idTipoVeiculoFrete;
        this.qtdeInicial = qtdeInicial;
        this.qtdeFinal = qtdeFinal;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Integer getIdTipoVeiculoFrete() {
        return idTipoVeiculoFrete;
    }

    public void setIdTipoVeiculoFrete(Integer idTipoVeiculoFrete) {
        this.idTipoVeiculoFrete = idTipoVeiculoFrete;
    }

    public Double getQtdeInicial() {
        return qtdeInicial;
    }

    public void setQtdeInicial(Double qtdeInicial) {
        this.qtdeInicial = qtdeInicial;
    }

    public Double getQtdeFinal() {
        return qtdeFinal;
    }

    public void setQtdeFinal(Double qtdeFinal) {
        this.qtdeFinal = qtdeFinal;
    }
}