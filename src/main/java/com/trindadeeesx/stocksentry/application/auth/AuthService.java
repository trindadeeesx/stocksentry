package com.trindadeeesx.stocksentry.application.auth;

import com.trindadeeesx.stocksentry.domain.tenant.Tenant;
import com.trindadeeesx.stocksentry.domain.user.User;
import com.trindadeeesx.stocksentry.domain.user.UserRole;
import com.trindadeeesx.stocksentry.infraestructure.persistence.TenantRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.UserRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.JwtService;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.AuthResponse;
import com.trindadeeesx.stocksentry.web.dto.LoginRequest;
import com.trindadeeesx.stocksentry.web.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final SecurityUtils securityUtils;

    /**
     * {
     *   "tenantName": "Minha Empresa",
     *   "tenantSlug": "minha-empresa",
     *   "name": "Trindade",
     *   "email": "trindade@email.com",
     *   "password": "12345678"
     * }
     */

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        /**
         * pegar o ADMIN que ta criando o usuario novo
         * pegar o tenant
         * atribuir ao mesmo
         */

        User u = userRepository.findById(securityUtils.getCurrentUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        System.out.println(u.getName());

//        Tenant tenant = tenantRepository.save(
//                Tenant.builder()
//                        .name(request.getTenantName())
//                        .slug(request.getTenantSlug())
//                        .active(true)
//                        .build()
//        );
//
//        User user = userRepository.save(
//                User.builder()
//                        .tenant(tenant)
//                        .name(request.getName())
//                        .email(request.getEmail())
//                        .passwordHash(passwordEncoder.encode(request.getPassword()))
//                        .role(UserRole.ADMIN)
//                        .active(true)
//                        .build()
//        );
//
//        return AuthResponse.builder()
//                .token(jwtService.generateToken(user))
//                .email(user.getEmail())
//                .role(user.getRole().name())
//                .tenantId(tenant.getId())
//                .build();
        return null;
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();

        return AuthResponse.builder()
                .token(jwtService.generateToken(user))
                .email(user.getEmail())
                .role(user.getRole().name())
                .tenantId(user.getTenant().getId())
                .build();
    }
}
