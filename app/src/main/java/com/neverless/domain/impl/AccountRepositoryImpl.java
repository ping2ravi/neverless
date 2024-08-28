package com.neverless.domain.impl;

import com.neverless.domain.Account;
import com.neverless.domain.AccountId;
import com.neverless.domain.AccountRepository;
import com.neverless.exceptions.DuplicateException;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AccountRepositoryImpl implements AccountRepository {

    private final ConcurrentMap<AccountId, Account> accountStorage = new ConcurrentHashMap<>();

    @Override
    public Account save(Account account) {
        Account existingAccount = accountStorage.putIfAbsent(account.id(), account);
        if(existingAccount != null){
            throw new DuplicateException("Account with id %s already exists".formatted(account.id()));
        }

        return account;
    }

    @Override
    public Optional<Account> find(AccountId accountId) {
        return Optional.ofNullable(accountStorage.get(accountId));
    }
}
