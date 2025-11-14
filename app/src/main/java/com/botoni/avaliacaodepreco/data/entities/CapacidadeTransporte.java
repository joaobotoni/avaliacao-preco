package com.botoni.avaliacaodepreco.data.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "capacidade_transporte")
public class CapacidadeTransporte {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "tipo_transporte")
    private TipoTransporte tipoTransporte;

    @ColumnInfo(name = "quantidade_bois")
    private int quantidadeBois;

    @ColumnInfo(name = "quantidade_vacas")
    private int quantidadeVacas;

    @ColumnInfo(name = "quantidade_bezerros_minimo")
    private int quantidadeBezerrosMinimo;

    @ColumnInfo(name = "quantidade_bezerros_maximo")
    private int quantidadeBezerrosMaximo;

    public CapacidadeTransporte() {
    }

    public CapacidadeTransporte(TipoTransporte tipoTransporte, int quantidadeBois, int quantidadeVacas,
                                int quantidadeBezerrosMinimo, int quantidadeBezerrosMaximo) {
        this.tipoTransporte = tipoTransporte;
        this.quantidadeBois = quantidadeBois;
        this.quantidadeVacas = quantidadeVacas;
        this.quantidadeBezerrosMinimo = quantidadeBezerrosMinimo;
        this.quantidadeBezerrosMaximo = quantidadeBezerrosMaximo;
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

    public int getQuantidadeBois() {
        return quantidadeBois;
    }

    public void setQuantidadeBois(int quantidadeBois) {
        this.quantidadeBois = quantidadeBois;
    }

    public int getQuantidadeVacas() {
        return quantidadeVacas;
    }

    public void setQuantidadeVacas(int quantidadeVacas) {
        this.quantidadeVacas = quantidadeVacas;
    }

    public int getQuantidadeBezerrosMinimo() {
        return quantidadeBezerrosMinimo;
    }

    public void setQuantidadeBezerrosMinimo(int quantidadeBezerrosMinimo) {
        this.quantidadeBezerrosMinimo = quantidadeBezerrosMinimo;
    }

    public int getQuantidadeBezerrosMaximo() {
        return quantidadeBezerrosMaximo;
    }

    public void setQuantidadeBezerrosMaximo(int quantidadeBezerrosMaximo) {
        this.quantidadeBezerrosMaximo = quantidadeBezerrosMaximo;
    }
}