package hana.lovepet.productservice.infrastructure.kafka.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.productservice.common.exception.ApplicationException
import hana.lovepet.productservice.api.product.service.ProductService
import hana.lovepet.productservice.infrastructure.kafka.Groups
import hana.lovepet.productservice.infrastructure.kafka.Topics
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.GetProductsEvent
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.ProductStockDecreaseEvent
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.ProductStockRollbackEvent
import hana.lovepet.productservice.infrastructure.kafka.out.ProductEventPublisher
import hana.lovepet.productservice.infrastructure.kafka.out.dto.ProductStockDecreasedEvent
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
//        include = [Exception::class],
        exclude = [ApplicationException::class],
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
            log.info("=== 메시지 파싱 성공: orderId=${readValue.orderId}, items=${readValue.items} ===")
            productService.getProductsInformation(readValue.orderId, readValue.items)
            log.info("=== productService.getProductsInformation 호출 완료 ===")
            ack.acknowledge()
        } catch (e: Exception) {
            log.error("getProductsInformation 처리 실패. payload={}, err={}", message, e.message, e)
            throw e
//            ack.acknowledge()
        }
    }


    @RetryableTopic(
        attempts = "3", // 최대 3회 실행
        backoff = Backoff(delay = 1000), //1 초 간격으로 재시도
//        include = [Exception::class],
        exclude = [ApplicationException::class],
        dltStrategy = DltStrategy.FAIL_ON_ERROR, // 모든 재시도 실패시 DLT로 전송
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(topics = [Topics.PRODUCT_STOCK_DECREASE], groupId = Groups.PRODUCT)
    fun onDecreaseStock(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        val message = record.value()
        try {
            val readValue = om.readValue(message, ProductStockDecreaseEvent::class.java)
            productService.decreaseStock(readValue.orderId, readValue.products)
            ack.acknowledge()
        } catch (e: Exception) {
            log.error("getProductsInformation 처리 실패. payload={}, err{}", message, e.message)
            throw e
//            ack.acknowledge()
        }
    }


    @RetryableTopic(
        attempts = "3", // 최대 3회 실행
        backoff = Backoff(delay = 1000), //1 초 간격으로 재시도
//        include = [Exception::class],
        exclude = [ApplicationException::class],
        dltStrategy = DltStrategy.FAIL_ON_ERROR, // 모든 재시도 실패시 DLT로 전송
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(topics = [Topics.PRODUCT_STOCK_ROLLBACK], groupId = Groups.PRODUCT)
    fun onRollbackStock(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        val message = record.value()
        try {
            val readValue = om.readValue(message, ProductStockRollbackEvent::class.java)
            productService.rollbackStock(readValue.orderId, readValue.products)
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
            when (record.topic()) {
                Topics.PRODUCT_INFORMATION_REQUEST+"-dlt" -> {
                    val failedEvent = om.readValue(record.value(), GetProductsEvent::class.java)
                    productEventPublisher.publishProductsInformation(
                        ProductsInformationResponseEvent(
                            eventId = UUID.randomUUID().toString(),
                            orderId = failedEvent.orderId,
                            success = false,
                            products = emptyList(),
                            errorMessage = "상품 정보 조회 실패 (DLQ): ${record.value()}"
                        )
                    )
                }

                Topics.PRODUCT_STOCK_DECREASE+"-dlt" -> {
                    val failedEvent = om.readValue(record.value(), ProductStockDecreaseEvent::class.java)
                    productEventPublisher.publishProductStockDecreased(
                        ProductStockDecreasedEvent(
                            eventId = UUID.randomUUID().toString(),
                            orderId = failedEvent.orderId,
                            success = false,
                            errorMessage = "재고 차감 실패 (DLQ): ${record.value()}",
                            idempotencyKey = failedEvent.orderId.toString()
                        )
                    )
                }
                Topics.PRODUCT_STOCK_ROLLBACK+"-dlt" -> {
                    val failedEvent = om.readValue(record.value(), ProductStockRollbackEvent::class.java)
                    log.error("재고 복구 실패 (수동 개입 필요) - orderId: {}, products: {}",
                        failedEvent.orderId, failedEvent.products)
                }
            }
        } catch (e: Exception) {
            log.error("DLT 처리 중 오류 발생: {}", record.value(), e)
        } finally {
            ack.acknowledge()
        }
    }
}
