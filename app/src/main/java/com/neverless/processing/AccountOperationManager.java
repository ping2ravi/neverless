package com.neverless.processing;

import com.neverless.domain.Account;
import com.neverless.domain.AccountId;
import com.neverless.resources.dtos.WithdrawalResponse;

import java.util.List;
import java.util.function.Consumer;

public interface AccountOperationManager {

    void doAccountOperationAsync(AccountId accountId, Consumer<Account> operation, Consumer<Exception> errorHandler);

    void validateAccountId(AccountId accountId);

    Account getAccount(AccountId accountId);

    Account createAccount(AccountId accountId);

    List<WithdrawalResponse> getWithdrawalStatus(AccountId accountId);

    void shutdown();

}
