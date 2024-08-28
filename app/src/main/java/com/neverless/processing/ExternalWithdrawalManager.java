package com.neverless.processing;

import com.neverless.domain.AccountId;
import com.neverless.domain.impl.AccountImpl.WithdrawalStatus;
import com.neverless.integration.WithdrawalService;
import com.neverless.integration.WithdrawalService.Address;
import com.neverless.integration.WithdrawalService.WithdrawalId;
import com.neverless.integration.WithdrawalService.WithdrawalState;
import kotlin.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

public class ExternalWithdrawalManager {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public ExternalWithdrawalManager(WithdrawalService<Long> withdrawalService) {
        this.withdrawalService = withdrawalService;
        this.withdrawalQueue = new ConcurrentLinkedQueue<>();
        statusTask = new StatusTask();
        Thread.startVirtualThread(statusTask);

    }

    private final WithdrawalService<Long> withdrawalService;
    private final ConcurrentLinkedQueue<Pair<AccountWithdrawalRequest, BiConsumer<AccountWithdrawalRequest, WithdrawalStatus>>> withdrawalQueue;
    private final StatusTask statusTask;


    public void withdraw(AccountWithdrawalRequest accountWithdrawalRequest, BiConsumer<AccountWithdrawalRequest, WithdrawalStatus> withdrawalCompletionHandler) {
        try {
            withdrawalService.requestWithdrawal(accountWithdrawalRequest.withdrawalId, accountWithdrawalRequest.address, accountWithdrawalRequest.amount);
        } catch (Exception ex) {
//            If unable to request withdrawal with external service, make sure update withdrawal status with in our system and update locked balance.
            withdrawalCompletionHandler.accept(accountWithdrawalRequest, WithdrawalStatus.ERROR);
        }
        withdrawalQueue.add(new Pair<>(accountWithdrawalRequest, withdrawalCompletionHandler));
    }

    public void shutdown() {
        logger.info("Stopping external withdraw status checker thread.");
        statusTask.shutdown();
        if (!withdrawalQueue.isEmpty()) {
            logger.warn("%d Incomplete tasks in withdrawal status queue".formatted(withdrawalQueue.size()));
        }

    }

    public record AccountWithdrawalRequest(AccountId accountId, WithdrawalId withdrawalId, Address address,
                                           Long amount) {

    }


    class StatusTask implements Runnable {

        private transient boolean running = true;
        private final Logger logger = LoggerFactory.getLogger(this.getClass());

        @Override
        public void run() {
            while (running) {
                checkWithdrawalStatus();
            }

        }

        private void checkWithdrawalStatus() {
            try {
                var finished = withdrawalQueue.stream().filter(it -> {
                            var currentState = withdrawalService.getRequestState(it.getFirst().withdrawalId());
                            if (currentState != WithdrawalState.PROCESSING) {
                                it.getSecond().accept(it.getFirst(), WithdrawalStatus.of(currentState));
                                return true;
                            }
                            return false;
                        }

                ).toList();
                withdrawalQueue.removeAll(finished);

            } catch (Exception ex) {
                logger.error("Error while checking status of withdrawal", ex);
            }
        }

        public void shutdown() {
            running = false;
        }

    }
}
