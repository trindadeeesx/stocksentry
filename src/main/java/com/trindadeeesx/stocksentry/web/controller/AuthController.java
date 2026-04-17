package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.auth.AuthService;
import com.trindadeeesx.stocksentry.domain.user.User;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.AuthResponse;
import com.trindadeeesx.stocksentry.web.dto.LoginRequest;
import com.trindadeeesx.stocksentry.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
	
	private final AuthService authService;
	private final SecurityUtils securityUtils;
	
	@PostMapping("/login")
	public AuthResponse login(@RequestBody @Valid LoginRequest request) {
		return authService.login(request);
	}
	
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse register(@RequestBody @Valid RegisterRequest request) {
		return authService.register(request);
	}
	
	@GetMapping("/me")
	public ResponseEntity<Map<String, Object>> me() {
		User user = securityUtils.getCurrentUser();
		return ResponseEntity.ok(Map.of(
			"id", user.getId(),
			"name", user.getName(),
			"email", user.getEmail(),
			"role", user.getRole()
		));
	}
}
