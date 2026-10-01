package com.example.zettaassignment.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class IdempotencyLockRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private IdempotencyLockRepository idempotencyLockRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void lockBlocksSameClientAndIdempotencyKeyUntilOwningTransactionCompletes() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicBoolean secondAcquired = new AtomicBoolean(false);

        try {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                idempotencyLockRepository.lock("CLIENT-001", "same-key");
                firstHasLock.countDown();
                await(releaseFirst);
            }));

            assertThat(firstHasLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                secondStarted.countDown();
                idempotencyLockRepository.lock("CLIENT-001", "same-key");
                secondAcquired.set(true);
            }));

            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(secondAcquired).isFalse();

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(secondAcquired).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void lockAllowsDifferentIdempotencyKeysToProceedIndependently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondAcquired = new AtomicBoolean(false);

        try {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                idempotencyLockRepository.lock("CLIENT-001", "key-one");
                firstHasLock.countDown();
                await(releaseFirst);
            }));

            assertThat(firstHasLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                idempotencyLockRepository.lock("CLIENT-001", "key-two");
                secondAcquired.set(true);
            }));

            second.get(5, TimeUnit.SECONDS);
            assertThat(secondAcquired).isTrue();

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
