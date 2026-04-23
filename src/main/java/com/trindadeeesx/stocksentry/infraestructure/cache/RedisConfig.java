package com.trindadeeesx.stocksentry.infraestructure.cache;

import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

@Configuration
public class RedisConfig {
	
	@Bean
	public GenericJacksonJsonRedisSerializer redisSerializer() {
		ObjectMapper mapper = JsonMapper.builder().build();
		return new GenericJacksonJsonRedisSerializer(mapper);
	}
	
	@Bean
	public RedisCacheManager cacheManager(
		RedisConnectionFactory factory,
		GenericJacksonJsonRedisSerializer serializer
	) {
		RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(Duration.ofMinutes(5))
			.serializeKeysWith(
				RedisSerializationContext.SerializationPair
					.fromSerializer(new StringRedisSerializer()))
			.serializeValuesWith(
				RedisSerializationContext.SerializationPair
					.fromSerializer(serializer));
		
		return RedisCacheManager.builder(factory)
			.cacheDefaults(config)
			.build();
	}
	
	@Bean
	public RedisTemplate<String, Object> redisTemplate(
		RedisConnectionFactory connectionFactory,
		GenericJacksonJsonRedisSerializer serializer
	) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(serializer);
		
		template.setHashKeySerializer(new StringRedisSerializer());
		template.setHashValueSerializer(serializer);
		
		template.afterPropertiesSet();
		return template;
	}
}