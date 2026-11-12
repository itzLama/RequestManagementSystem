package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AttachmentReservationConcurrencyIntegrationTests {
    @Autowired AttachmentLifecycleService lifecycle;
    @Autowired AttachmentObjectKeyGenerator keyGenerator;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void competingReservationsCannotExceedTenSlots() throws Exception {
        Fixture fixture = transactions.execute(status -> createFixture());
        try {
            for (int index = 0; index < 9; index++) {
                lifecycle.reserveInternal(fixture.project(), fixture.task(), fixture.user(), "file.pdf",
                        "application/pdf", keyGenerator.generate().quarantine());
            }

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> reservation = () -> {
                ready.countDown();
                start.await();
                try {
                    lifecycle.reserveInternal(fixture.project(), fixture.task(), fixture.user(), "file.pdf",
                            "application/pdf", keyGenerator.generate().quarantine());
                    return true;
                } catch (AttachmentLifecycleException exception) {
                    assertThat(exception.getReason())
                            .isEqualTo(AttachmentLifecycleException.Reason.CAPACITY_EXCEEDED);
                    return false;
                }
            };

            try (var executor = Executors.newFixedThreadPool(2)) {
                List<Future<Boolean>> results = List.of(executor.submit(reservation), executor.submit(reservation));
                ready.await();
                start.countDown();
                assertThat(results).extracting(Future::get).containsExactlyInAnyOrder(true, false);
            }

            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM task_attachments WHERE request_id=?
                      AND storage_status IN ('PENDING','QUARANTINED','AVAILABLE','DELETION_PENDING')
                    """, Long.class, fixture.task())).isEqualTo(10);
        } finally {
            transactions.executeWithoutResult(status -> cleanup(fixture));
        }
    }

    private Fixture createFixture() {
        long user = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);
        long project = jdbc.queryForObject(
                "INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id",
                Long.class, "Attachment concurrency " + UUID.randomUUID());
        long workType = jdbc.queryForObject(
                "INSERT INTO project_work_types(project_id,work_type_name) VALUES (?,'Task') RETURNING work_type_id",
                Long.class, project);
        long status = jdbc.queryForObject(
                "SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key='NEW'",
                Long.class, project);
        long task = jdbc.queryForObject("""
                INSERT INTO requests(title,description,project_work_type_id,priority,status,project_status_id,
                                     created_by,project_id,assigned_to)
                VALUES ('Concurrent attachments','Details',?,'MEDIUM','NEW',?,?,?,?) RETURNING request_id
                """, Long.class, workType, status, user, project, user);
        return new Fixture(project, task, user);
    }

    private void cleanup(Fixture fixture) {
        jdbc.update("DELETE FROM task_attachment_visibility_events WHERE request_id=?", fixture.task());
        jdbc.update("DELETE FROM task_attachments WHERE request_id=?", fixture.task());
        jdbc.update("DELETE FROM requests WHERE request_id=?", fixture.task());
        jdbc.update("DELETE FROM project_status_columns WHERE project_id=?", fixture.project());
        jdbc.update("DELETE FROM project_work_types WHERE project_id=?", fixture.project());
        jdbc.update("DELETE FROM projects WHERE project_id=?", fixture.project());
    }

    private record Fixture(long project, long task, long user) { }
}
