package hana.lovepet.orderservice.api.repository

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class OrderCacheRepository(
    @Qualifier("paymentKeyRedisTemplate")
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val log = LoggerFactory.getLogger(OrderCacheRepository::class.java)

    fun setPaymentKey(orderId: Long, paymentKey: String) {
        val key ="orderId:$orderId"
        log.info("setPaymentKey: {}, {}", key, paymentKey)
        redisTemplate.opsForValue().set(key, paymentKey, Duration.ofMinutes(30))
    }

    fun findPaymentKeyByOrderId(orderId: Long): String? {
        val key ="orderId:$orderId"
        log.info("findPaymentKeyByOrderId: {}", key)
        return redisTemplate.opsForValue().get(key)
    }
}
