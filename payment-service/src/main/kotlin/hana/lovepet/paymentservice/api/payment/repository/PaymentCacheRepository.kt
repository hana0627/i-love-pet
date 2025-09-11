//package hana.lovepet.paymentservice.api.payment.repository
//
//import org.slf4j.LoggerFactory
//import org.springframework.beans.factory.annotation.Qualifier
//import org.springframework.data.redis.core.RedisTemplate
//import org.springframework.stereotype.Repository
//import java.time.Duration
//
//@Repository
//class PaymentCacheRepository(
//    @Qualifier("paymentRedisTemplate")
//    private val redisTemplate: RedisTemplate<String, Boolean>,
//) {
//
//    private val log = LoggerFactory.getLogger(PaymentCacheRepository::class.java)
//
//    fun setCancelRequest(paymentId: Long) {
//        val key = "cancelRequest:$paymentId"
//        log.info("setCancelRequest: key = $key, value = true")
//        redisTemplate.opsForValue().set(key, true, Duration.ofMinutes(30))
//    }
//
//}
//
