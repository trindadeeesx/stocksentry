package com.trindadeeesx.stocksentry.domain.alert;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "alert_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertConfig {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AlertType type;
	
	@Column(nullable = false)
	private String destination;
	
	@Column(nullable = false)
	private boolean active = true;
}
