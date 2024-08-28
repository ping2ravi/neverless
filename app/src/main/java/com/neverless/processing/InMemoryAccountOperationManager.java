package com.neverless.processing;

import com.neverless.domain.Account;
import com.neverless.domain.AccountId;
import com.neverless.domain.AccountRepository;
import com.neverless.domain.impl.AccountImpl;
import com.neverless.exceptions.NotFoundException;
import com.neverless.resources.dtos.WithdrawalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class InMemoryAccountOperationManager implements AccountOperationManager {


    private final AccountRepository accountRepository;
    private final int concurrency;
    private final Map<Integer, TaskQueue> queues;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public InMemoryAccountOperationManager(AccountRepository accountRepository, int concurrency) {
        queues = new HashMap<>();
        this.accountRepository = accountRepository;
        this.concurrency = concurrency;
        IntStream.range(0, concurrency).forEach(i ->
                {
                    ConcurrentLinkedQueue<AccountOperation> queue = new ConcurrentLinkedQueue<>();
                    AccountOperationTask task = new AccountOperationTask(queue, accountRepository);
                    Thread.startVirtualThread(task);
                    queues.put(i, new TaskQueue(queue, task));
                }
        );
    }

    @Override
    public void doAccountOperationAsync(AccountId accountId, Consumer<Account> operation, Consumer<Exception> errorHandler) {
        AccountOperation accountOperation = new AccountOperation(accountId, operation, errorHandler);
//        Queues are selected as per accountId, so that same account operation will always be enqueued in the same queue
//        and operations will run in serial fashion to make sure no locking on account object required
        queues.get(Math.abs(accountId.hashCode() % concurrency)).queue().add(accountOperation);
    }

    @Override
    public void validateAccountId(AccountId accountId) {
        accountRepository.find(accountId).orElseThrow(() -> new NotFoundException("Account %s is not found".formatted(accountId)));

    }

    @Override
    public Account getAccount(AccountId accountId) {
        return accountRepository.find(accountId).orElseThrow(() -> new NotFoundException("Account %s is not found".formatted(accountId)));
    }

    @Override
    public Account createAccount(AccountId accountId) {
        final var account = new AccountImpl(accountId);
        return accountRepository.save(account);
    }

    @Override
    public List<WithdrawalResponse> getWithdrawalStatus(AccountId accountId) {

        final var account = accountRepository.find(accountId).orElseThrow(() -> new NotFoundException("%s is not found".formatted(accountId)));

        return account.withdrawals().stream().map(it -> new WithdrawalResponse(it.withdrawalId(), it.status(), it.amount(), it.message())).toList();
    }

    @Override
    public void shutdown() {
        queues.forEach((key, task) -> {
            logger.info("Stopping thread %d".formatted(key));
            task.task().shutdownThread();
            if (!task.queue().isEmpty()) {
                logger.warn("%d Incomplete tasks in queue %d".formatted(task.queue().size(), key));
            }
        });
    }


    //    To Keep Queue and Thread together
    record TaskQueue(Queue<AccountOperation> queue, AccountOperationTask task) {
    }


    record AccountOperation(AccountId accountId, Consumer<Account> operation, Consumer<Exception> errorHandler) {
    }

    static class AccountOperationTask implements Runnable {


        public AccountOperationTask(ConcurrentLinkedQueue<AccountOperation> queue,
                                    AccountRepository accountRepository) {
            this.queue = queue;
            this.accountRepository = accountRepository;
        }

        private transient boolean running = true;
        private final ConcurrentLinkedQueue<AccountOperation> queue;
        private final AccountRepository accountRepository;

        @Override
        public void run() {
            while (running) {
                processTask();
            }
        }

        private void processTask() {
            AccountOperation accountOperation = queue.poll();
            if (accountOperation == null) {
                return;
            }

            try {
                var account = accountRepository.find(accountOperation.accountId())
                        .orElseThrow(() -> new NotFoundException("Account %s is not found".formatted(accountOperation.accountId())));
                accountOperation.operation().accept(account);
            } catch (Exception ex) {
                accountOperation.errorHandler().accept(ex);
            }
        }

        public void shutdownThread() {
            running = false;
        }
    }
}

