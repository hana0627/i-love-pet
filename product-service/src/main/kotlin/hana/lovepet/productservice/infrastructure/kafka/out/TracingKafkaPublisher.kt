package hana.lovepet.productservice.infrastructure.kafka.out

import org.slf4j.MDC
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Kafka 메시지 발행 시 분산 트레이싱을 위한 Wrapper 클래스
 *
 * 기능:
 * 1. 기존 KafkaTemplate을 래핑하여 모든 메시지에 자동으로 traceId 헤더 추가
 * 2. MDC에서 현재 스레드의 traceId를 가져와서 Kafka 헤더에 포함
 * 3. traceId가 없으면 새로운 UUID를 생성하여 사용
 *
 * 사용법:
 * - 기존: kafkaTemplate.send(topic, key, payload)
 * - 변경: tracingKafkaPublisher.send(topic, key, payload)
 *
 * Spring Bean으로 자동 등록되므로 다른 클래스에서 주입받아 사용 가능
 */
@Service
class TracingKafkaPublisher(private val kafkaTemplate: KafkaTemplate<String, String>) {

    /**
     * 분산 트레이싱을 위한 Kafka 메시지 발행
     * @param topic Kafka 토픽명
     * @param key 메시지 키 (파티셔닝용)
     * @param payloadJson JSON 형태의 메시지 본문
     */
    fun send(topic: String, key: String?, payloadJson: String) {
        // 현재 스레드의 MDC에서 traceId 가져오기 (없으면 새로 생성)
        val traceId = MDC.get("traceId") ?: UUID.randomUUID().toString()

        // Kafka 메시지에 traceId 헤더 추가
        val msg = MessageBuilder.withPayload(payloadJson)
            .setHeader(KafkaHeaders.TOPIC, topic)
            .setHeader(KafkaHeaders.KEY, key)
            .setHeader("traceId", traceId) // 분산 트레이싱을 위한 헤더
            .build()

        kafkaTemplate.send(msg)
    }
}

