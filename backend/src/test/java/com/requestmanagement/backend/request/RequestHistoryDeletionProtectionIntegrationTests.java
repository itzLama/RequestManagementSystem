package com.requestmanagement.backend.request;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class RequestHistoryDeletionProtectionIntegrationTests {
    @Autowired JdbcTemplate jdbc;

    @Test
    void commentPreventsRequestDeletionAndRemainsIntact() {
        long requestId = createRequest("Comment-protected request");
        jdbc.update("""
                INSERT INTO comments(request_id, user_id, comment_text, is_internal)
                VALUES (?, (SELECT user_id FROM users WHERE email = 'sara.saad@example.com'),
                        'Protected comment', false)
                """, requestId);

        assertRequestDeletionRejected(requestId);

        assertThat(count("comments", requestId)).isEqualTo(1L);
        assertThat(count("requests", requestId)).isEqualTo(1L);
    }

    @Test
    void statusHistoryPreventsRequestDeletionAndRemainsIntact() {
        long requestId = createRequest("Status-history-protected request");
        jdbc.update("""
                INSERT INTO status_history(request_id, old_status, new_status, changed_by, change_note)
                VALUES (?, 'NEW', 'IN_PROGRESS',
                        (SELECT user_id FROM users WHERE email = 'sara.saad@example.com'),
                        'Protected transition')
                """, requestId);

        assertRequestDeletionRejected(requestId);

        assertThat(count("status_history", requestId)).isEqualTo(1L);
        assertThat(count("requests", requestId)).isEqualTo(1L);
    }

    private long createRequest(String title) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title, description, type_id, priority, status, created_by)
                VALUES (?, 'Deletion protection test',
                        (SELECT type_id FROM request_types ORDER BY type_id LIMIT 1),
                        'MEDIUM', 'NEW',
                        (SELECT user_id FROM users WHERE email = 'nora.ahmed@example.com'))
                RETURNING request_id
                """, Long.class, title);
    }

    private void assertRequestDeletionRejected(long requestId) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            Savepoint savepoint = connection.setSavepoint();
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM requests WHERE request_id = ?")) {
                statement.setLong(1, requestId);
                assertThatThrownBy(statement::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .extracting(throwable -> ((SQLException) throwable).getSQLState())
                        .isEqualTo("23503");
            } finally {
                connection.rollback(savepoint);
            }
            return null;
        });
    }

    private long count(String table, long requestId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE request_id = ?",
                Long.class,
                requestId);
    }
}
