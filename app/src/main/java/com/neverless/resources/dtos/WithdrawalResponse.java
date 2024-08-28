package com.neverless.resources.dtos;

import com.neverless.domain.impl.AccountImpl.WithdrawalStatus;
import com.neverless.integration.WithdrawalService.WithdrawalId;

public record WithdrawalResponse(WithdrawalId withdrawalId, WithdrawalStatus status, Long amount,
                                 String message) {
}
