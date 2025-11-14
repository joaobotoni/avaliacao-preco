package com.botoni.avaliacaodepreco.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.botoni.avaliacaodepreco.data.entities.CapacidadeTransporte;
import com.botoni.avaliacaodepreco.data.entities.TipoTransporte;

import java.util.List;

@Dao
public interface CapacidadeTransporteDao {

    @Insert
    long insert(CapacidadeTransporte capacidade);

    @Insert
    void insertAll(List<CapacidadeTransporte> capacidades);

    @Update
    void update(CapacidadeTransporte capacidade);

    @Delete
    void delete(CapacidadeTransporte capacidade);

    @Query("SELECT * FROM capacidade_transporte")
    List<CapacidadeTransporte> getAll();

    @Query("SELECT * FROM capacidade_transporte WHERE id = :id")
    CapacidadeTransporte getById(long id);

    @Query("SELECT * FROM capacidade_transporte WHERE tipo_transporte = :tipoTransporte")
    CapacidadeTransporte getByTipoTransporte(TipoTransporte tipoTransporte);

    @Query("DELETE FROM capacidade_transporte")
    void deleteAll();
}
