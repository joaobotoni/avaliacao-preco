package com.botoni.avaliacaodepreco.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.botoni.avaliacaodepreco.data.entities.TipoVeiculoFrete;

import java.util.List;

@Dao
public interface TipoVeiculoFreteDao {

    @Query("SELECT * FROM xgp_tipo_veiculo_frete")
    List<TipoVeiculoFrete> getAll();
    @Query("SELECT * FROM xgp_tipo_veiculo_frete WHERE id_tipo_veiculo_frete = :id LIMIT 1")
    TipoVeiculoFrete findById(long id);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TipoVeiculoFrete tipoVeiculo);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TipoVeiculoFrete> tiposVeiculo);
    @Update
    int update(TipoVeiculoFrete tipoVeiculo);
    @Delete
    int delete(TipoVeiculoFrete tipoVeiculo);
    @Query("DELETE FROM xgp_tipo_veiculo_frete")
    void deleteAll();
}