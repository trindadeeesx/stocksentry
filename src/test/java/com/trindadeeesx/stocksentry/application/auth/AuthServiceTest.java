package com.trindadeeesx.stocksentry.application.auth;

import com.trindadeeesx.stocksentry.application.common.ConflictException;
import com.trindadeeesx.stocksentry.domain.tenant.Tenant;
import com.trindadeeesx.stocksentry.domain.user.User;
import com.trindadeeesx.stocksentry.domain.user.UserRole;
import com.trindadeeesx.stocksentry.infraestructure.persistence.TenantRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.UserRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.JwtService;
import com.trindadeeesx.stocksentry.web.dto.AuthResponse;
import com.trindadeeesx.stocksentry.web.dto.LoginRequest;
import com.trindadeeesx.stocksentry.web.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock TenantRepository tenantRepository;
	@Mock UserRepository userRepository;
	@Mock PasswordEncoder passwordEncoder;
	@Mock JwtService jwtService;
	@Mock AuthenticationManager authenticationManager;

	@InjectMocks AuthService authService;

	RegisterRequest request;

	@BeforeEach
	void setUp() {
		request = new RegisterRequest();
		request.setTenantName("Minha Empresa");
		request.setTenantSlug("minha-empresa");
		request.setName("Trindade");
		request.setEmail("trindade@email.com");
		request.setPassword("12345678");
	}

	@Test
	void shouldCreateTenantAndAdmin_andReturnToken() {
		UUID tenantId = UUID.randomUUID();
		when(tenantRepository.existsBySlug("minha-empresa")).thenReturn(false);
		when(userRepository.existsByEmailIgnoreCase("trindade@email.com")).thenReturn(false);
		when(passwordEncoder.encode("12345678")).thenReturn("hash");
		when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
			Tenant t = inv.getArgument(0);
			t.setId(tenantId);
			return t;
		});
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
		when(jwtService.generateToken(any(User.class))).thenReturn("token");

		AuthResponse response = authService.register(request);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		User saved = captor.getValue();
		assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(saved.getPasswordHash()).isEqualTo("hash");
		assertThat(saved.isActive()).isTrue();
		assertThat(saved.getTenant().getId()).isEqualTo(tenantId);
		assertThat(response.getToken()).isEqualTo("token");
		assertThat(response.getRole()).isEqualTo("ADMIN");
		assertThat(response.getEmail()).isEqualTo("trindade@email.com");
		assertThat(response.getTenantId()).isEqualTo(tenantId);
	}

	/**
	 * E-mail não diferencia maiúscula: "Trindade@Email.com" é a mesma conta de
	 * "trindade@email.com". Grava normalizado e checa duplicidade sem caixa.
	 */
	@Test
	void shouldNormalizeEmail_onRegister() {
		request.setEmail("  Trindade@Email.COM ");
		when(tenantRepository.existsBySlug("minha-empresa")).thenReturn(false);
		when(userRepository.existsByEmailIgnoreCase("trindade@email.com")).thenReturn(false);
		when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		authService.register(request);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getEmail()).isEqualTo("trindade@email.com");
	}

	@Test
	void shouldLogin_withEmailInAnyCase() {
		Tenant tenant = Tenant.builder().id(UUID.randomUUID()).build();
		User user = User.builder().tenant(tenant).email("trindade@email.com").role(UserRole.ADMIN).build();
		when(userRepository.findByEmailIgnoreCase("trindade@email.com")).thenReturn(Optional.of(user));
		when(jwtService.generateToken(user)).thenReturn("token");
		LoginRequest login = new LoginRequest();
		login.setEmail(" Trindade@Email.com");
		login.setPassword("12345678");

		AuthResponse response = authService.login(login);

		ArgumentCaptor<UsernamePasswordAuthenticationToken> auth =
				ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
		verify(authenticationManager).authenticate(auth.capture());
		assertThat(auth.getValue().getPrincipal()).isEqualTo("trindade@email.com");
		assertThat(response.getEmail()).isEqualTo("trindade@email.com");
		assertThat(response.getToken()).isEqualTo("token");
	}

	@Test
	void shouldRejectDuplicatedSlug_withoutSaving() {
		when(tenantRepository.existsBySlug("minha-empresa")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(request))
				.isInstanceOf(ConflictException.class)
				.hasMessage("Slug already in use");

		verify(tenantRepository, never()).save(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void shouldRejectDuplicatedEmail_withoutSaving() {
		when(tenantRepository.existsBySlug("minha-empresa")).thenReturn(false);
		when(userRepository.existsByEmailIgnoreCase("trindade@email.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(request))
				.isInstanceOf(ConflictException.class)
				.hasMessage("Email already in use");

		verify(tenantRepository, never()).save(any());
		verify(userRepository, never()).save(any());
	}
}
