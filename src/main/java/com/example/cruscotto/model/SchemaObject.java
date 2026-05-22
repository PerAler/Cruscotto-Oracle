package com.example.cruscotto.model;

import java.util.List;

public record SchemaObject(
    String name,
    String type,  // TABLE o VIEW
    List<String> columns
) {}
