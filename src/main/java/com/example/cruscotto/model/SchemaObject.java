package com.example.cruscotto.model;

import java.util.List;

public record SchemaObject(
    String name,
    String type,
    List<String> columns
) {}
