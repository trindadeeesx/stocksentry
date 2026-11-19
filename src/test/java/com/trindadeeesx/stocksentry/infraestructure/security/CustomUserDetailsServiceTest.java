package com.trindadeeesx.stocksentry.infraestructure.security;

import com.trindadeeesx.stocksentry.domain.user.User;
import com.trindadeeesx.stocksentry.infraestructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

	@Mock UserRepository userRepository;

	@InjectMocks CustomUserDetailsService service;

	/**
	 * Contas antigas podem ter e-mail gravado com maiúscula. A busca ignora a
	 * caixa para que elas continuem entrando depois da normalização.
	 */
	@Test
	void shouldFindUser_ignoringCase() {
		User user = User.builder().email("Maria@Email.com").build();
		when(userRepository.findByEmailIgnoreCase("maria@email.com")).thenReturn(Optional.of(user));

		assertThat(service.loadUserByUsername(" MARIA@email.com ")).isSameAs(user);
	}
}
