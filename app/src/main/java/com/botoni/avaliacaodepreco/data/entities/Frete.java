package com.botoni.avaliacaodepreco.data.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "xgp_frete")
public class Frete {
    @ColumnInfo(name = "id_frete")
    @PrimaryKey(autoGenerate = true)
    private long id;
    @ColumnInfo(name = "id_tipo_veiculo_frete")
    private Integer idTipoVeiculoFrete;
    @ColumnInfo(name = "km_inicial")
    private int kmInicial;
    @ColumnInfo(name = "km_final")
    private int kmFinal;
    @ColumnInfo(name = "valor")
    private double valor;

    public Frete() {
    }

    public Frete(Integer idTipoVeiculoFrete, int kmInicial, int kmFinal, double valor) {
        this.idTipoVeiculoFrete = idTipoVeiculoFrete;
        this.kmInicial = kmInicial;
        this.kmFinal = kmFinal;
        this.valor = valor;
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

    public int getKmInicial() {
        return kmInicial;
    }

    public void setKmInicial(int kmInicial) {
        this.kmInicial = kmInicial;
    }

    public int getKmFinal() {
        return kmFinal;
    }

    public void setKmFinal(int kmFinal) {
        this.kmFinal = kmFinal;
    }

    public double getValor() {
        return valor;
    }

    public void setValor(double valor) {
        this.valor = valor;
    }
}