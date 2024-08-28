package com.neverless.resources;

import com.neverless.exceptions.DuplicateException;
import com.neverless.exceptions.NotFoundException;
import com.neverless.processing.AccountOperationManager;
import com.neverless.processing.ExternalWithdrawalManager;
import io.javalin.router.JavalinDefaultRouting;

public class Resources {
    private final Healthcheck healthcheck;
    private final Accounts accounts;

    public Resources(AccountOperationManager accountOperationManager, ExternalWithdrawalManager externalWithdrawalManager) {
        healthcheck = new Healthcheck();
        accounts = new Accounts(accountOperationManager, externalWithdrawalManager);
    }

    public void register(JavalinDefaultRouting router) {
        router.exception(NotFoundException.class, (ex, ctx) -> ctx.status(404).json(new HttpError(ex.getMessage())));
        router.exception(DuplicateException.class, (ex, ctx) -> ctx.status(409).json(new HttpError(ex.getMessage())));
        router.exception(Exception.class, (ex, ctx) -> ctx.status(500).json(new HttpError("Server Error: Unable to process your request at the moment")));

        router.before("/accounts/{id}/*", accounts::validateAccountId);

        router.get("/healthcheck", healthcheck::check);
        router.get("/accounts/{id}", accounts::getAccount);
//        Caller can send money from their account to an external withdrawal address through an API
        router.post("/accounts/{id}/withdrawals", accounts::createWithdrawal);
//        Caller can see operation/withdrawals progress
        router.get("/accounts/{id}/withdrawals", accounts::getWithdrawalStatus);

//        Created following endpoints to make sure i can test it and system can work functionally
//        Service to create an account
        router.post("/accounts", accounts::createAccount);
//        Service to add funds to account
        router.put("/accounts/{id}/funds", accounts::addFunds);


    }

    record HttpError(String message) {

    }

}

