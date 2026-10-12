package com.requestmanagement.backend.employee;

import com.requestmanagement.backend.comment.CommentRepository;
import com.requestmanagement.backend.guestcommunication.GuestCommunicationRepository;
import com.requestmanagement.backend.project.ProjectMembershipRepository;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeServiceTest {
    @Test
    void databaseIntegrityConflictIsReportedAsSafeConflict() {
        UserRepository users = mock(UserRepository.class);
        User employee = mock(User.class);
        when(employee.getId()).thenReturn(42L);
        when(users.findByIdAndRole(42L, User.Role.EMPLOYEE)).thenReturn(Optional.of(employee));
        doThrow(new DataIntegrityViolationException("Concurrent dependent record"))
                .when(users).flush();

        EmployeeService service = new EmployeeService(
                users,
                mock(PasswordEncoder.class),
                mock(RequestRepository.class),
                mock(CommentRepository.class),
                mock(StatusHistoryRepository.class),
                mock(GuestCommunicationRepository.class),
                mock(ProjectMembershipRepository.class));

        assertThatThrownBy(() -> service.delete(42L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason())
                            .isEqualTo("Employee has persisted activity and must be deactivated instead.");
                });
    }
}
