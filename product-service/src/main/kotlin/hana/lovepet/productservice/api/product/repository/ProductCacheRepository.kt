package hana.lovepet.productservice.api.product.repository

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class ProductCacheRepository(
    @Qualifier("decreaseStockRedisTemplate")
    private val decreaseStockRedisTemplate: RedisTemplate<String, Boolean>,
    @Qualifier("rollbackStockRedisTemplate")
    private val rollbackStockRedisTemplate: RedisTemplate<String, Boolean>,
) {
    private val log = LoggerFactory.getLogger(ProductCacheRepository::class.java)

    fun setDecreased(orderId: Long) {
        val key ="decreased_orderId:$orderId"
        log.info("setDecreased: {}, {}", key, true)
        decreaseStockRedisTemplate.opsForValue().set(key, true, Duration.ofMinutes(30))
    }

    fun getDecreased(orderId: Long): Boolean {
        val key ="decreased_orderId:$orderId"
        log.info("getDecreased: {}", key)
        return decreaseStockRedisTemplate.opsForValue().get(key) ?: false
    }

    fun setRollbacked(orderId: Long) {
        val key ="rollbacked_orderId:$orderId"
        log.info("setRollbacked: {}, {}", key, true)
        rollbackStockRedisTemplate.opsForValue().set(key, true, Duration.ofMinutes(30))
    }

    fun getRollbacked(orderId: Long): Boolean {
        val key ="rollbacked_orderId:$orderId"
        log.info("getRollbacked: {}", key)
        return rollbackStockRedisTemplate.opsForValue().get(key) ?: false
    }

}
