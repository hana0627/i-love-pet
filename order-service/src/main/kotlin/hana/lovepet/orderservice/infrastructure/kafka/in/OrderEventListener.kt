package hana.lovepet.orderservice.infrastructure.kafka.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.orderservice.api.service.OrderService
import hana.lovepet.orderservice.infrastructure.kafka.Groups
import hana.lovepet.orderservice.infrastructure.kafka.Topics
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.PaymentPreparedEvent
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.ProductsInformationResponseEvent
//import hana.lovepet.orderservice.infrastructure.webClient.product.dto.response.ProductInformationResponse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import kotlin.jvm.java

@Service
class OrderEventListener(
    private val orderService: OrderService,
    private val om: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(OrderEventListener::class.java)

    @KafkaListener(topics = ["product.information.response"], groupId = "product-service")
    fun onProductsInformation(record: ConsumerRecord<String, String>,
                              ack: Acknowledgment) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, ProductsInformationResponseEvent::class.java)
            if (readValue.success) {
                orderService.mappedTotalAmount(readValue.orderId, readValue.products)
                log.info("상품 정보 처리 완료. orderId={}, products={}", readValue.orderId, readValue.products)
            } else {
                // TODO OrderStatus = VALIDATION_FAILED 구현 추가
                log.error("상품 정보 조회 실패. orderId={}, error={}", readValue.orderId, readValue.errorMessage)
            }
            log.info("Payment prepared. orderId={}, products={}", readValue.orderId, readValue.products)
            ack.acknowledge()
        } catch (e: Exception) {
            // TODO OrderStatus = VALIDATION_FAILED 구현 추가
            log.error("prepared 처리 실패. payload={}, err={}", messages, e.message)
            // 재시도/ DLQ는 Step4~5에서 설정 (ErrorHandler or Dead Letter Topic)
            // 일단 nack을 안 하고 운영 단순화를 위해 ack (학습 환경)
            ack.acknowledge()
        }
    }


//    @KafkaListener(topics = ["payment.prepared"], groupId = "order-service")
    @KafkaListener(topics = [Topics.PAYMENT_PREPARED], groupId = Groups.ORDER)
    fun onPreparedRequest(record: ConsumerRecord<String, String>,
                          ack: Acknowledgment
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, PaymentPreparedEvent::class.java)

            val result = orderService.mappedPaymentId(readValue.orderId, readValue.paymentId)
            log.info("Payment prepared. orderId={}, paymentId={}", readValue.orderId, readValue.paymentId)
            ack.acknowledge()
        } catch (e: Exception) {
            log.error("결제 서비스 처리 실패. payload={}, err={}", messages, e.message, e)
            // 재시도/ DLQ는 Step4~5에서 설정 (ErrorHandler or Dead Letter Topic)
            // 일단 nack을 안 하고 운영 단순화를 위해 ack (학습 환경)
            ack.acknowledge()
        }
    }

}