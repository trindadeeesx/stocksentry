package com.trindadeeesx.stocksentry.infraestructure.cache;

import com.trindadeeesx.stocksentry.web.dto.product.ProductResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

@Configuration
public class RedisConfig {

    /**
     * critical-products guarda List<ProductResponse>. O serializer genérico só
     * grava o tipo de classes não-final, e a lista de Stream.toList() é final:
     * o JSON saía sem tipo e a leitura do cache estourava (500 em /critical e
     * /out-of-stock). Com o tipo fixo no serializer, o JSON nem precisa dele.
     */
    static RedisSerializer<List<ProductResponse>> productListSerializer() {
        JsonMapper mapper = JsonMapper.builder().build();
        return new JacksonJsonRedisSerializer<>(mapper,
                mapper.getTypeFactory().constructCollectionType(List.class, ProductResponse.class));
    }

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
                        config.entryTtl(Duration.ofMinutes(2))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                        .fromSerializer(productListSerializer())))
                .build();
    }
}