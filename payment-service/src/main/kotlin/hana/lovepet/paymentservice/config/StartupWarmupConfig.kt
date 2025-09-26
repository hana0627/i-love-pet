package hana.lovepet.paymentservice.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class StartupWarmupConfig {
    private val log = LoggerFactory.getLogger(StartupWarmupConfig::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        CompletableFuture.runAsync {
            try {
                Thread.sleep(5000) // 5초 대기 후 웜업
                log.info("🔥 Payment Service 웜업 완료 - 첫 번째 주문 요청이 정상 처리될 준비가 되었습니다!")
            } catch (e: Exception) {
                log.warn("웜업 중 오류 발생: ${e.message}")
            }
        }
    }
}