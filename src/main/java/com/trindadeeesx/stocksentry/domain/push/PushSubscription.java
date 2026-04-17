package com.trindadeeesx.stocksentry.domain.push;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	
	@Column(nullable = false, unique = true, columnDefinition = "TEXT")
	private String endpoint;
	
	@Column(nullable = false, columnDefinition = "TEXT")
	private String p256dh;
	
	@Column(name = "auth_key", nullable = false, columnDefinition = "TEXT")
	private String authKey;
	
	@Column(length = 100)
	private String deviceName;
	
	@CreationTimestamp
	private LocalDateTime createdAt;
}
