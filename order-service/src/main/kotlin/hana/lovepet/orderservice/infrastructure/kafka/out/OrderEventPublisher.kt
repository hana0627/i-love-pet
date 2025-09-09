package hana.lovepet.orderservice.infrastructure.kafka.out

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.orderservice.infrastructure.kafka.Topics
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.GetProductsEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentCancelEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentPendingEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentPrepareEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.ProductStockDecreaseEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.ProductStockRollbackEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class OrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val om: ObjectMapper,
) {

//    private val paymentPrepareTopic = "payment.prepare"


//    fun publishOrderCreated(event: OrderCreateEvent) {
//        val json = om.writeValueAsString(event)
//        kafkaTemplate.send(orderCreatedTopic, event.orderNo, json)
//    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishPaymentPrepareRequested(event: PaymentPrepareEvent) {
        kafkaTemplate.send(Topics.PAYMENT_PREPARE, event.orderId.toString(), om.writeValueAsString(event))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishGetProductsInformation(event: GetProductsEvent) {
        kafkaTemplate.send(Topics.PRODUCT_INFORMATION_REQUEST, event.orderId.toString(), om.writeValueAsString(event))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishDecreaseStock(event: ProductStockDecreaseEvent) {
        kafkaTemplate.send(Topics.PRODUCT_STOCK_DECREASE, event.orderId.toString(), om.writeValueAsString(event))
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishPaymentPending(event: PaymentPendingEvent) {
        kafkaTemplate.send(Topics.PAYMENT_PENDING, event.orderId.toString(), om.writeValueAsString(event))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishPaymentCancel(event: PaymentCancelEvent) {
        kafkaTemplate.send(Topics.PAYMENT_CANCEL, event.orderId.toString(), om.writeValueAsString(event))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishRollbackStock(event: ProductStockRollbackEvent) {
        kafkaTemplate.send(Topics.PRODUCT_STOCK_ROLLBACK, event.orderId.toString(), om.writeValueAsString(event))
    }
}
