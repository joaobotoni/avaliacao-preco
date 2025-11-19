package com.botoni.avaliacaodepreco.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.botoni.avaliacaodepreco.data.entities.Frete;

import java.util.List;

@Dao
public interface FreteDao {
    @Query("SELECT * FROM xgp_frete")
    List<Frete> getAll();
    @Query("SELECT * FROM xgp_frete WHERE id_frete = :id LIMIT 1")
    Frete findById(long id);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Frete frete);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Frete> fretes);
    @Update
    int update(Frete frete);
    @Delete
    int delete(Frete frete);
    @Query("DELETE FROM xgp_frete")
    void deleteAll();
}