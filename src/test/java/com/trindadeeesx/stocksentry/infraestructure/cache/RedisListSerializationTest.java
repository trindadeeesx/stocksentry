package com.trindadeeesx.stocksentry.infraestructure.cache;

import com.trindadeeesx.stocksentry.domain.product.UnitType;
import com.trindadeeesx.stocksentry.web.dto.product.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisListSerializationTest {

	/**
	 * findCritical/findOutOfStock devolvem Stream.toList(), uma lista final.
	 * O serializer genérico gravava essa lista sem tipo e não conseguia lê-la de
	 * volta: todo acesso ao cache dava 500. O cache precisa ida e volta sem perda.
	 */
	@Test
	void shouldRoundTripProductList_fromStreamToList() {
		RedisCacheManager manager = new RedisConfig().cacheManager(mock(RedisConnectionFactory.class));
		manager.afterPropertiesSet();
		RedisCacheConfiguration config = ((RedisCache) manager.getCache("critical-products")).getCacheConfiguration();
		ProductResponse product = ProductResponse.builder()
				.id(UUID.randomUUID()).name("Produto").sku("SKU-1").unit(UnitType.UN)
				.currentStock(new BigDecimal("2.000")).minStock(new BigDecimal("5"))
				.active(true).critical(true).createdAt(LocalDateTime.of(2026, 11, 19, 10, 0))
				.build();

		for (List<ProductResponse> list : List.of(Stream.of(product).toList(), Stream.<ProductResponse>empty().toList())) {
			ByteBuffer written = config.getValueSerializationPair().write(list);
			assertThat(config.getValueSerializationPair().read(written)).isEqualTo(list);
		}
	}
}
