package com.neverless.integration;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

import static com.neverless.integration.WithdrawalService.WithdrawalState.COMPLETED;
import static com.neverless.integration.WithdrawalService.WithdrawalState.FAILED;
import static com.neverless.integration.WithdrawalService.WithdrawalState.PROCESSING;

/**
 * MUST BE USED AS IS AND NOT BE MODIFIED IN ANY WAY, WITH EXCEPTIONS TO T
 */
public class WithdrawalServiceStub<T> implements WithdrawalService<T> {
    private final ConcurrentMap<WithdrawalId, Withdrawal<T>> requests = new ConcurrentHashMap<>();

    @Override
    public void requestWithdrawal(WithdrawalId id, Address address, T amount) { // Please substitute T with preferred type
        final var existing = requests.putIfAbsent(id, new Withdrawal<>(finalState(), finaliseAt(), address, amount));
        if (existing != null && !Objects.equals(existing.address, address) && !Objects.equals(existing.amount, amount))
            throw new IllegalStateException("Withdrawal request with id[%s] is already present".formatted(id));
    }

    private WithdrawalState finalState() {
        return ThreadLocalRandom.current().nextBoolean() ? COMPLETED : FAILED;
    }

    private long finaliseAt() {
        return System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(1000, 10000);
    }

    @Override
    public WithdrawalState getRequestState(WithdrawalId id) {
        final var request = requests.get(id);
        if (request == null)
            throw new IllegalArgumentException("Request %s is not found".formatted(id));
        return request.finalState();
    }

    record Withdrawal<T>(WithdrawalState state, long finaliseAt, Address address, T amount) {
        public WithdrawalState finalState() {
            return finaliseAt <= System.currentTimeMillis() ? state : PROCESSING;
        }
    }
}

