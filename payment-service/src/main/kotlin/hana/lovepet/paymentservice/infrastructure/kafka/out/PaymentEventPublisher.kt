package hana.lovepet.paymentservice.infrastructure.kafka.out

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.paymentservice.infrastructure.kafka.Topics
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.PaymentPrepareFailEvent
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.PaymentPreparedEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event

@Service
class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val om: ObjectMapper,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishPaymentPrepared(event: PaymentPreparedEvent) {
        kafkaTemplate.send(Topics.PAYMENT_PREPARED, event.orderId.toString(), om.writeValueAsString(event))
    }

    fun paymentPrepareFailed(event: PaymentPrepareFailEvent) {
        kafkaTemplate.send(Topics.PAYMENT_PREPARE_FAIL, event.orderId.toString(), om.writeValueAsString(event))
    }
}
