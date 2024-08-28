package com.neverless.resources;

import com.neverless.domain.Account;
import com.neverless.domain.AccountId;
import com.neverless.domain.impl.AccountImpl.AccountBalance;
import com.neverless.domain.impl.AccountImpl.WithdrawalStatus;
import com.neverless.integration.WithdrawalService;
import com.neverless.processing.AccountOperationManager;
import com.neverless.processing.ExternalWithdrawalManager;
import com.neverless.processing.ExternalWithdrawalManager.AccountWithdrawalRequest;
import com.neverless.resources.dtos.AddFundRequest;
import com.neverless.resources.dtos.CreateAccountRequest;
import com.neverless.resources.dtos.WithdrawalRequest;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class Accounts {
    private final AccountOperationManager accountOperationManager;
    private final ExternalWithdrawalManager externalWithdrawalManager;
    final private Logger logger = LoggerFactory.getLogger(this.getClass());

    public Accounts(AccountOperationManager accountOperationManager, ExternalWithdrawalManager externalWithdrawalManager) {
        this.accountOperationManager = accountOperationManager;
        this.externalWithdrawalManager = externalWithdrawalManager;
    }

    public void validateAccountId(Context context) {
        final var accountId = AccountId.fromString(context.pathParam("id"));
        accountOperationManager.validateAccountId(accountId);
    }

    public void getAccount(Context context) {
        final var accountId = AccountId.fromString(context.pathParam("id"));
        final var account = accountOperationManager.getAccount(accountId);
        context.json(AccountResponse.of(account)).status(200);
    }


    public void createAccount(Context context) {
        final var createAccountRequest = context.bodyAsClass(CreateAccountRequest.class);
        Account account = accountOperationManager.createAccount(createAccountRequest.accountId());
        context.json(AccountResponse.of(account)).status(200);
    }

    public void addFunds(Context context) {
        final var id = AccountId.fromString(context.pathParam("id"));
        final var addFundRequest = context.bodyAsClass(AddFundRequest.class);

        accountOperationManager.doAccountOperationAsync(id,
                (Account accountToBeUpdated) -> accountToBeUpdated.addToBalance(addFundRequest.amount()), getExceptionHandler(id));
        context.status(204);
    }

    public void createWithdrawal(Context context) {
        final var accountId = AccountId.fromString(context.pathParam("id"));
        final var withdrawalRequest = context.bodyAsClass(WithdrawalRequest.class);

        accountOperationManager.doAccountOperationAsync(accountId, (Account accountToBeUpdated) -> {

//            Create withdrawal with in internal System
            var withdrawalId = accountToBeUpdated.withdraw(withdrawalRequest.address(), withdrawalRequest.amount());
//            Create withdrawal with in external system
            AccountWithdrawalRequest accountWithdrawalRequest = new AccountWithdrawalRequest(
                    accountId,
                    withdrawalId,
                    WithdrawalService.Address.fromString(withdrawalRequest.address()),
                    withdrawalRequest.amount()
            );
            externalWithdrawalManager.withdraw(accountWithdrawalRequest, this::updateWithdrawalStatus);

        }, getExceptionHandler(accountId));


        context.status(204);
    }

    @NotNull
    private Consumer<Exception> getExceptionHandler(AccountId accountId) {
        return (Exception ex) -> {
//            More sophisticated error handling can be done here, i.e. add to deadletter queue etc
//            For now i am just reporting it to logs.
            logger.error("Unable to add funds for account %s".formatted(accountId), ex);
        };
    }

    private void updateWithdrawalStatus(AccountWithdrawalRequest accountWithdrawalRequest, WithdrawalStatus withdrawalStatus) {
        accountOperationManager.doAccountOperationAsync(accountWithdrawalRequest.accountId(),
                (Account accountToBeUpdated) -> accountToBeUpdated.updateWithdrawalStatus(accountWithdrawalRequest.withdrawalId(), withdrawalStatus), getExceptionHandler(accountWithdrawalRequest.accountId())
        );
    }

    public void getWithdrawalStatus(Context context) {
        final var accountId = AccountId.fromString(context.pathParam("id"));
        final var withdrawal = accountOperationManager.getWithdrawalStatus(accountId);

        context.json(withdrawal).status(200);
    }


    public record AccountResponse(AccountId id, Long balance, Long lockedBalance) {
        public static AccountResponse of(Account account) {
            AccountBalance accountBalance = account.accountBalance();
            return new AccountResponse(account.id(), accountBalance.balance(), accountBalance.lockedBalance());
        }
    }

}

