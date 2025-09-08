package hana.lovepet.orderservice.infrastructure.kafka.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.orderservice.api.service.OrderService
import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.infrastructure.kafka.Groups
import hana.lovepet.orderservice.infrastructure.kafka.Topics
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.PaymentPrepareFailEvent
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.PaymentPreparedEvent
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.ProductsInformationResponseEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.DltStrategy
import org.springframework.kafka.support.Acknowledgment
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Service

@Service
class OrderEventListener(
    private val orderService: OrderService,
    private val om: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(OrderEventListener::class.java)

    @RetryableTopic(
        attempts = "3", // 최대 3회 실행
        backoff = Backoff(delay = 1000), //1 초 간격으로 재시도
//        include = [Exception::class],
        exclude = [ApplicationException::class],
        dltStrategy = DltStrategy.FAIL_ON_ERROR, // 모든 재시도 실패시 DLT로 전송
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(topics = [Topics.PRODUCT_INFORMATION_RESPONSE], groupId = Groups.ORDER)
    fun onProductsInformation(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, ProductsInformationResponseEvent::class.java)
            if (readValue.success) {
                orderService.mappedTotalAmount(readValue.orderId, readValue.products)
                log.info("상품 정보 처리 완료. orderId={}, products={}", readValue.orderId, readValue.products)
            } else {
                orderService.validationFail(readValue.orderId)
                log.error("상품 정보 조회 실패. orderId={}, error={}", readValue.orderId, readValue.errorMessage)
            }
            log.info("Payment prepared. orderId={}, products={}", readValue.orderId, readValue.products)
            ack.acknowledge()
        } catch (e: Exception) {
            log.error("prepared 처리 실패. payload={}, err={}", messages, e.message)
            throw e
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
    @KafkaListener(topics = [Topics.PAYMENT_PREPARE_FAIL], groupId = Groups.ORDER)
    fun onPrepareFailRequest(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, PaymentPrepareFailEvent::class.java)

            val result = orderService.paymentPrepareFail(readValue.orderId)
            log.info("Payment prepared. orderId={}, paymentId={}", readValue.orderId)
            ack.acknowledge()
        } catch (e: Exception) {
            log.error("처리 실패. payload={}, err={}", messages, e.message, e)
//            ack.acknowledge()
            throw e
        }
    }

    // TODO DLQ 처리. 근데 이거 실패할 가능성이.. 있나...
    @KafkaListener(topics = [Topics.PAYMENT_PREPARED], groupId = Groups.ORDER)
    fun onPreparedRequest(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
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


    @DltHandler
    fun handleDeadLetterTopic(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        log.error("DLT로 전송된 상품 정보 요청 처리: {}", record.value())

        try {
            when (record.topic()) {
                Topics.PRODUCT_INFORMATION_RESPONSE + "-dlt" -> {
                    val failedEvent = om.readValue(record.value(), ProductsInformationResponseEvent::class.java)
                    orderService.orderProcessFail(failedEvent.orderId)
                    log.error("상품 정보 응답 처리 실패: orderId={}", failedEvent.orderId)
                }
                Topics.PAYMENT_PREPARE_FAIL + "dlt" -> {
                    val failedEvent = om.readValue(record.value(), PaymentPrepareFailEvent::class.java)
                    // 단순로깅
                    log.error("결제 준비 실패 메시지 처리도 실패: orderId={}", failedEvent.orderId)
                }
                // 이경우의 예외는 발생하지 않는다고 가정
                // "payment.prepared-dlt" -> {
                //     val failedEvent = om.readValue(record.value(), PaymentPreparedEvent::class.java)
                //     orderService.paymentProcessFail(failedEvent.orderId)
                // }
                else -> {
                    log.error("알 수 없는 DLT 토픽: {}", record.topic())
                }
            }
        } catch (e: Exception) {
            log.error("DLT 처리 중 오류 발생: {}", record.value(), e)
        } finally {
            ack.acknowledge()
        }
    }

}