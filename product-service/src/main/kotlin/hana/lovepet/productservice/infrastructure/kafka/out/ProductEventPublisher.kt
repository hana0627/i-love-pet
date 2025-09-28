package hana.lovepet.productservice.infrastructure.kafka.out

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.productservice.infrastructure.kafka.Topics
import hana.lovepet.productservice.infrastructure.kafka.out.dto.ProductStockDecreasedEvent
import hana.lovepet.productservice.infrastructure.kafka.out.dto.ProductsInformationResponseEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class ProductEventPublisher (
    private val tracingKafkaPublisher: TracingKafkaPublisher,
    private val om: ObjectMapper,
){

//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishProductsInformation(event: ProductsInformationResponseEvent) {
        tracingKafkaPublisher.send(Topics.PRODUCT_INFORMATION_RESPONSE, event.orderId.toString(), om.writeValueAsString(event))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishProductStockDecreased(event: ProductStockDecreasedEvent) {
        tracingKafkaPublisher.send(Topics.PRODUCT_STOCK_DECREASED, event.orderId.toString(), om.writeValueAsString(event))
    }
}
