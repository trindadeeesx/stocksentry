package com.trindadeeesx.stocksentry.application.user;

import com.trindadeeesx.stocksentry.application.common.ConflictException;
import com.trindadeeesx.stocksentry.domain.tenant.Tenant;
import com.trindadeeesx.stocksentry.domain.user.User;
import com.trindadeeesx.stocksentry.domain.user.UserRole;
import com.trindadeeesx.stocksentry.infraestructure.persistence.UserRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.user.CreateUserRequest;
import com.trindadeeesx.stocksentry.web.dto.user.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock UserRepository userRepository;
	@Mock PasswordEncoder passwordEncoder;
	@Mock SecurityUtils securityUtils;

	@InjectMocks UserService userService;

	Tenant tenant;
	User admin;
	User operator;
	CreateUserRequest request;

	@BeforeEach
	void setUp() {
		tenant = Tenant.builder().id(UUID.randomUUID()).name("Empresa").slug("empresa").build();
		admin = User.builder().id(UUID.randomUUID()).tenant(tenant).role(UserRole.ADMIN).build();
		operator = User.builder().id(UUID.randomUUID()).tenant(tenant).role(UserRole.OPERATOR).build();
		request = new CreateUserRequest();
		request.setName("Maria");
		request.setEmail("maria@email.com");
		request.setPassword("12345678");
	}

	@Test
	void shouldCreateOperatorInAdminsTenant() {
		when(securityUtils.getCurrentUser()).thenReturn(admin);
		when(userRepository.existsByEmail("maria@email.com")).thenReturn(false);
		when(passwordEncoder.encode("12345678")).thenReturn("hash");
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		UserResponse response = userService.createOperator(request);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		User saved = captor.getValue();
		assertThat(saved.getRole()).isEqualTo(UserRole.OPERATOR);
		assertThat(saved.getTenant()).isSameAs(tenant);
		assertThat(saved.getPasswordHash()).isEqualTo("hash");
		assertThat(saved.isActive()).isTrue();
		assertThat(response.getEmail()).isEqualTo("maria@email.com");
		assertThat(response.getRole()).isEqualTo(UserRole.OPERATOR);
	}

	@Test
	void shouldDenyOperator() {
		when(securityUtils.getCurrentUser()).thenReturn(operator);

		assertThatThrownBy(() -> userService.createOperator(request))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> userService.findAll())
				.isInstanceOf(AccessDeniedException.class);

		verify(userRepository, never()).save(any());
	}

	@Test
	void shouldRejectDuplicatedEmail() {
		when(securityUtils.getCurrentUser()).thenReturn(admin);
		when(userRepository.existsByEmail("maria@email.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.createOperator(request))
				.isInstanceOf(ConflictException.class);

		verify(userRepository, never()).save(any());
	}

	@Test
	void shouldListOnlyCurrentTenantUsers() {
		when(securityUtils.getCurrentUser()).thenReturn(admin);
		when(userRepository.findAllByTenantId(tenant.getId())).thenReturn(List.of(admin, operator));

		List<UserResponse> result = userService.findAll();

		assertThat(result).hasSize(2);
		verify(userRepository).findAllByTenantId(tenant.getId());
	}
}
