package com.neverless.domain;

import com.neverless.domain.impl.AccountImpl.AccountBalance;
import com.neverless.domain.impl.AccountImpl.Withdrawal;
import com.neverless.domain.impl.AccountImpl.WithdrawalStatus;
import com.neverless.integration.WithdrawalService.WithdrawalId;

import java.util.Collection;

public interface Account {
    AccountId id();

    AccountBalance accountBalance();

    void addToBalance(Long amount);

    WithdrawalId withdraw(String addressId, Long amount);

    void updateWithdrawalStatus(WithdrawalId withdrawalId, WithdrawalStatus withdrawalStatus);

    Collection<Withdrawal> withdrawals();

}
