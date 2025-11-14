package com.botoni.avaliacaodepreco.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.botoni.avaliacaodepreco.data.entities.Frete;
import com.botoni.avaliacaodepreco.data.entities.TipoTransporte;
import java.util.List;

@Dao
public interface FreteDao {
    @Insert
    long insert(Frete frete);

    @Insert
    void insertAll(List<Frete> fretes);

    @Update
    void update(Frete frete);

    @Delete
    void delete(Frete frete);

    @Query("SELECT * FROM frete ORDER BY data_frete DESC")
    List<Frete> getAll();

    @Query("SELECT * FROM frete WHERE id = :id")
    Frete getById(long id);

    @Query("SELECT * FROM frete WHERE tipo_transporte = :tipoTransporte ORDER BY data_frete DESC")
    List<Frete> getByTipoTransporte(TipoTransporte tipoTransporte);

    @Query("SELECT * FROM frete WHERE data_frete BETWEEN :dataInicio AND :dataFim ORDER BY data_frete DESC")
    List<Frete> getByPeriodo(long dataInicio, long dataFim);

    @Query("SELECT * FROM frete WHERE distancia_km BETWEEN :kmMin AND :kmMax ORDER BY data_frete DESC")
    List<Frete> getByFaixaDistancia(int kmMin, int kmMax);

    @Query("SELECT SUM(valor_calculado) FROM frete WHERE data_frete BETWEEN :dataInicio AND :dataFim")
    Double getTotalValorPorPeriodo(long dataInicio, long dataFim);

    @Query("SELECT COUNT(*) FROM frete WHERE tipo_transporte = :tipoTransporte")
    int countByTipoTransporte(TipoTransporte tipoTransporte);

    @Query("DELETE FROM frete")
    void deleteAll();

    @Query("DELETE FROM frete WHERE id = :id")
    void deleteById(long id);
}