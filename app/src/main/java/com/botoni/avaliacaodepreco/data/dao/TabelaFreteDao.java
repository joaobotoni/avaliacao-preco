package com.botoni.avaliacaodepreco.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.botoni.avaliacaodepreco.data.entities.TabelaFrete;
import com.botoni.avaliacaodepreco.data.entities.TipoTransporte;

import java.util.List;

@Dao
public interface TabelaFreteDao {

    @Insert
    long insert(TabelaFrete tabelaFrete);

    @Insert
    void insertAll(List<TabelaFrete> tabelas);

    @Update
    void update(TabelaFrete tabelaFrete);

    @Delete
    void delete(TabelaFrete tabelaFrete);

    @Query("SELECT * FROM tabela_frete")
    List<TabelaFrete> getAll();

    @Query("SELECT * FROM tabela_frete WHERE id = :id")
    TabelaFrete getById(long id);

    @Query("SELECT * FROM tabela_frete WHERE tipo_transporte = :tipoTransporte AND faixa_distancia_id = :faixaDistanciaId")
    TabelaFrete getByTipoEFaixa(TipoTransporte tipoTransporte, long faixaDistanciaId);

    @Query("SELECT * FROM tabela_frete WHERE tipo_transporte = :tipoTransporte")
    List<TabelaFrete> getByTipoTransporte(TipoTransporte tipoTransporte);

    @Query("SELECT valor_frete FROM tabela_frete WHERE tipo_transporte = :tipoTransporte AND faixa_distancia_id = :faixaDistanciaId LIMIT 1")
    Double getValorFrete(TipoTransporte tipoTransporte, long faixaDistanciaId);

    @Query("DELETE FROM tabela_frete")
    void deleteAll();
}
