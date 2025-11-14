package com.botoni.avaliacaodepreco.data.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "faixa_distancia")
public class FaixaDistancia {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "km_minimo")
    private int kmMinimo;

    @ColumnInfo(name = "km_maximo")
    private Integer kmMaximo;

    @ColumnInfo(name = "descricao")
    private String descricao;

    public FaixaDistancia() {
    }

    public FaixaDistancia(int kmMinimo, Integer kmMaximo, String descricao) {
        this.kmMinimo = kmMinimo;
        this.kmMaximo = kmMaximo;
        this.descricao = descricao;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getKmMinimo() {
        return kmMinimo;
    }

    public void setKmMinimo(int kmMinimo) {
        this.kmMinimo = kmMinimo;
    }

    public Integer getKmMaximo() {
        return kmMaximo;
    }

    public void setKmMaximo(Integer kmMaximo) {
        this.kmMaximo = kmMaximo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }
}