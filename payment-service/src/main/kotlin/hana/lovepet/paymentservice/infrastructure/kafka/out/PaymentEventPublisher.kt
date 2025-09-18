package hana.lovepet.paymentservice.infrastructure.kafka.out

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.paymentservice.infrastructure.kafka.Topics
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.*
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val om: ObjectMapper,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishPaymentPrepared(event: PaymentPreparedEvent) {
        kafkaTemplate.send(Topics.PAYMENT_PREPARED, event.orderId.toString(), om.writeValueAsString(event))
    }

    fun publishPaymentPrepareFailed(event: PaymentPrepareFailEvent) {
        kafkaTemplate.send(Topics.PAYMENT_PREPARE_FAIL, event.orderId.toString(), om.writeValueAsString(event))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishPaymentConfirm(event: PaymentConfirmedEvent) {
        kafkaTemplate.send(Topics.PAYMENT_CONFIRMED, event.orderId.toString(), om.writeValueAsString(event))
    }

    fun publishPaymentConfirmedFail(event: PaymentConfirmedFailEvent) {
        kafkaTemplate.send(Topics.PAYMENT_CONFIRMED_FAIL, event.orderId.toString(), om.writeValueAsString(event))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishPaymentCanceled(event: PaymentCanceledEvent) {
        kafkaTemplate.send(Topics.PAYMENT_CANCELED, event.orderId.toString(), om.writeValueAsString(event))
    }

    fun publishPaymentCanceledFail(event: PaymentCanceledFailEvent) {
        kafkaTemplate.send(Topics.PAYMENT_CANCELED_FAIL, event.orderId.toString(), om.writeValueAsString(event))
    }
}
