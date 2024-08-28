package com.neverless.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

public record AccountId(@JsonValue UUID value) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public AccountId {
        requireNonNull(value, "AccountId must not be null");
    }

    @Override
    public String toString() {
        return "AccountId(%s)".formatted(value);
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId fromString(String value) {
        return new AccountId(UUID.fromString(value));
    }

    public static AccountId random() {
        return of(UUID.randomUUID());
    }
}
