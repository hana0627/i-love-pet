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

    fun getNextOrderNumber(dateString: String): String {
        val key = "orderNo_$dateString"
        val nextSeq = redisTemplate.opsForValue().increment(key) ?: 1L

        // TTL 36시간 설정
        redisTemplate.expire(key, Duration.ofHours(36))

        // yyyyMMdd + 8자리 시퀀스 번호 (00000001 ~ 99999999)
        val orderNo = "$dateString%08d".format(nextSeq)

        log.info("Generated orderNo: {} for date: {}, sequence: {}", orderNo, dateString, nextSeq)
        return orderNo
    }
}
