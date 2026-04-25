package com.trindadeeesx.stocksentry.infraestructure.persistence;

import com.trindadeeesx.stocksentry.domain.push.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {
	Optional<PushSubscription> findByEndpoint(String endpoint);

	List<PushSubscription> findAllByDeviceName(String deviceName);

	void deleteByEndpoint(String endpoint);
}
