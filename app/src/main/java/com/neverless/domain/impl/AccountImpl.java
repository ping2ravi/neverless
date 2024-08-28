package com.neverless.domain.impl;

import com.neverless.domain.Account;
import com.neverless.domain.AccountId;
import com.neverless.integration.WithdrawalService.WithdrawalId;
import com.neverless.integration.WithdrawalService.WithdrawalState;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.neverless.domain.impl.AccountImpl.WithdrawalStatus.*;


/*
 * Not a threadsafe class, all access to it must be through AccountOperationManager
 */
public class AccountImpl implements Account {

    private final AccountId accountId;
    //    Keeping two balances as balance is updated later via separate thread and during that time we can show the client full balance,
//    but some balance is locked for pending withdrawals
//   Although all updates are done via single thread for any account object but reads can be done concurrently,
//  so while single thread is updating two values, it is possible that a read thread read the balance which is just updated by updating thread
//  and read lockedBalance before updating thread finish updating lockedBalance. This way we will always have atomic read,
//  i.e. either previous values or new values.
//    Also Atomic reference update operation are not checked for return value false/true as we expect it will always be true,
//    as only one thread is updating at any time and there is no scenario(as per current implementation) that it happens concurrently.
    private final AtomicReference<AccountBalance> accountBalanceAtomicReference;

    private final Map<WithdrawalId, Withdrawal> withdrawals;

    public AccountImpl(AccountId accountId) {
        this(accountId, 0L);
    }

    public AccountImpl(AccountId accountId, Long initialBalance) {
        this.withdrawals = new LinkedHashMap<>();
        this.accountBalanceAtomicReference = new AtomicReference<>(new AccountBalance(initialBalance, 0L));
        this.accountId = accountId;
    }

    @Override
    public AccountId id() {
        return accountId;
    }

    @Override
    public AccountBalance accountBalance() {
        return accountBalanceAtomicReference.get();
    }


    @Override
    public void addToBalance(Long amount) {
        AccountBalance currentAccountBalance = accountBalanceAtomicReference.get();
        AccountBalance newAccountbalance = new AccountBalance(currentAccountBalance.balance + amount, currentAccountBalance.lockedBalance);
        accountBalanceAtomicReference.compareAndSet(currentAccountBalance, newAccountbalance);
    }

    @Override
    public WithdrawalId withdraw(String addressId, Long amount) {
        WithdrawalId withdrawalId = createWithdrawal(addressId, amount);
        AccountBalance currentAccountBalance = accountBalanceAtomicReference.get();
        if (currentAccountBalance.balance - currentAccountBalance.lockedBalance < amount) {
            Withdrawal withdrawal = withdrawals.get(withdrawalId);
            String message = "You do not have enough balance to cover the withdrawal of amount %d".formatted(amount);
            Withdrawal updatedWithdrawal = new Withdrawal(withdrawal.withdrawalId(), withdrawal.addressId(), withdrawal.amount(), ERROR, message);
            withdrawals.put(withdrawalId, updatedWithdrawal);
        } else {
            AccountBalance newAccountbalance = new AccountBalance(currentAccountBalance.balance, currentAccountBalance.lockedBalance + amount);
            accountBalanceAtomicReference.compareAndSet(currentAccountBalance, newAccountbalance);
        }
        return withdrawalId;
    }

    @NotNull
    private WithdrawalId createWithdrawal(String addressId, Long amount) {
        WithdrawalId withdrawalId;
        while (true) {
//            While loop just to make sure we don't get extreme edge case where two generated UUIDs are same.
            withdrawalId = WithdrawalId.random();
            Withdrawal existingWithdrawal = withdrawals.putIfAbsent(withdrawalId, new Withdrawal(withdrawalId, addressId, amount));
            if (existingWithdrawal == null) {
                break;
            }
        }
        return withdrawalId;
    }

    @Override
    public void updateWithdrawalStatus(WithdrawalId withdrawalId, WithdrawalStatus withdrawalStatus) {
        Withdrawal withdrawal = withdrawals.get(withdrawalId);
        if (withdrawal.status() != PENDING && withdrawal.status() != PROCESSING) {
            return;
        }
        AccountBalance currentAccountBalance = accountBalanceAtomicReference.get();
        if (withdrawalStatus == WithdrawalStatus.SUCCESS) {
            AccountBalance newAccountbalance = new AccountBalance(currentAccountBalance.balance - withdrawal.amount(), currentAccountBalance.lockedBalance - withdrawal.amount());
            accountBalanceAtomicReference.compareAndSet(currentAccountBalance, newAccountbalance);
        }
        if (withdrawalStatus == ERROR) {
            AccountBalance newAccountbalance = new AccountBalance(currentAccountBalance.balance, currentAccountBalance.lockedBalance - withdrawal.amount());
            accountBalanceAtomicReference.compareAndSet(currentAccountBalance, newAccountbalance);
        }
        Withdrawal updatedWithdrawal = new Withdrawal(withdrawal.withdrawalId(), withdrawal.addressId(), withdrawal.amount(), withdrawalStatus, "");
        withdrawals.put(withdrawalId, updatedWithdrawal);
    }

    @Override
    public Collection<Withdrawal> withdrawals() {
        return withdrawals.values();
    }

    public record AccountBalance(Long balance, Long lockedBalance) {

    }

    public record Withdrawal(WithdrawalId withdrawalId, String addressId, Long amount,
                             WithdrawalStatus status,
                             String message) {

        public Withdrawal(WithdrawalId withdrawalId, String addressId, Long amount) {
            this(withdrawalId, addressId, amount, PENDING, "");
        }


    }

    public enum WithdrawalStatus {
        PENDING,
        PROCESSING,
        ERROR,
        SUCCESS;

        public static WithdrawalStatus of(WithdrawalState withdrawalState) {
            return switch (withdrawalState) {
                case WithdrawalState.PROCESSING -> WithdrawalStatus.PROCESSING;
                case WithdrawalState.COMPLETED -> WithdrawalStatus.SUCCESS;
                case WithdrawalState.FAILED -> WithdrawalStatus.ERROR;
            };
        }


    }
}
