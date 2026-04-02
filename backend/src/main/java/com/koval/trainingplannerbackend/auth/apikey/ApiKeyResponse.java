package com.koval.trainingplannerbackend.auth.apikey;

/** Returned once at creation time — the only time the full key is visible. */
public record ApiKeyResponse(String id, String key, String prefix, String name) {}
