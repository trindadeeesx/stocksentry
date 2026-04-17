package com.trindadeeesx.stocksentry.web.handler;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
	
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
		return Map.of("error", ex.getMessage());
	}
	
	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new HashMap<>();
		ex.getBindingResult().getFieldErrors()
			.forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
		return Map.of("error", "Validation failed", "fields", fields);
	}
	
	@ExceptionHandler(BadCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public Map<String, String> handleBadCredentials(BadCredentialsException ex) {
		return Map.of("error", "Invalid email or password");
	}
	
	@ExceptionHandler(AccessDeniedException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public Map<String, String> handleAccessDenied(AccessDeniedException ex) {
		return Map.of("error", "Access denied");
	}
	
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public Map<String, String> handleGeneric(Exception ex) {
		ex.printStackTrace();
		return Map.of("error", "Internal server error");
	}
}