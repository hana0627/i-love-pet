package hana.lovepet.paymentservice.infrastructure.kafka.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.paymentservice.api.payment.service.PaymentService
import hana.lovepet.paymentservice.infrastructure.kafka.Groups
import hana.lovepet.paymentservice.infrastructure.kafka.Topics
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentPrepareEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class PaymentEventListener(

    private val paymentService: PaymentService,
    private val om: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(PaymentEventListener::class.java)


//    @KafkaListener(topics = ["payment.prepare"], groupId = "payment-service")
    @KafkaListener(topics = [Topics.PAYMENT_PREPARE], groupId = Groups.PAYMENT)
    fun onPrepareRequest(record: ConsumerRecord<String, String>,
                         ack: Acknowledgment
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
            // 재시도/ DLQ는 Step4~5에서 설정 (ErrorHandler or Dead Letter Topic)
            // 일단 nack을 안 하고 운영 단순화를 위해 ack (학습 환경)
            ack.acknowledge()
        }

    }
}