package com.trindadeeesx.stocksentry.domain.settings;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSettings {

	@Id
	private Short id = 1;

	@Column(nullable = false)
	private long syncIntervalMs = 300_000L;
}
