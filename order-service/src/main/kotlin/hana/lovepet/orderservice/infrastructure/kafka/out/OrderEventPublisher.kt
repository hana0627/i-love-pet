package hana.lovepet.orderservice.infrastructure.kafka.out

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.orderservice.infrastructure.kafka.Topics
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.GetProductsEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.OrderCreateEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentPrepareEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class OrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val om: ObjectMapper,
) {

    private val orderCreatedTopic = "order.create"
//    private val paymentPrepareTopic = "payment.prepare"


    fun publishOrderCreated(event: OrderCreateEvent) {
        val json = om.writeValueAsString(event)
        kafkaTemplate.send(orderCreatedTopic, event.orderNo, json)
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishPaymentPrepareRequested(event: PaymentPrepareEvent) {
        kafkaTemplate.send(Topics.PAYMENT_PREPARE, event.orderId.toString(), om.writeValueAsString(event))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishGetProductsInformation(event: GetProductsEvent) {
        kafkaTemplate.send("product.information.request", event.orderId.toString(), om.writeValueAsString(event))
    }
}