package com.trindadeeesx.stocksentry.infraestructure.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {
	
	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
		RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(Duration.ofMinutes(5))
			.serializeKeysWith(
				RedisSerializationContext.SerializationPair
					.fromSerializer(new StringRedisSerializer()))
			.serializeValuesWith(
				RedisSerializationContext.SerializationPair
					.fromSerializer(RedisSerializer.json()));
		
		return RedisCacheManager.builder(factory)
			.cacheDefaults(config)
			.withCacheConfiguration("products",
				config.entryTtl(Duration.ofMinutes(5)))
			.withCacheConfiguration("stock-summary",
				config.entryTtl(Duration.ofMinutes(2)))
			.withCacheConfiguration("critical-products",
				config.entryTtl(Duration.ofMinutes(2)))
			.build();
	}
}