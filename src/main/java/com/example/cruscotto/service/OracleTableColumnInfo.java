package com.example.cruscotto.service;

public record OracleTableColumnInfo(
        int position,
        String columnName,
        String dataType,
        Integer dataLength,
        Integer dataPrecision,
        Integer dataScale,
        String nullable
) {
}
