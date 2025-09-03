package hana.lovepet.productservice.infrastructure.kafka.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.productservice.api.product.service.ProductService
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.GetProductsEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class ProductEventListener(
    private val productService: ProductService,
    private val om: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(ProductEventListener::class.java)

    @KafkaListener(topics = ["product.information.request"], groupId = "product-service")
    fun onProductsInformation(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        val message = record.value()
        try {
            val readValue = om.readValue(message, GetProductsEvent::class.java)
            productService.getProductsInformation(readValue.orderId, readValue.items)
            ack.acknowledge()
        } catch (e: Exception) {
            log.error("getProductsInformation 처리 실패. payload={}, err{}", message, e.message)
            ack.acknowledge()
        }

    }

}
