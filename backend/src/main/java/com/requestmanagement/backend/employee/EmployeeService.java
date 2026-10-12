package com.requestmanagement.backend.employee;

import com.requestmanagement.backend.comment.CommentRepository;
import com.requestmanagement.backend.guestcommunication.GuestCommunicationRepository;
import com.requestmanagement.backend.project.ProjectMembershipRepository;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RequestRepository requestRepository;
    private final CommentRepository commentRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final GuestCommunicationRepository guestCommunicationRepository;
    private final ProjectMembershipRepository projectMembershipRepository;

    @Transactional(readOnly = true)
    public List<EmployeeResponse> list() {
        return userRepository.findByRoleOrderByFullNameAscIdAsc(User.Role.EMPLOYEE).stream()
                .map(EmployeeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse details(Long id) {
        return EmployeeResponse.from(requireEmployee(id));
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest input) {
        String fullName = normalizeName(input.fullName());
        String email = normalizeEmail(input.email());
        validateUniqueEmail(email, null);
        validatePassword(input.initialPassword());
        return EmployeeResponse.from(userRepository.save(User.createEmployee(
                fullName, email, passwordEncoder.encode(input.initialPassword()))));
    }

    @Transactional
    public EmployeeResponse update(Long id, UpdateEmployeeRequest input) {
        User employee = requireEmployee(id);
        String fullName = normalizeName(input.fullName());
        String email = normalizeEmail(input.email());
        validateUniqueEmail(email, id);
        employee.updateEmployeeProfile(fullName, email);
        return EmployeeResponse.from(userRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse deactivate(Long id) {
        User employee = requireEmployee(id);
        employee.deactivate();
        return EmployeeResponse.from(userRepository.save(employee));
    }

    @Transactional
    public void delete(Long id) {
        User employee = requireEmployee(id);
        if (hasPersistedDependencies(employee.getId())) {
            throw deletionConflict();
        }

        try {
            userRepository.delete(employee);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Employee has persisted activity and must be deactivated instead.",
                    exception);
        }
    }

    private boolean hasPersistedDependencies(Long employeeId) {
        return requestRepository.existsByCreatedBy_Id(employeeId)
                || requestRepository.existsByAssignedTo_Id(employeeId)
                || commentRepository.existsByUser_Id(employeeId)
                || statusHistoryRepository.existsByChangedBy_Id(employeeId)
                || guestCommunicationRepository.existsBySentBy_Id(employeeId)
                || projectMembershipRepository.existsByEmployee_Id(employeeId);
    }

    private ResponseStatusException deletionConflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Employee has persisted activity and must be deactivated instead.");
    }

    private User requireEmployee(Long id) {
        return userRepository.findByIdAndRole(id, User.Role.EMPLOYEE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found."));
    }

    private String normalizeName(String value) {
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required.");
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be valid.");
        }
        return normalized;
    }

    private void validateUniqueEmail(String email, Long currentId) {
        boolean exists = currentId == null ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, currentId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use.");
        }
    }

    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Initial password must be at most 72 bytes.");
        }
    }
}
