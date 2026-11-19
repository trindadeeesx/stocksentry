package com.trindadeeesx.stocksentry.application.user;

import com.trindadeeesx.stocksentry.application.common.ConflictException;
import com.trindadeeesx.stocksentry.domain.user.User;
import com.trindadeeesx.stocksentry.domain.user.UserRole;
import com.trindadeeesx.stocksentry.infraestructure.persistence.UserRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.user.CreateUserRequest;
import com.trindadeeesx.stocksentry.web.dto.user.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;

    @Transactional
    public UserResponse createOperator(CreateUserRequest request) {
        User admin = requireAdmin();

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already in use");
        }

        User user = userRepository.save(
                User.builder()
                        .tenant(admin.getTenant())
                        .name(request.getName())
                        .email(request.getEmail())
                        .passwordHash(passwordEncoder.encode(request.getPassword()))
                        .role(UserRole.OPERATOR)
                        .active(true)
                        .build()
        );

        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        User admin = requireAdmin();
        return userRepository.findAllByTenantId(admin.getTenant().getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private User requireAdmin() {
        User current = securityUtils.getCurrentUser();
        if (current.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only admins can manage users");
        }
        return current;
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
