package com.trindadeeesx.stocksentry.application.common;

import com.trindadeeesx.stocksentry.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

	MutableClock clock;
	RateLimiter limiter;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-11-19T12:00:00Z"));
		limiter = new RateLimiter(clock);
	}

	@Test
	void shouldAllowUpToLimit_thenRefuseWithMessage() {
		for (int i = 0; i < 3; i++) {
			limiter.check("k", 3, Duration.ofMinutes(1), "devagar");
		}

		assertThatThrownBy(() -> limiter.check("k", 3, Duration.ofMinutes(1), "devagar"))
				.isInstanceOf(TooManyRequestsException.class)
				.hasMessage("devagar");
	}

	@Test
	void shouldReleaseAfterWindow() {
		limiter.check("k", 1, Duration.ofMinutes(1), "devagar");
		clock.advance(Duration.ofSeconds(61));

		assertThatCode(() -> limiter.check("k", 1, Duration.ofMinutes(1), "devagar")).doesNotThrowAnyException();
	}

	@Test
	void shouldCountKeysSeparately() {
		limiter.check("a", 1, Duration.ofMinutes(1), "devagar");

		assertThatCode(() -> limiter.check("b", 1, Duration.ofMinutes(1), "devagar")).doesNotThrowAnyException();
	}
}
