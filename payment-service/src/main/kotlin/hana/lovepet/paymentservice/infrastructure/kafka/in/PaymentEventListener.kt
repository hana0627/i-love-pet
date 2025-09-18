package hana.lovepet.paymentservice.infrastructure.kafka.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.paymentservice.api.payment.service.PaymentService
import hana.lovepet.paymentservice.common.exception.ApplicationException
import hana.lovepet.paymentservice.infrastructure.kafka.Groups
import hana.lovepet.paymentservice.infrastructure.kafka.Topics
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentCancelEvent
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentPendingEvent
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentPrepareEvent
import hana.lovepet.paymentservice.infrastructure.kafka.out.PaymentEventPublisher
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.PaymentCanceledFailEvent
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.PaymentConfirmedFailEvent
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.PaymentPrepareFailEvent
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
class PaymentEventListener(
    private val paymentService: PaymentService,
    private val om: ObjectMapper,
    private val paymentEventPublisher: PaymentEventPublisher,
) {

    private val log = LoggerFactory.getLogger(PaymentEventListener::class.java)

    @RetryableTopic(
        attempts = "3", // 최대 3회 실행
        backoff = Backoff(delay = 1000), //1 초 간격으로 재시도
//        include = [Exception::class],
        exclude = [ApplicationException::class],
        dltStrategy = DltStrategy.FAIL_ON_ERROR, // 모든 재시도 실패시 DLT로 전송
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(topics = [Topics.PAYMENT_PREPARE], groupId = Groups.PAYMENT)
    fun onPrepareRequest(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, PaymentPrepareEvent::class.java)
            val result = paymentService.preparePayment(
                userId = readValue.userId,
                orderId = readValue.orderId,
                amount = readValue.amount,
                method = readValue.method ?: "카드",
            )
            log.info("Payment prepared. orderId={}, paymentId={}", readValue.orderId, result.paymentId)
            ack.acknowledge()

        } catch (e: Exception) {
            log.error("prepare.requested 처리 실패. payload={}, err={}", messages, e.message, e)
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
    @KafkaListener(topics = [Topics.PAYMENT_PENDING], groupId = Groups.PAYMENT)
    fun onPaymentPendingRequest(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, PaymentPendingEvent::class.java)
            val result = paymentService.confirmPayment(
                orderId = readValue.orderId,
                paymentId = readValue.paymentId,
                orderNo = readValue.orderNo,
                paymentKey = readValue.paymentKey,
                amount = readValue.amount,
            )
            log.info("Payment confirmed. orderId={}, paymentId={}", readValue.orderId, result.paymentId)
            ack.acknowledge()

        } catch (e: Exception) {
            log.error("pending.requested 처리 실패. payload={}, err={}", messages, e.message, e)
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
    @KafkaListener(topics = [Topics.PAYMENT_CANCEL], groupId = Groups.PAYMENT)
    fun onPaymentCancelRequest(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        val messages = record.value()
        try {
            val readValue = om.readValue(messages, PaymentCancelEvent::class.java)
//            val result = paymentService.refundPayment(readValue)
            val result = paymentService.cancelPayment(readValue)
            log.info("Payment cencel. orderNo={}, paymentId={}", readValue.orderNo, result.paymentId)
            ack.acknowledge()

        } catch (e: Exception) {
            log.error("cancel.requested 처리 실패. payload={}, err={}", messages, e.message, e)
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
                Topics.PAYMENT_PREPARE + "-dlt" -> {
                    val failedEvent = om.readValue(record.value(), PaymentPrepareEvent::class.java)
                    // 비지니스 내부에서 어떤 뭔가를 처리할 필요는 없음. (예외가 발생했다면 rollback을 예상하기 떄문)
                    paymentEventPublisher.publishPaymentPrepareFailed(
                        PaymentPrepareFailEvent(
                            eventId = UUID.randomUUID().toString(),
                            orderId = failedEvent.orderId,
                            idempotencyKey = failedEvent.orderId.toString(),
                        )
                    )
                    log.error("처리 실패: orderId={}", failedEvent.orderId)
                }

                Topics.PAYMENT_PENDING + "-dlt" -> {
                    val failEvent = om.readValue(record.value(), PaymentPendingEvent::class.java)
                    paymentService.failPayment(failEvent.paymentId, "결제 확정 실패 orderId: ${failEvent.orderId}")
                    paymentEventPublisher.publishPaymentConfirmedFail(
                        PaymentConfirmedFailEvent(
                            eventId = UUID.randomUUID().toString(),
                            orderId = failEvent.orderId,
                            idempotencyKey = failEvent.orderId.toString(),
                        )
                    )
                }

                Topics.PAYMENT_CANCEL + "-dlt" -> {
                    val failEvent = om.readValue(record.value(), PaymentCancelEvent::class.java)
                    val isCanceled = paymentService.isPaymentCanceled(failEvent.paymentId)

                    if(isCanceled) {
                        log.error("결제취소 되었으나 장애발생. paymentId = ${failEvent.paymentId}")
                    }
                    else {
                        // 3차례의 재시도 후에도 결제취소에 실패했으면
                        // 다시 결제취소를 보낸다고 해서 결제취소가 제대로 될 가능성이 낮아보이는데. 이렇게 처리하는건 별로?
                        log.error("결제 취소실패, 수동 결제취소 필요. paymentId = ${failEvent.paymentId}")
                    }
                    // 결제취소에 실패했음 이벤트 발행
                    paymentEventPublisher.publishPaymentCanceledFail(
                        PaymentCanceledFailEvent(
                            eventId = UUID.randomUUID().toString(),
                            orderId = failEvent.orderId,
                            idempotencyKey = failEvent.orderId.toString(),
                        )
                    )
                }
                else -> {
                    log.error("알 수 없는 DLT 토픽: record : {}", record.topic())
                }
            }
        } catch (e: Exception) {
            log.error("DLT 처리 중 오류 발생: {}", record.value(), e)
        } finally {
            ack.acknowledge()
        }
    }
}
