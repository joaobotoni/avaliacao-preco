package com.botoni.avaliacaodepreco.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.botoni.avaliacaodepreco.data.entities.FaixaDistancia;

import java.util.List;

@Dao
public interface FaixaDistanciaDao {

    @Insert
    long insert(FaixaDistancia faixaDistancia);

    @Insert
    void insertAll(List<FaixaDistancia> faixas);

    @Update
    void update(FaixaDistancia faixaDistancia);

    @Delete
    void delete(FaixaDistancia faixaDistancia);

    @Query("SELECT * FROM faixa_distancia ORDER BY km_minimo ASC")
    List<FaixaDistancia> getAll();

    @Query("SELECT * FROM faixa_distancia WHERE id = :id")
    FaixaDistancia getById(long id);

    @Query("SELECT * FROM faixa_distancia " +
            "WHERE :distanciaKm >= km_minimo " +
            "  AND (km_maximo IS NULL OR :distanciaKm <= km_maximo) " +
            "ORDER BY km_minimo ASC " +
            "LIMIT 1")
    FaixaDistancia getFaixaPorDistancia(int distanciaKm);

    @Query("DELETE FROM faixa_distancia")
    void deleteAll();
}