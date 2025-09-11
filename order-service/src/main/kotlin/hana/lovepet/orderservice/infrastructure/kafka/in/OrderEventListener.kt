package hana.lovepet.orderservice.infrastructure.kafka.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.orderservice.api.service.OrderService
import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.infrastructure.kafka.Groups
import hana.lovepet.orderservice.infrastructure.kafka.Topics
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.PaymentCanceledEvent
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.PaymentCanceledFailEvent
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.PaymentConfirmedEvent
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.PaymentConfirmedFailEvent
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.PaymentPrepareFailEvent
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.PaymentPreparedEvent
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.ProductStockDecreasedEvent
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


    @RetryableTopic(
        attempts = "3", // 최대 3회 실행
        backoff = Backoff(delay = 1000), //1 초 간격으로 재시도
//        include = [Exception::class],
        exclude = [ApplicationException::class],
        dltStrategy = DltStrategy.FAIL_ON_ERROR, // 모든 재시도 실패시 DLT로 전송
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(topics = [Topics.PRODUCT_STOCK_DECREASED], groupId = Groups.ORDER)
    fun onDecreasedStock(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, ProductStockDecreasedEvent::class.java)
            if(readValue.success) {
                val result = orderService.processOrder(readValue.orderId)
            }else {
                val result = orderService.decreaseStockFail(readValue.orderId)
            }
            log.info("stock decreased. orderId={}, paymentId={}", readValue.orderId)
            ack.acknowledge()
        } catch (e: Exception) {
            log.error("처리 실패. payload={}, err={}", messages, e.message, e)
//            ack.acknowledge()
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
    @KafkaListener(topics = [Topics.PAYMENT_CONFIRMED], groupId = Groups.ORDER)
    fun onPaymentConfirmed(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, PaymentConfirmedEvent::class.java)
            val result = orderService.confirmedOrder(readValue.orderId)
            log.info("payment confirmed. orderId={}, paymentId={}", readValue.orderId)
            ack.acknowledge()
        } catch (e: Exception) {
            log.error("처리 실패. payload={}, err={}", messages, e.message, e)
//            ack.acknowledge()
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
    @KafkaListener(topics = [Topics.PAYMENT_CONFIRMED_FAIL], groupId = Groups.ORDER)
    fun onPaymentConfirmedFail(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, PaymentConfirmedFailEvent::class.java)
            val result = orderService.failOrder(readValue.orderId)
            ack.acknowledge()
        } catch (e: Exception) {
            log.error("처리 실패. payload={}, err={}", messages, e.message, e)
//            ack.acknowledge()
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
    @KafkaListener(topics = [Topics.PAYMENT_CANCELED], groupId = Groups.ORDER)
    fun onPaymentCanceled(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, PaymentCanceledEvent::class.java)
            val result = orderService.canceledOrder(readValue.orderId)
            ack.acknowledge()
        } catch (e: Exception) {
            log.error("처리 실패. payload={}, err={}", messages, e.message, e)
//            ack.acknowledge()
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
    @KafkaListener(topics = [Topics.PAYMENT_CANCELED_FAIL], groupId = Groups.ORDER)
    fun onPaymentCanceledFail(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, PaymentCanceledFailEvent::class.java)
            val result = orderService.canceledFailOrder(readValue.orderId)
            ack.acknowledge()
        } catch (e: Exception) {
            log.error("처리 실패. payload={}, err={}", messages, e.message, e)
//            ack.acknowledge()
            throw e
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
                Topics.PAYMENT_PREPARE_FAIL + "-dlt" -> {
                    val failedEvent = om.readValue(record.value(), PaymentPrepareFailEvent::class.java)
                    // 단순로깅
                    log.error("결제 준비 실패 메시지 처리도 실패: orderId={}", failedEvent.orderId)
                }
                 Topics.PAYMENT_PREPARED +"-dlt" -> {
                     val failedEvent = om.readValue(record.value(), PaymentPreparedEvent::class.java)
                     log.error("결제 준비 실패: orderId={}", failedEvent.orderId)
                 }
                Topics.PRODUCT_STOCK_DECREASED + "-dlt" -> {
                    val failedEvent = om.readValue(record.value(), ProductStockDecreasedEvent::class.java)

                    if (failedEvent.success) {
                        // processOrder에서 실패한 경우 - 재고 롤백 필요
                        log.error("결제 처리 실패로 재고 롤백 필요: orderId={}", failedEvent.orderId)
                        orderService.rollbackStockAndCancel(failedEvent.orderId)
                    } else {
                        // decreaseStockFail에서 실패한 경우 - 이미 실패 처리된 상태이므로 로깅만
                        log.error("재고 차감 실패 처리도 실패: orderId={}", failedEvent.orderId)
                        orderService.failOrder(failedEvent.orderId)
                    }
                }
                Topics.PAYMENT_CONFIRMED+"-dlt" -> {
                    val failedEvent = om.readValue(record.value(), PaymentConfirmedEvent::class.java)
                    log.error("결제완료 처리 실패: orderId={}", failedEvent.orderId)
                }
                Topics.PAYMENT_CONFIRMED_FAIL+"-dlt" -> {
                    val failedEvent = om.readValue(record.value(), PaymentConfirmedFailEvent::class.java)
                    log.error("결제실패 후 처리 실패: orderId={}", failedEvent.orderId)
                }

                Topics.PAYMENT_CANCELED+"-dlt" -> {
                    val failedEvent = om.readValue(record.value(), PaymentCanceledEvent::class.java)
                    log.error("결제취소 후 처리 실패: orderId={}", failedEvent.orderId)
                }


                Topics.PAYMENT_CANCELED_FAIL+"-dlt" -> {
                    val failedEvent = om.readValue(record.value(), PaymentCanceledFailEvent::class.java)
                    log.error("결제취소실패 후 처리 실패: orderId={}", failedEvent.orderId)
                }


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