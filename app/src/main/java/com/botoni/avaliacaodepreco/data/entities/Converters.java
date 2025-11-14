package com.botoni.avaliacaodepreco.data.entities;

import androidx.room.TypeConverter;

public class Converters {
    @TypeConverter
    public String fromTipoTransporte(TipoTransporte value) {
        return value.name();
    }

    @TypeConverter
    public TipoTransporte toTipoTransporte(String value) {
        return TipoTransporte.valueOf(value);
    }
}
