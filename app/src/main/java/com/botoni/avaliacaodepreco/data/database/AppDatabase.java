package com.botoni.avaliacaodepreco.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.botoni.avaliacaodepreco.data.entities.CapacidadeFrete;
import com.botoni.avaliacaodepreco.data.entities.CategoriaFrete;
import com.botoni.avaliacaodepreco.data.entities.Frete;
import com.botoni.avaliacaodepreco.data.entities.TipoVeiculoFrete;

import java.util.concurrent.Executors;

@Database(entities = {
        Frete.class,
        CapacidadeFrete.class,
        CategoriaFrete.class,
        TipoVeiculoFrete.class
}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "database"
                            )
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}