package com.botoni.avaliacaodepreco.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.botoni.avaliacaodepreco.data.dao.CapacidadeFreteDao;
import com.botoni.avaliacaodepreco.data.dao.CategoriaFreteDao;
import com.botoni.avaliacaodepreco.data.dao.FreteDao;
import com.botoni.avaliacaodepreco.data.dao.TipoVeiculoFreteDao;
import com.botoni.avaliacaodepreco.data.entities.CapacidadeFrete;
import com.botoni.avaliacaodepreco.data.entities.CategoriaFrete;
import com.botoni.avaliacaodepreco.data.entities.Frete;
import com.botoni.avaliacaodepreco.data.entities.TipoVeiculoFrete;

import java.util.concurrent.CompletableFuture;


@Database(entities = {
        Frete.class,
        CapacidadeFrete.class,
        CategoriaFrete.class,
        TipoVeiculoFrete.class
}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract FreteDao freteDao();

    public abstract CategoriaFreteDao categoriaFreteDao();

    public abstract CapacidadeFreteDao capacidadeFreteDao();

    public abstract TipoVeiculoFreteDao tipoVeiculoFreteDao();

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
                            .addCallback(new DatabaseCallback())
                            .fallbackToDestructiveMigration(false)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static class DatabaseCallback extends RoomDatabase.Callback {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            populate(db);
        }
    }

    private static void populate(@NonNull SupportSQLiteDatabase db) {
        CompletableFuture.runAsync(() -> {
            populateCategoriaFrete(db);
            populateTipoVeiculoFrete(db);
            populateCapacidadeFrete(db);
            populateFrete(db);
        });
    }

    //TIPOS DE CATEGORIA DE ANIMAIS
    private static void populateCategoriaFrete(SupportSQLiteDatabase db) {
        db.execSQL("INSERT INTO xgp_categoria_frete (descricao) VALUES ('Boi')");
        db.execSQL("INSERT INTO xgp_categoria_frete (descricao) VALUES ('Vaca')");
        db.execSQL("INSERT INTO xgp_categoria_frete (descricao) VALUES ('Bezerro')");
    }

    //TIPOS DE TRANSPORTES
    private static void populateTipoVeiculoFrete(SupportSQLiteDatabase db) {
        db.execSQL("INSERT INTO xgp_tipo_veiculo_frete (descricao) VALUES ('TRUK')");
        db.execSQL("INSERT INTO xgp_tipo_veiculo_frete (descricao) VALUES ('CARRETA BAIXA')");
        db.execSQL("INSERT INTO xgp_tipo_veiculo_frete (descricao) VALUES ('CARRETA ALTA')");
        db.execSQL("INSERT INTO xgp_tipo_veiculo_frete (descricao) VALUES ('CARRETA TRES EIXOS')");
    }

    private static void populateCapacidadeFrete(SupportSQLiteDatabase db) {
        //CAPACIDADE DE BOIS
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (1,1,1,18)");
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (1,2,19,26)");
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (1,3,27,36)");
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (1,4,37,45)");

        //CAPACIDADE DE VACAS
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (2,1,1,20)");
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (2,2,21,28)");
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (2,3,29,38)");
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (2,4,39,50)");

        //CAPACIDADE DE BEZERROS
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (3,1,1,38)");
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (3,2,39,55)");
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (3,3,56,75)");
        db.execSQL("INSERT INTO xgp_capacidade_frete VALUES (3,4,76,110)");
    }

    private static void populateFrete(SupportSQLiteDatabase db) {
        // TRUK
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (1,0,50,836.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (1,51,75,1067.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (1,76,100,1320.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (1,101,150,1640.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (1,151,200,1980.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (1,201,250,2390.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (1,251,300,2830.00)");

        // CARRETA BAIXA
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (2,0,50,1040.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (2,51,75,1350.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (2,76,100,1650.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (2,101,150,2200.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (2,151,200,2820.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (2,201,250,3390.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (2,251,300,3950.00)");

        // CARRETA ALTA
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (3,0,50,1250.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (3,51,75,1650.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (3,76,100,2050.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (3,101,150,2800.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (3,151,200,3500.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (3,201,250,4100.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (3,251,300,4700.00)");

        // CARRETA TRES EIXOS
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (4,0,50,1400.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (4,51,75,2000.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (4,76,100,2350.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (4,101,150,3400.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (4,151,200,4100.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (4,201,250,4700.00)");
        db.execSQL("INSERT INTO xgp_frete (id_tipo_veiculo_frete, km_inicial, km_final, valor) VALUES (4,251,300,5300.00)");
    }
}