package hana.lovepet.paymentservice.infrastructure.kafka.out

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.paymentservice.infrastructure.kafka.Topics
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.PaymentPreparedEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val om: ObjectMapper,
) {
//    private val preparedTopic = "payment.prepared"

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishPaymentPrepared(event: PaymentPreparedEvent) {
        kafkaTemplate.send(Topics.PAYMENT_PREPARED, event.orderId.toString(), om.writeValueAsString(event))
    }

}