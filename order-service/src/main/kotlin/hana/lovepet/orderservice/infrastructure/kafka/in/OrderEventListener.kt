package hana.lovepet.orderservice.infrastructure.kafka.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.orderservice.api.service.OrderService
import hana.lovepet.orderservice.infrastructure.kafka.Groups
import hana.lovepet.orderservice.infrastructure.kafka.Topics
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.PaymentPreparedEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class OrderEventListener(
    private val orderService: OrderService,
    private val om: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(OrderEventListener::class.java)


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
            log.error("prepared 처리 실패. payload={}, err={}", messages, e.message, e)
            // 재시도/ DLQ는 Step4~5에서 설정 (ErrorHandler or Dead Letter Topic)
            // 일단 nack을 안 하고 운영 단순화를 위해 ack (학습 환경)
            ack.acknowledge()
        }
    }
}