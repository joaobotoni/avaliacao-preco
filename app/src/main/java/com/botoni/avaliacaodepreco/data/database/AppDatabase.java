package com.botoni.avaliacaodepreco.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.botoni.avaliacaodepreco.data.dao.CapacidadeTransporteDao;
import com.botoni.avaliacaodepreco.data.dao.FaixaDistanciaDao;
import com.botoni.avaliacaodepreco.data.dao.FreteDao;
import com.botoni.avaliacaodepreco.data.dao.TabelaFreteDao;
import com.botoni.avaliacaodepreco.data.entities.CapacidadeTransporte;
import com.botoni.avaliacaodepreco.data.entities.Converters;
import com.botoni.avaliacaodepreco.data.entities.FaixaDistancia;
import com.botoni.avaliacaodepreco.data.entities.Frete;
import com.botoni.avaliacaodepreco.data.entities.TabelaFrete;

import java.util.concurrent.Executors;

@Database(entities = {
        FaixaDistancia.class,
        CapacidadeTransporte.class,
        TabelaFrete.class,
        Frete.class
}, version = 3, exportSchema = false)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    public abstract FaixaDistanciaDao faixaDistanciaDao();
    public abstract CapacidadeTransporteDao capacidadeTransporteDao();
    public abstract TabelaFreteDao tabelaFreteDao();
    public abstract FreteDao freteDao();

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
                            .fallbackToDestructiveMigration()
                            .addCallback(new Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        popularBancoDados(db);
                                    });
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static void popularBancoDados(SupportSQLiteDatabase db) {
        db.execSQL("INSERT INTO faixa_distancia (km_minimo, km_maximo, descricao) VALUES (0, 50, '0 - 50 KM')");
        db.execSQL("INSERT INTO faixa_distancia (km_minimo, km_maximo, descricao) VALUES (51, 75, '51 - 75 KM')");
        db.execSQL("INSERT INTO faixa_distancia (km_minimo, km_maximo, descricao) VALUES (76, 100, '76 - 100 KM')");
        db.execSQL("INSERT INTO faixa_distancia (km_minimo, km_maximo, descricao) VALUES (101, 150, '101 - 150 KM')");
        db.execSQL("INSERT INTO faixa_distancia (km_minimo, km_maximo, descricao) VALUES (151, 200, '151 - 200 KM')");
        db.execSQL("INSERT INTO faixa_distancia (km_minimo, km_maximo, descricao) VALUES (201, 250, '201 - 250 KM')");
        db.execSQL("INSERT INTO faixa_distancia (km_minimo, km_maximo, descricao) VALUES (251, 300, '251 - 300 KM')");
        db.execSQL("INSERT INTO faixa_distancia (km_minimo, km_maximo, descricao) VALUES (301, NULL, '+ DE 300 KM')");

        db.execSQL("INSERT INTO capacidade_transporte (tipo_transporte, quantidade_bois, quantidade_vacas, quantidade_bezerros_minimo, quantidade_bezerros_maximo) VALUES ('TRUCK', 18, 20, 35, 38)");
        db.execSQL("INSERT INTO capacidade_transporte (tipo_transporte, quantidade_bois, quantidade_vacas, quantidade_bezerros_minimo, quantidade_bezerros_maximo) VALUES ('CAIXA_BAIXA', 28, 28, 55, 55)");
        db.execSQL("INSERT INTO capacidade_transporte (tipo_transporte, quantidade_bois, quantidade_vacas, quantidade_bezerros_minimo, quantidade_bezerros_maximo) VALUES ('CAIXA_ALTA', 36, 38, 70, 75)");
        db.execSQL("INSERT INTO capacidade_transporte (tipo_transporte, quantidade_bois, quantidade_vacas, quantidade_bezerros_minimo, quantidade_bezerros_maximo) VALUES ('TRES_EIXOS', 45, 50, 90, 110)");

        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRUCK', 1, 1, 836.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRUCK', 2, 1, 1067.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRUCK', 3, 1, 1320.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRUCK', 4, 1, 1640.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRUCK', 5, 1, 1980.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRUCK', 6, 1, 2820.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRUCK', 7, 1, 3390.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRUCK', 8, 1, 9130.00)");

        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_BAIXA', 1, 2, 1040.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_BAIXA', 2, 2, 1350.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_BAIXA', 3, 2, 1650.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_BAIXA', 4, 2, 2050.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_BAIXA', 5, 2, 2200.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_BAIXA', 6, 2, 2820.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_BAIXA', 7, 2, 3390.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_BAIXA', 8, 2, 13100.00)");

        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_ALTA', 1, 3, 1250.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_ALTA', 2, 3, 1650.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_ALTA', 3, 3, 2050.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_ALTA', 4, 3, 2850.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_ALTA', 5, 3, 3500.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_ALTA', 6, 3, 4100.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_ALTA', 7, 3, 4700.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('CAIXA_ALTA', 8, 3, 17100.00)");

        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRES_EIXOS', 1, 4, 1400.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRES_EIXOS', 2, 4, 2000.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRES_EIXOS', 3, 4, 2350.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRES_EIXOS', 4, 4, 3400.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRES_EIXOS', 5, 4, 4100.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRES_EIXOS', 6, 4, 4700.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRES_EIXOS', 7, 4, 5300.00)");
        db.execSQL("INSERT INTO tabela_frete (tipo_transporte, faixa_distancia_id, capacidade_transporte_id, valor_frete) VALUES ('TRES_EIXOS', 8, 4, 17100.00)");
    }
}