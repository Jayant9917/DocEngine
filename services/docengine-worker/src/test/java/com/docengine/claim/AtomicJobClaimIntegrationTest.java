package com.docengine.claim;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtomicJobClaimIntegrationTest {

    private static final String JDBC_URL =
            System.getProperty("docengine.test.jdbc-url", "jdbc:postgresql://localhost:5432/docengine");
    private static final String DB_USER =
            System.getProperty("docengine.test.db-user", "docengine");
    private static final String DB_PASSWORD =
            System.getProperty("docengine.test.db-password", "docengine");

    private static UUID jobId;

    @BeforeAll
    static void createSchemaAndJob() throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS job_claim_test");
            statement.execute("""
                    CREATE TABLE job_claim_test (
                        id UUID PRIMARY KEY,
                        status VARCHAR(30) NOT NULL,
                        claimed_by VARCHAR(200),
                        attempt_count INTEGER NOT NULL DEFAULT 0
                    )
                    """);

            jobId = UUID.randomUUID();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO job_claim_test (id, status) VALUES (?, 'QUEUED')")) {
                insert.setObject(1, jobId);
                insert.executeUpdate();
            }
        }
    }

    @Test
    void onlyOneWorkerCanClaimTheSameQueuedJob() throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        int firstResult;
        int secondResult;
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> firstWorker = workers.submit(
                    () -> claimJob("worker-1", startSignal));
            Future<Integer> secondWorker = workers.submit(
                    () -> claimJob("worker-2", startSignal));

            startSignal.countDown();

            firstResult = firstWorker.get();
            secondResult = secondWorker.get();
        }

        assertEquals(1, firstResult + secondResult,
                "Exactly one worker must claim the job");

        try (Connection connection = connection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT status, claimed_by, attempt_count FROM job_claim_test WHERE id = ?")) {
            query.setObject(1, jobId);
            try (ResultSet result = query.executeQuery()) {
                result.next();
                assertEquals("PROCESSING", result.getString("status"));
                assertEquals(1, result.getInt("attempt_count"));
            }
        }
    }

    private static int claimJob(String workerId, CountDownLatch startSignal)
            throws SQLException, InterruptedException {
        startSignal.await();

        try (Connection connection = connection();
             PreparedStatement claim = connection.prepareStatement("""
                     UPDATE job_claim_test
                     SET status = 'PROCESSING',
                         claimed_by = ?,
                         attempt_count = attempt_count + 1
                     WHERE id = ? AND status = 'QUEUED'
                     """)) {
            claim.setString(1, workerId);
            claim.setObject(2, jobId);
            return claim.executeUpdate();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD);
    }
}
