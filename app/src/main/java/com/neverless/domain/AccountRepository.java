package com.neverless.domain;

import java.util.Optional;

public interface AccountRepository {
    Account save(Account account);

    Optional<Account> find(AccountId id);
}
