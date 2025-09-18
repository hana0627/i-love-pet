package hana.lovepet.paymentservice.unit

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.paymentservice.common.exception.constant.ErrorCode
import hana.lovepet.paymentservice.api.payment.controller.PaymentController
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentLogResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentResponse
import hana.lovepet.paymentservice.api.payment.domain.constant.LogType
import hana.lovepet.paymentservice.api.payment.domain.constant.PaymentStatus
import hana.lovepet.paymentservice.api.payment.service.PaymentService
import hana.lovepet.paymentservice.common.clock.TimeProvider
import hana.lovepet.paymentservice.common.exception.ApplicationException
import hana.lovepet.paymentservice.common.exception.RestControllerHandler
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@WebMvcTest(PaymentController::class)
@Import(RestControllerHandler::class)
class PaymentControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockitoBean
    lateinit var timeProvider: TimeProvider

    @MockitoBean
    lateinit var paymentService: PaymentService

    @Autowired
    lateinit var om: ObjectMapper


    @Test
    fun `결제정보 조회에 성공한다`() {
        //given
        val paymentId = 1L
        BDDMockito.given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val response = GetPaymentResponse(
            paymentId = paymentId,
            status = PaymentStatus.SUCCESS,
            amount = 10000,
            method = "카드",
            occurredAt = timeProvider.now(),
            description = "",
        )
        BDDMockito.given(paymentService.getPayment(paymentId)).willReturn(
            response
        )


        //when & then
        mvc.get("/api/payments/$paymentId")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.paymentId") { value(response.paymentId) } }
            .andExpect { jsonPath("$.status") { value(response.status.toString()) } }
            .andExpect { jsonPath("$.amount") { value(response.amount) } }
            .andExpect { jsonPath("$.method") { value(response.method) } }
            .andExpect { jsonPath("$.occurredAt") { value((response.occurredAt.toString() + ":00")) } }
            .andExpect { jsonPath("$.description") { value(response.description) } }
            .andDo { print() }
    }


    @Test
    fun `없는 paymentId로 결제정보 조회시 예외가 발생한다`() {
        //given
        val wrongPaymentId = 9999L
        BDDMockito.given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        BDDMockito.given(paymentService.getPayment(wrongPaymentId)).willThrow(
            ApplicationException(ErrorCode.PAYMENT_NOT_FOUND, "Payments not found [id = $wrongPaymentId]")
        )


        //when & then
        mvc.get("/api/payments/$wrongPaymentId")
            .andExpect { status { isNotFound() } }
            .andExpect { jsonPath("$.message") { value("Payments not found [id = $wrongPaymentId]") } }
            .andDo { print() }
    }


    @Test
    fun `결제기록 조회에 성공한다`() {
        //given
        val paymentId = 1L
        BDDMockito.given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val response = listOf(
            GetPaymentLogResponse(
                logType = LogType.REQUEST,
                message = "결제 요청 : orderId = 12345",
                createdAt = timeProvider.now(),
            ),
            GetPaymentLogResponse(
                logType = LogType.RESPONSE,
                message = "결제 승인 완료: orderId = 12345, tossPaymentKey = 5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1",
                createdAt = timeProvider.now(),
            )
        )

        BDDMockito.given(paymentService.getPaymentLogs(paymentId)).willReturn(
            response
        )

        //when & then
        mvc.get("/api/payments/$paymentId/logs")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$[0].logType") { value(response[0].logType.toString()) } }
            .andExpect { jsonPath("$[0].message") { value(response[0].message) } }
            .andExpect { jsonPath("$[1].createdAt") { value((response[1].createdAt.toString())+":00") } }
            .andDo { print() }
    }

    @Test
    fun `없는 paymentId로 결제기록 조회시 빈 리스트를 반환한다`() {
        //given
        val wrongPaymentId = 9999L

        val response = emptyList<GetPaymentLogResponse>()

        BDDMockito.given(paymentService.getPaymentLogs(wrongPaymentId)).willReturn(
            response
        )

        //when & then
        mvc.get("/api/payments/$wrongPaymentId/logs")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$") { isArray() } }
            .andExpect { jsonPath("$") { isEmpty() } }
            .andDo { print() }
    }

}