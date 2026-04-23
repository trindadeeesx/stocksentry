package com.trindadeeesx.stocksentry.infraestructure.security;

import com.trindadeeesx.stocksentry.domain.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

	private static final String ISSUER = "stocksentry";

	@Value("${security.jwt.secret}")
	private String secret;

	@Value("${security.jwt.expiration}")
	private long expiration;

	private SecretKey getSigningKey() {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public String generateToken(User user) {
		return Jwts.builder()
			.issuer(ISSUER)
			.subject(user.getEmail())
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + expiration))
			.signWith(getSigningKey())
			.compact();
	}

	public String extractEmail(String token) {
		return getClaims(token).getSubject();
	}

	public boolean isTokenValid(String token) {
		try {
			getClaims(token);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private Claims getClaims(String token) {
		return Jwts.parser()
			.verifyWith(getSigningKey())
			.requireIssuer(ISSUER)
			.clockSkewSeconds(30)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}
}
