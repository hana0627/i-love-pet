package hana.lovepet.productservice.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig {

    @Bean("decreaseStockRedisTemplate")
    fun decreaseStockRedisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Boolean> {
        val template = RedisTemplate<String, Boolean>()
        template.connectionFactory = connectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = Jackson2JsonRedisSerializer(Boolean::class.java)
        return template
    }


    @Bean("rollbackStockRedisTemplate")
    fun rollbackStockRedisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Boolean> {
        val template = RedisTemplate<String, Boolean>()
        template.connectionFactory = connectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = Jackson2JsonRedisSerializer(Boolean::class.java)
        return template
    }
}