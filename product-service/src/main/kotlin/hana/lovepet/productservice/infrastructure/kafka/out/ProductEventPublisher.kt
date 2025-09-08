package hana.lovepet.productservice.infrastructure.kafka.out

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.productservice.infrastructure.kafka.Topics
import hana.lovepet.productservice.infrastructure.kafka.out.dto.ProductsInformationResponseEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class ProductEventPublisher (
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val om: ObjectMapper,
){

//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishProductsInformation(event: ProductsInformationResponseEvent) {
        kafkaTemplate.send(Topics.PRODUCT_INFORMATION_RESPONSE, event.orderId.toString(), om.writeValueAsString(event))
    }
}
