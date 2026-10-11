package com.example.ragsystem.vector;

import java.util.Map;

public record VectorPoint(String id, float[] vector, Map<String, Object> payload) {
}
