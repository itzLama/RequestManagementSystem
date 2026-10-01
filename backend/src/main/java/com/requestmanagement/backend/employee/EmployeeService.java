package com.requestmanagement.backend.employee;

import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
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
