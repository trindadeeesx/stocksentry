package com.trindadeeesx.stocksentry.application.auth;

import com.trindadeeesx.stocksentry.domain.user.User;
import com.trindadeeesx.stocksentry.domain.user.UserRole;
import com.trindadeeesx.stocksentry.infraestructure.persistence.UserRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.JwtService;
import com.trindadeeesx.stocksentry.web.dto.AuthResponse;
import com.trindadeeesx.stocksentry.web.dto.LoginRequest;
import com.trindadeeesx.stocksentry.web.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;

	@Value("${security.register-key:}")
	private String registerKey;

	public AuthResponse register(String providedKey, RegisterRequest request) {
		if (registerKey.isBlank() || !registerKey.equals(providedKey)) {
			throw new BadCredentialsException("Invalid register key");
		}
		if (userRepository.existsByEmail(request.getEmail())) {
			throw new IllegalArgumentException("Email already in use");
		}
		
		User user = userRepository.save(
			User.builder()
				.name(request.getName())
				.email(request.getEmail())
				.passwordHash(passwordEncoder.encode(request.getPassword()))
				.role(UserRole.ADMIN)
				.active(true)
				.build()
		);
		
		return AuthResponse.builder()
			.token(jwtService.generateToken(user))
			.email(user.getEmail())
			.role(user.getRole().name())
			.build();
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
			.build();
	}
}
