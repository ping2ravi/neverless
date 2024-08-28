package com.neverless.spec;

import com.neverless.domain.AccountId;
import com.neverless.domain.impl.AccountImpl;
import com.neverless.resources.dtos.AddFundRequest;
import com.neverless.resources.dtos.CreateAccountRequest;
import com.neverless.resources.dtos.WithdrawalRequest;
import com.neverless.resources.dtos.WithdrawalResponse;
import io.restassured.response.Response;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static java.time.temporal.ChronoUnit.SECONDS;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class AccountsSpec extends FunctionalSpec {


    protected AccountsSpec(ApplicationContext context) {
        super(context);
    }

    @Test
    void should_respond_with_account_when_created() {
        final var accountId = AccountId.random();

        // when
        createAccount(accountId);
        // then
        assertAccountBalances(accountId, 0L, 0L);
    }

    @Test
    void should_respond_with_error_when_try_to_reuse_same_account_id() {
        final var accountId = AccountId.random();

        // when
        createAccount(accountId);
        assertAccountBalances(accountId, 0L, 0L);

        Response createAccountResponseFail = createAccount(accountId);

        // then
        assertThat(createAccountResponseFail.statusCode()).isEqualTo(409);
        assertThatJson(createAccountResponseFail.body().asString()).isEqualTo(
                """
                        {
                            "message": "Account with id AccountId(%s) already exists"
                        }
                        """.formatted(accountId.value())
        );
    }

    @Test
    void should_respond_with_error_when_try_to_create_wthdrawal_for_non_existing_account() {
        final var accountId = AccountId.random();

        // when
        //Account Not created
        String address = UUID.randomUUID().toString();
        final var createWithdrawalResponse = createWithdrawal(accountId, address, 100L);
        // then
        assertThat(createWithdrawalResponse.statusCode()).isEqualTo(404);
        assertThatJson(createWithdrawalResponse.body().asString()).isEqualTo(
                """
                        {
                            "message": "Account AccountId(%s) is not found"
                        }
                        """.formatted(accountId.value())
        );
    }

    @Test
    void should_respond_with_account_on_accounts_get_when_exists() {
        final var accountId = AccountId.random();

        // Given
        createAccount(accountId);
        addFundsToAccount(accountId, 500L);

        // Then
        assertAccountBalances(accountId, 500L, 0L);
    }


    @Test
    void should_respond_with_404_on_accounts_get_when_not_exists() {
        final var accountId = AccountId.random();

        // when
        final var response = when().get("/accounts/{id}", accountId.value()).thenReturn();

        // then
        assertThat(response.statusCode()).isEqualTo(404);
        assertThatJson(response.body().asString()).isEqualTo(
                """
                        {
                                "message": "Account %s is not found"
                        }
                            """.formatted(accountId)
        );
    }


    @Test
    void should_respond_with_accepted_status_when_withdrawal_requested_and_not_enough_funds_in_account() {
        final var accountId = AccountId.random();

        // when
        createAccount(accountId);
        addFundsToAccount(accountId, 10L);
        String address = UUID.randomUUID().toString();
        final var response = createWithdrawal(accountId, address, 100L);

        // then
        assertThat(response.statusCode()).isEqualTo(204);
        assertThatJson(response.body().asString()).isEqualTo("");

        // and
        assertAccountBalances(accountId, 10L, 0L);
        checkWithdrawalStatusIsSuccessOrError(accountId, 100L, "You do not have enough balance to cover the withdrawal of amount 100");
//        Locked balance will be 0, doesnt matter if withdrawal was success or failure, in both cases it will be 0
        assertAccountBalances(accountId, 10L, 0L);

    }

    @Test
    void should_respond_with_accepted_status_when_withdrawal_requested_when_enough_funds_in_account() {
        final var accountId = AccountId.random();

        // when
        createAccount(accountId);
        addFundsToAccount(accountId, 5000L);
        String address = UUID.randomUUID().toString();
        final var response = createWithdrawal(accountId, address, 100L);

        // then
        assertThat(response.statusCode()).isEqualTo(204);
        assertThatJson(response.body().asString()).isEqualTo("");
    }


    @Test
    void withdrawal_status_should_respond_with_pending_status_when_withdrawal_requested_and_have_enough_funds_in_account() {
        final var accountId = AccountId.random();

        // when
        createAccount(accountId);
        Long accountBalance = 5000L;
        addFundsToAccount(accountId, accountBalance);
        String address = UUID.randomUUID().toString();
        Long withdrawalAmount = 100L;
        createWithdrawal(accountId, address, withdrawalAmount);

        // then
        assertAccountBalances(accountId, accountBalance, withdrawalAmount);
        final var withdrawalStatusResponse = when().get("/accounts/{id}/withdrawals", accountId.value()).thenReturn();
        assertThatJson(withdrawalStatusResponse.body().asString()).whenIgnoringPaths("[*].withdrawalId").isEqualTo(
                """
                        [
                                {
                                        "withdrawalId": "SomeRandomValueFromServerIgnoredFromAssertion",
                                        "status": "PENDING",
                                        "amount": %d,
                                        "message": ""
                                }
                        ]
                        """.formatted(withdrawalAmount)
        );

    }

    @Test
    void withdrawal_status_should_respond_with_pending_status_when_two_withdrawal_requested_and_have_enough_funds_in_account() {
        final var accountId = AccountId.random();

        // when
        createAccount(accountId);
        Long accountBalance = 5000L;
        addFundsToAccount(accountId, accountBalance);
        String address = UUID.randomUUID().toString();
        Long withdrawalAmount1 = 100L;
        createWithdrawal(accountId, address, withdrawalAmount1);
        Long withdrawalAmount2 = 300L;
        createWithdrawal(accountId, address, withdrawalAmount2);

        // then
        final var withdrawalStatusResponse = when().get("/accounts/{id}/withdrawals", accountId.value()).thenReturn();
        assertThatJson(withdrawalStatusResponse.body().asString()).whenIgnoringPaths("[*].withdrawalId").isEqualTo(
                """
                        [
                                {
                                        "withdrawalId": "SomeRandomValueFromServerIgnoredFromAssertion",
                                        "status": "PENDING",
                                        "amount": %s,
                                        "message": ""
                                },
                                {
                                        "withdrawalId": "SomeRandomValueFromServerIgnoredFromAssertion",
                                        "status": "PENDING",
                                        "amount": %s,
                                        "message": ""
                                }
                        ]
                        """.formatted(withdrawalAmount1, withdrawalAmount2)
        );

        assertAccountBalances(accountId, accountBalance, withdrawalAmount1 + withdrawalAmount2);

    }


    @Test
    void withdrawal_status_should_respond_with_success_status_when_withdrawal_requested_and_have_enough_funds_in_account_and_waited_enough() {
        final var accountId = AccountId.random();

        // when
        createAccount(accountId);
        Long accountBalance = 5000L;
        addFundsToAccount(accountId, accountBalance);
        String address = UUID.randomUUID().toString();
        Long withdrawalAmount = 100L;
        createWithdrawal(accountId, address, withdrawalAmount);


        // then
        assertAccountBalances(accountId, accountBalance, withdrawalAmount);

        await()
                .atLeast(Durations.FIVE_HUNDRED_MILLISECONDS)
                .atMost(Duration.of(11, SECONDS))//Max 11 Seconds as currently stub Withdrawal service will respond in max 10 seconds
                .with()
                .pollInterval(Durations.FIVE_HUNDRED_MILLISECONDS)
                .until(() -> checkWithdrawalStatusIsSuccessOrError(accountId, withdrawalAmount, ""));


        WithdrawalResponse withdrawalResponse = getWithdrawalStatus(accountId);
        Long expectedBalance = accountBalance;
//        Locked balance will be 0, doesn't matter if withdrawal was success or failure, in both cases it will be 0
        Long expectedLockedBalance = 0L;
        if (withdrawalResponse.status() == AccountImpl.WithdrawalStatus.SUCCESS) {
            expectedBalance = expectedBalance - withdrawalAmount;
        }

        assertAccountBalances(accountId, expectedBalance, expectedLockedBalance);

    }


    @Test
    void withdrawal_status_should_respond_with_pending_status_when_two_concurrent_withdrawal_requested_and_have_enough_funds_in_account() throws ExecutionException, InterruptedException {
        final var accountId = AccountId.random();

        // when
        createAccount(accountId);
        Long accountBalance = 5000L;
        addFundsToAccount(accountId, accountBalance);
        String address = UUID.randomUUID().toString();
        Long withdrawalAmount1 = 100L;
        FutureTask<Response> withdrawalAsync1 = createWithdrawalAsync(accountId, address, withdrawalAmount1);
        Long withdrawalAmount2 = 300L;
        FutureTask<Response> withdrawalAsync2 = createWithdrawalAsync(accountId, address, withdrawalAmount2);
        //Wait for both task to finish
        withdrawalAsync1.get();
        withdrawalAsync2.get();
        assertAccountBalances(accountId, accountBalance, withdrawalAmount1 + withdrawalAmount2);

        // then
        final var withdrawalStatusResponse = when().get("/accounts/{id}/withdrawals", accountId.value()).thenReturn();
        assertThatJson(withdrawalStatusResponse.body().asString()).when(IGNORING_ARRAY_ORDER).whenIgnoringPaths("[*].withdrawalId").isEqualTo(
                """
                        [
                                {
                                        "withdrawalId": "SomeRandomValueFromServerIgnoredFromAssertion",
                                        "status": "PENDING",
                                        "amount": %s,
                                        "message": ""
                                },
                                {
                                        "withdrawalId": "SomeRandomValueFromServerIgnoredFromAssertion",
                                        "status": "PENDING",
                                        "amount": %s,
                                        "message": ""
                                }
                        ]
                        """.formatted(withdrawalAmount1, withdrawalAmount2)
        );

        assertAccountBalances(accountId, accountBalance, withdrawalAmount1 + withdrawalAmount2);

    }

    private void assertAccountBalances(AccountId accountId, Long expectedBalance, Long lockedBalance) {
        final var response = when().get("/accounts/{id}", accountId.value()).thenReturn();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body().asString()).isEqualTo(
                """
                        {
                            "id": "%s",
                            "balance": %d,
                            "lockedBalance": %d
                        }
                        """.formatted(accountId.value(), expectedBalance, lockedBalance)
        );
    }

    private WithdrawalResponse getWithdrawalStatus(AccountId accountId) {
        final var withdrawalStatusResponse = when().get("/accounts/{id}/withdrawals", accountId.value()).thenReturn();
        List<WithdrawalResponse> withdrawalResponses = withdrawalStatusResponse.body().jsonPath().getList(".", WithdrawalResponse.class);
        return withdrawalResponses.get(0);
    }

    /*
    As STUB can randomly return success or fail, so we need to check both the status
     */
    private boolean checkWithdrawalStatusIsSuccessOrError(AccountId accountId, Long withdrawalAmount, String message) {
        final var withdrawalStatusResponse = when().get("/accounts/{id}/withdrawals", accountId.value()).thenReturn();
        try {
            assertThatJson(withdrawalStatusResponse.body().asString()).inPath("[0].status").isIn("SUCCESS", "ERROR");
            assertThatJson(withdrawalStatusResponse.body().asString()).inPath("[0].amount").isEqualTo(withdrawalAmount);
            assertThatJson(withdrawalStatusResponse.body().asString()).inPath("[0].message").isEqualTo(message);
        } catch (AssertionError ae) {
            return false;
        }

        return true;
    }

    private Response createAccount(AccountId id) {
        final var createAccountRequest = new CreateAccountRequest(id);
        return when().body(createAccountRequest).post("/accounts").thenReturn();
    }

    private Response createWithdrawal(AccountId accountId, String address, Long amount) {
        final var createWithdrawalRequest = new WithdrawalRequest(address, amount);
        return when().body(createWithdrawalRequest).post("/accounts/{id}/withdrawals", accountId.value()).thenReturn();
    }

    private FutureTask<Response> createWithdrawalAsync(AccountId accountId, String address, Long amount) {
        Callable<Response> callable = () -> {
            final var createWithdrawalRequest = new WithdrawalRequest(address, amount);
            return when().body(createWithdrawalRequest).post("/accounts/{id}/withdrawals", accountId.value()).thenReturn();
        };
        FutureTask<Response> futureTask = new FutureTask<>(callable);
        Thread.startVirtualThread(futureTask);
        return futureTask;
    }

    private void addFundsToAccount(AccountId accountId, Long amount) {
        final var addFundRequest = new AddFundRequest(amount);
        when().body(addFundRequest).put("/accounts/{id}/funds", accountId.value()).thenReturn();
    }


}
