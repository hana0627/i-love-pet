package hana.lovepet.paymentservice.infrastructure.kafka.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.paymentservice.api.payment.service.PaymentService
import hana.lovepet.paymentservice.common.exception.ApplicationException
import hana.lovepet.paymentservice.infrastructure.kafka.Groups
import hana.lovepet.paymentservice.infrastructure.kafka.Topics
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentPrepareEvent
import hana.lovepet.paymentservice.infrastructure.kafka.out.PaymentEventPublisher
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.PaymentPrepareFailEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.DltStrategy
import org.springframework.kafka.support.Acknowledgment
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PaymentEventListener(
    private val paymentService: PaymentService,
    private val om: ObjectMapper,
    private val paymentEventPublisher: PaymentEventPublisher,
) {

    private val log = LoggerFactory.getLogger(PaymentEventListener::class.java)

    @RetryableTopic(
        attempts = "3", // 최대 3회 실행
        backoff = Backoff(delay = 1000), //1 초 간격으로 재시도
//        include = [Exception::class],
        exclude = [ApplicationException::class],
        dltStrategy = DltStrategy.FAIL_ON_ERROR, // 모든 재시도 실패시 DLT로 전송
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(topics = [Topics.PAYMENT_PREPARE], groupId = Groups.PAYMENT)
    fun onPrepareRequest(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, PaymentPrepareEvent::class.java)
            val result = paymentService.preparePayment(
                userId = readValue.userId,
                orderId = readValue.orderId,
                amount = readValue.amount,
                method = readValue.method ?: "카드",
            )
            log.info("Payment prepared. orderId={}, paymentId={}", readValue.orderId, result.paymentId)
            ack.acknowledge()

        } catch (e: Exception) {
            log.error("prepare.requested 처리 실패. payload={}, err={}", messages, e.message, e)
            throw e
        }
    }

    @DltHandler
    fun handleDeadLetterTopic(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        log.error("DLT로 전송된 상품 정보 요청 처리: {}", record.value())

        try {
            when (record.topic()) {
                Topics.PAYMENT_PREPARE+"-dlt" -> {
                    val failedEvent = om.readValue(record.value(), PaymentPrepareEvent::class.java)
                    // 비지니스 내부에서 어떤 뭔가를 처리할 필요는 없음. (예외가 발생했다면 rollback을 예상하기 떄문)
                    paymentEventPublisher.paymentPrepareFailed(
                        PaymentPrepareFailEvent(
                            eventId = UUID.randomUUID().toString(),
                            orderId = failedEvent.orderId,
                            idempotencyKey = failedEvent.orderId.toString(),
                        )
                    )
                    log.error("처리 실패: orderId={}", failedEvent.orderId)
                }

                else -> {
                    log.error("알 수 없는 DLT 토픽: {}", record.topic())
                }
            }
        } catch (e: Exception) {
            log.error("DLT 처리 중 오류 발생: {}", record.value(), e)
        } finally {
            ack.acknowledge()
        }
    }
}
