package com.botoni.avaliacaodepreco.data.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "xgp_categoria_frete")

public class CategoriaFrete {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_categoria_frete")
    private Long id;
    @ColumnInfo(name = "descricao")
    private String descricao;

    public CategoriaFrete() {
    }

    public CategoriaFrete(String descricao) {
        this.descricao = descricao;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }
}

