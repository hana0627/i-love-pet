package hana.lovepet.paymentservice.api.payment.domain

import hana.lovepet.paymentservice.api.payment.domain.constant.PaymentStatus
import hana.lovepet.paymentservice.common.clock.TimeProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class PaymentTest {


    @Mock
    lateinit var timeProvider: TimeProvider

    @BeforeEach
    fun setUp() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 2, 9, 0, 0))
    }

    @Test
    fun `결제 최소생성시 상태는 PENDING이다`() {
        //given
        val userId: Long = 1L
        val orderId: Long = 1001L
        val paymentKey: String = "temp_pgid_UUID"
        val amount: Long = 120000L
        val method: String = "카드"

        //when
        val payment = Payment(
            userId = userId,
            orderId = orderId,
            paymentKey = paymentKey,
            amount = amount,
            method = method,
            requestedAt = timeProvider.now()
        )

        //then
        assertThat(payment.userId).isEqualTo(userId)
        assertThat(payment.orderId).isEqualTo(orderId)
        assertThat(payment.paymentKey).isEqualTo(paymentKey)
        assertThat(payment.amount).isEqualTo(amount)
        assertThat(payment.method).isEqualTo(method)
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(payment.requestedAt).isEqualTo(timeProvider.now())

    }

    @Test
    fun `결제에 성공하면 SUCCESS 상태가 된다`() {
        //given
        val payment = Payment.fixture(
            timeProvider = timeProvider
        )
        val realPaymentKey = "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1"
        val pgResponse = "{\"result\":\"ok\"}"

        //when
        payment.approve(timeProvider = timeProvider, paymentKey = realPaymentKey, pgResponse = pgResponse)

        //then
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(payment.approvedAt).isEqualTo(timeProvider.now())
        assertThat(payment.paymentKey).isEqualTo(realPaymentKey)
        assertThat(payment.pgResponse).isEqualTo(pgResponse)
        assertThat(payment.updatedAt).isEqualTo(timeProvider.now())
    }


    @Test
    fun `PENDING 상태가 아니면 결제에 실패한다`() {
        //given
        val payment = Payment.fixture(
            timeProvider = timeProvider
        )
        val realPaymentKey = "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1"
        val pgResponse = "{\"result\":\"ok\"}"
        payment.approve(timeProvider = timeProvider, paymentKey = realPaymentKey, pgResponse = pgResponse)

        //when
        val result = assertThrows<IllegalStateException> {
            payment.approve(
                timeProvider = timeProvider,
                paymentKey = realPaymentKey,
                pgResponse = pgResponse
            )
        }

        //then
        assertThat(result.message).isEqualTo("결제 승인 불가 상태입니다.")
    }


    @Test
    fun `결제에 실패하면 FAIL 상태가 된다`() {
        //given
        val payment = Payment.fixture(
            timeProvider = timeProvider
        )
        val realPaymentKey = "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1"
        val failReason = "한도초과"
        val pgResponse = """{"code":"REJECT_CARD_PAYMENT","message":"한도초과 혹은 잔액부족으로 결제에 실패했습니다."}"""


        //when
        payment.fail(
            timeProvider = timeProvider,
            paymentKey = realPaymentKey,
            pgResponse = pgResponse,
            failReason = failReason
        )

        //then
        assertThat(payment.status).isEqualTo(PaymentStatus.FAIL)
        assertThat(payment.paymentKey).isEqualTo(realPaymentKey)
        assertThat(payment.failedAt).isEqualTo(timeProvider.now())
        assertThat(payment.failReason).isEqualTo(failReason)
        assertThat(payment.pgResponse).isEqualTo(pgResponse)
        assertThat(payment.updatedAt).isEqualTo(timeProvider.now())
    }


    @Test
    fun `결제에 실패하면 FAIL 상태가 된다2`() {
        //given
        val payment = Payment.fixture(
            timeProvider = timeProvider
        )
        val realPaymentKey = null
        val failReason = "한도초과"
        val pgResponse = """{"code":"REJECT_CARD_PAYMENT","message":"한도초과 혹은 잔액부족으로 결제에 실패했습니다."}"""


        //when
        payment.fail(
            timeProvider = timeProvider,
            paymentKey = realPaymentKey,
            pgResponse = pgResponse,
            failReason = failReason
        )

        //then
        assertThat(payment.status).isEqualTo(PaymentStatus.FAIL)
        assertThat(payment.paymentKey).isEqualTo("temp_pgid_UUID")
        assertThat(payment.failedAt).isEqualTo(timeProvider.now())
        assertThat(payment.failReason).isEqualTo(failReason)
        assertThat(payment.pgResponse).isEqualTo(pgResponse)
        assertThat(payment.updatedAt).isEqualTo(timeProvider.now())
    }

    @Test
    fun `PENDING 상태가 아니면 결제 실패 처리시 예외가 발생한다`() {
        //given
        val payment = Payment.fixture(
            timeProvider = timeProvider
        )

        val realPaymentKey = "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1"
        val failReason = "한도초과"
        val pgResponse = """{"code":"REJECT_CARD_PAYMENT","message":"한도초과 혹은 잔액부족으로 결제에 실패했습니다."}"""

        payment.approve(timeProvider = timeProvider, paymentKey = realPaymentKey, pgResponse = pgResponse)

        //when
        val result = assertThrows<IllegalStateException> {
            payment.fail(
                timeProvider = timeProvider,
                paymentKey = realPaymentKey,
                pgResponse = pgResponse,
                failReason = failReason
            )
        }

        //then
        assertThat(result.message).isEqualTo("결제 실패 불가 상태입니다.")
    }

    @Test
    fun `결제 취소에 성공하면 CANCEL 상태가 된다`() {
        //given
        val payment = Payment.fixture(timeProvider = timeProvider)
            .apply {
                approve(
                    timeProvider = timeProvider,
                    paymentKey = "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1",
                    pgResponse = "{\"result\":\"ok\"}"
                )
            }

        val cancelReason = "고객요청"

        //when
        payment.cancel(timeProvider = timeProvider, description = cancelReason)

        //then
        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(payment.canceledAt).isEqualTo(timeProvider.now())
        assertThat(payment.description).isEqualTo(cancelReason)
        assertThat(payment.updatedAt).isEqualTo(timeProvider.now())
    }

    @Test
    fun `SUCCESS 상태가 아니면 결제 취소가 불가능하다`() {
        //given
        val payment = Payment.fixture(timeProvider = timeProvider)
            .apply {
                approve(
                    timeProvider = timeProvider,
                    paymentKey = "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1",
                    pgResponse = "{\"result\":\"ok\"}"
                )
            }

        val cancelReason = "고객요청"
        payment.cancel(timeProvider = timeProvider, description = cancelReason)

        //when
        val result = assertThrows<IllegalStateException> {
            payment.cancel(
                timeProvider = timeProvider,
                description = cancelReason
            )
        }

        //then
        assertThat(result.message).isEqualTo("이미 취소된 요청입니다.")
    }

    @Test
    fun `SUCCESS 상태가 아니면 결제 취소가 불가능하다2`() {
        //given
        val payment = Payment.fixture(timeProvider = timeProvider)
        val realPaymentKey = "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1"
        val failReason = "한도초과"
        val pgResponse = """{"code":"REJECT_CARD_PAYMENT","message":"한도초과 혹은 잔액부족으로 결제에 실패했습니다."}"""

        payment.fail(
            timeProvider = timeProvider,
            paymentKey = realPaymentKey,
            pgResponse = pgResponse,
            failReason = failReason
        )

        val cancelReason = "고객요청"

        //when
        val result = assertThrows<IllegalStateException> {
            payment.cancel(
                timeProvider = timeProvider,
                description = cancelReason
            )
        }

        //then
        assertThat(result.message).isEqualTo("승인된 결제만 취소할 수 있습니다.")
    }

    @Test
    fun `결제 환불시 REFUND 상태가 된다`() {
        //given
        val payment = Payment.fixture(timeProvider = timeProvider)
            .apply {
                approve(
                    timeProvider = timeProvider,
                    paymentKey = "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1",
                    pgResponse = "{\"result\":\"ok\"}"
                )
            }

        val refundReason = "단순 변심"

        //when
        payment.refund(timeProvider = timeProvider, description = refundReason)

        //then
        assertThat(payment.status).isEqualTo(PaymentStatus.REFUNDED)
        assertThat(payment.refundedAt).isEqualTo(timeProvider.now())
        assertThat(payment.description).isEqualTo(refundReason)
        assertThat(payment.updatedAt).isEqualTo(timeProvider.now())
    }

    @Test
    fun `결제 환불시 REFUND 상태가 된다2`() {
        //given
        val payment = Payment.fixture(timeProvider = timeProvider)
            .apply {
                approve(
                    timeProvider = timeProvider,
                    paymentKey = "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1",
                    pgResponse = "{\"result\":\"ok\"}"
                )
            }
            .apply { cancel(timeProvider = timeProvider, description = "단순 변심") }

        val refundReason = "단순 변심"

        //when
        payment.refund(timeProvider = timeProvider, description = refundReason)

        //then
        assertThat(payment.status).isEqualTo(PaymentStatus.REFUNDED)
        assertThat(payment.refundedAt).isEqualTo(timeProvider.now())
        assertThat(payment.description).isEqualTo(refundReason)
        assertThat(payment.updatedAt).isEqualTo(timeProvider.now())
    }

    @Test
    fun `SUCCESS, CANCELED가 아니면 환불 불가 예외가 발생한다`() {
        // given
        val payment = Payment.fixture(timeProvider = timeProvider)
        payment.fail(
            timeProvider = timeProvider,
            paymentKey = "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1",
            failReason = "한도초과",
            pgResponse = "{\"code\":\"LIMIT_OVER\"}"
        )

        // when
        val result = assertThrows<IllegalStateException> {
            payment.refund(timeProvider = timeProvider, description = "테스트환불")
        }

        // then
        assertThat(result.message).isEqualTo("환불 처리 불가 상태입니다.")
    }

    @Test
    fun `SUCCESS, CANCELED가 아니면 환불 불가 예외가 발생한다2`() {
        // given
        val payment = Payment.fixture(timeProvider = timeProvider)
            .apply {
                approve(
                    timeProvider = timeProvider,
                    paymentKey = "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1",
                    pgResponse = "{\"result\":\"ok\"}"
                )
            }

        payment.refund(timeProvider = timeProvider, description = "테스트환불")

        // when
        val result = assertThrows<IllegalStateException> {
            payment.refund(timeProvider = timeProvider, description = "테스트환불")
        }

        // then
        assertThat(result.message).isEqualTo("환불 처리 불가 상태입니다.")
    }

}
