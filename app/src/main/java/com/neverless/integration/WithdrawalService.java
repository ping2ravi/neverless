package com.neverless.integration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Withdrawal Service represents an external 3rd party, which provides us with a custodial service for funds.
 * Provides a simple API to request funds move from treasury to given address.
 * <p>
 * MUST BE USED AS IS AND NOT BE MODIFIED IN ANY WAY, WITH EXCEPTIONS TO T
 */
public interface WithdrawalService<T> {
    /**
     * Request a withdrawal for given address and amount. Completes at random moment between 1 and 10 seconds
     *
     * @param id      - a caller generated withdrawal id, used for idempotency
     * @param address - an address withdraw to, can be any arbitrary string
     * @param amount  - an amount to withdraw (please replace T with type you want to use)
     * @throws IllegalArgumentException in case there's different address or amount for given id
     */
    void requestWithdrawal(WithdrawalId id, Address address, T amount); // Please substitute T with preferred type

    /**
     * Return current state of withdrawal
     *
     * @param id - a withdrawal id
     * @return current state of withdrawal
     * @throws IllegalArgumentException in case there no withdrawal for the given id
     */
    WithdrawalState getRequestState(WithdrawalId id);

    enum WithdrawalState {
        PROCESSING, COMPLETED, FAILED
    }

    record WithdrawalId(@JsonValue UUID value) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public WithdrawalId {
            requireNonNull(value, "WithdrawalId must not be null");
        }
        
        public static WithdrawalId of(UUID value) {
            return new WithdrawalId(value);
        }

        public static WithdrawalId random() {
            return of(UUID.randomUUID());
        }


    }

    record Address(@JsonValue String value) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Address {
            requireNonNull(value, "Address must not be null");
        }

        public static Address fromString(String value) {
            return new Address(value);
        }

    }
}
