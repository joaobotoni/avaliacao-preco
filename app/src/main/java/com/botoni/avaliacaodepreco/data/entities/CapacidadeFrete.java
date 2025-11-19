package com.botoni.avaliacaodepreco.data.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "xgp_capacidade_frete")
public class CapacidadeFrete {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_capacidade_frete")
    private Long id;
    @ColumnInfo(name = "id_categoria_frete")
    private Long idCategoriaFrete;
    @ColumnInfo(name = "id_tipo_veiculo_frete")
    private Long idTipoVeiculoFrete;
    @ColumnInfo(name = "qtde_inicial")
    private Integer qtdeInicial;
    @ColumnInfo(name = "qtde_final")
    private Integer qtdeFinal;

    public CapacidadeFrete() {
    }

    public CapacidadeFrete(Long id, Long idCategoriaFrete, Long idTipoVeiculoFrete, Integer qtdeInicial, Integer qtdeFinal) {
        this.id = id;
        this.idCategoriaFrete = idCategoriaFrete;
        this.idTipoVeiculoFrete = idTipoVeiculoFrete;
        this.qtdeInicial = qtdeInicial;
        this.qtdeFinal = qtdeFinal;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getIdCategoriaFrete() {
        return idCategoriaFrete;
    }

    public void setIdCategoriaFrete(Long idCategoriaFrete) {
        this.idCategoriaFrete = idCategoriaFrete;
    }
    public Long getIdTipoVeiculoFrete() {
        return idTipoVeiculoFrete;
    }

    public void setIdTipoVeiculoFrete(Long idTipoVeiculoFrete) {
        this.idTipoVeiculoFrete = idTipoVeiculoFrete;
    }

    public Integer getQtdeInicial() {
        return qtdeInicial;
    }

    public void setQtdeInicial(Integer qtdeInicial) {
        this.qtdeInicial = qtdeInicial;
    }

    public Integer getQtdeFinal() {
        return qtdeFinal;
    }

    public void setQtdeFinal(Integer qtdeFinal) {
        this.qtdeFinal = qtdeFinal;
    }
}