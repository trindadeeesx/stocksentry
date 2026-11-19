package com.trindadeeesx.stocksentry.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Clock de teste que só anda quando mandam. */
public class MutableClock extends Clock {
	private Instant now;

	public MutableClock(Instant now) {
		this.now = now;
	}

	public void advance(Duration d) {
		now = now.plus(d);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return now;
	}
}
