package hana.lovepet.paymentservice.api.payment.domain

import hana.lovepet.paymentservice.api.payment.domain.constant.LogType
import hana.lovepet.paymentservice.common.clock.TimeProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class PaymentLogTest {

    @Mock
    lateinit var timeProvider: TimeProvider

    @BeforeEach
    fun setUp() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 2, 9, 0, 0))
    }
    @Test
    fun `PaymentLog 엔티티 생성 테스트`() {
        val payment = Payment.fixture(timeProvider = timeProvider).apply { id = 1L }
        val log = PaymentLog(
            paymentId = payment.id!!,
            logType = LogType.RESPONSE,
            message = """{"response":"ok"}""",
            createdAt = timeProvider.now()
        )

        assertThat(log.paymentId).isEqualTo(payment.id)
        assertThat(log.logType).isEqualTo(LogType.RESPONSE)
        assertThat(log.message).isEqualTo("""{"response":"ok"}""")
        assertThat(log.createdAt).isEqualTo(timeProvider.now())
    }
}

