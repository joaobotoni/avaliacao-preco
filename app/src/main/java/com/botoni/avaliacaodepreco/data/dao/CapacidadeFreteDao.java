package com.botoni.avaliacaodepreco.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.botoni.avaliacaodepreco.data.entities.CapacidadeFrete;

import java.util.List;

@Dao
public interface CapacidadeFreteDao {

    @Query("SELECT * FROM xgp_capacidade_frete")
    List<CapacidadeFrete> getAll();

    @Query("SELECT * FROM xgp_capacidade_frete WHERE id_capacidade_frete = :id")
    CapacidadeFrete findById(long id);

    @Query("SELECT * FROM xgp_capacidade_frete WHERE id_categoria_frete = :id_categoria_frete ORDER BY qtde_final DESC")
    List<CapacidadeFrete> findByCategoria(long id_categoria_frete);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CapacidadeFrete capacidadeFrete);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CapacidadeFrete> capacidades);

    @Update
    int update(CapacidadeFrete capacidadeFrete);

    @Delete
    int delete(CapacidadeFrete capacidadeFrete);

    @Query("DELETE FROM xgp_capacidade_frete")
    void deleteAll();
}