package hana.lovepet.productservice.infrastructure.kafka.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.productservice.api.product.service.ProductService
import hana.lovepet.productservice.infrastructure.kafka.Groups
import hana.lovepet.productservice.infrastructure.kafka.Topics
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.GetProductsEvent
import hana.lovepet.productservice.infrastructure.kafka.out.ProductEventPublisher
import hana.lovepet.productservice.infrastructure.kafka.out.dto.ProductsInformationResponseEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.DltStrategy
import org.springframework.kafka.support.Acknowledgment
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Service
import java.util.*

@Service
class ProductEventListener(
    private val productService: ProductService,
    private val productEventPublisher: ProductEventPublisher,

    private val om: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(ProductEventListener::class.java)
    @RetryableTopic(
        attempts = "3", // 최대 3회 실행
        backoff = Backoff(delay = 1000), //1 초 간격으로 재시도
        include = [Exception::class],
        dltStrategy = DltStrategy.FAIL_ON_ERROR, // 모든 재시도 실패시 DLT로 전송
        dltTopicSuffix = "-dlt",
        )
    @KafkaListener(topics = [Topics.PRODUCT_INFORMATION_REQUEST], groupId = Groups.PRODUCT)
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
            throw e
//            ack.acknowledge()
        }

    }
    @DltHandler
    fun handleProductInfoDlt(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        log.error("DLT로 전송된 상품 정보 요청 처리: {}", record.value())
        try {
            val failedEvent = om.readValue(record.value(), GetProductsEvent::class.java)
            // 실패 이벤트 발행 (기존 로직 활용)
            productEventPublisher.publishProductsInformation(
                ProductsInformationResponseEvent(
                    eventId = UUID.randomUUID().toString(),
                    orderId = failedEvent.orderId,
                    success = false,
                    products = emptyList(),
                    errorMessage = "상품 정보 조회 실패 (DLQ): ${record.value()}"
                )
            )
        } catch (e: Exception) {
            log.error("DLT 처리 중 오류 발생: {}", record.value(), e)
        } finally {
            ack.acknowledge()
        }
    }
}
