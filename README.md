# Solution Design:

All the resources/endpoints are divided into two different categories,

### Synchronous

All the end points which do not change state of the system.(Non-Mutable operations)

- Get Account
- Get Account Withdrawals
- Healthcheck

### Asynchronous

All the end points which do change state of the system.(Mutable operations)

- Add Funds to account
- Create Withdrawal

All Async operation to change account are run in background multiple virtual threads and not in http threads.
As the nature of application is concurrent, so each thread is assigned its own dedicated queue and data on queues are
partitioned by account id.
Which means operations of one account will always be posted on one queue and single thread will execute them in serial
fashion. [InMemoryAccountOperationManager.java](app/src/main/java/com/neverless/processing/InMemoryAccountOperationManager.java)

The operation on account itself will be defined by endpoint
implementation [Accounts.java](app/src/main/java/com/neverless/resources/Accounts.java) and AccountOperationManager will
only help in enqueuing and making sure operations get executed in serial fashion for one account while concurrently for
different accounts.

[ExternalWithdrawalManager.java](app/src/main/java/com/neverless/processing/ExternalWithdrawalManager.java) encapsulate
all
interaction with external withdrawal service and also run a single thread to check status of withdrawals

On Application shutdown all threads will be shutdown first and any pending task count will be printed in the logs

# Summary:

Design and implement a Service with an API (including data model and the backing implementation) with following
functional requirements:

- Caller can send money from their account to an external withdrawal address through an API (See Withdrawal Service
  Stub)
- Caller can see operation progress

## Requirements:

- Follow template, modify to an extent needed to complete the task
- Assume the API is invoked by multiple systems and services in the same time on behalf of end users.
- Datastore runs in memory for the sake of simplicity
- Runs standalone and doesn't require external pre-installed dependencies like docker

## Goals:

- To demonstrate high quality code and solution design (As if you’re writing code for your current company)
- To demonstrate ability to produce solution without detailed requirements
- To demonstrate the API and functional requirements are working correctly
- You can use any framework or library, but you must keep solution simple and straight to the point (hint: we’re not
  using Spring)

## Non-goals:

- Not to show ability to use frameworks - The goal of the task is to show fundamentals, not framework knowledge
- No need to implement non-functional pieces, like authentication, monitoring or logging

## Given

- Application skeleton
    - App setup with rest endpoints
    - Test setup
    - Account skeleton
    - Withdrawal service stubs
- Built with Gradle (Kotlin) and Java 21
- Contains minimal rest setup with Javalin and Jackson
- Provides utility libraries: junit 5, assertj, json-unit-assertj, awaitility, rest-assured, mockito (see
  libs.versions.toml)

## How to send us the solution

- Please upload solution to the Github, Bitbucket or Gitlab. 