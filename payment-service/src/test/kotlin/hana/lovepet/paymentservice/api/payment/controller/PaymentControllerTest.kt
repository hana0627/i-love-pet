package hana.lovepet.paymentservice.api.payment.controller

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.paymentservice.api.payment.controller.dto.PaymentController
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCancelRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCreateRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCancelResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCreateResponse
import hana.lovepet.paymentservice.api.payment.service.PaymentService
import hana.lovepet.paymentservice.common.clock.TimeProvider
import hana.lovepet.paymentservice.common.exception.PgCommunicationException
import hana.lovepet.paymentservice.common.exception.RestControllerHandler
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@WebMvcTest(PaymentController::class)
@Import(RestControllerHandler::class)
class PaymentControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockitoBean
    lateinit var paymentService: PaymentService

    @Autowired
    lateinit var om: ObjectMapper

    @Test
    fun `결제가 성공적으로 이루어진다`() {
        //given
        val paymentCreateRequest = PaymentCreateRequest.fixture()
        val json = om.writeValueAsString(paymentCreateRequest)

        val response = PaymentCreateResponse(
            paymentId = 1L,
            paymentKey = "test-success-payment-key",
        )

        given(paymentService.createPayment(paymentCreateRequest)).willReturn(response)

        //when & then
        mvc.post("/api/payments") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }
            .andExpect { status { isCreated() } }
            .andExpect { jsonPath("$.paymentId") { value(response.paymentId) } }
            .andExpect { jsonPath("$.paymentKey") { value(response.paymentKey) } }
            .andExpect { jsonPath("$.isSuccess") { value(response.isSuccess) } }
            .andExpect { jsonPath("$.failReason") { value(response.failReason) } }
            .andDo { println() }
    }

    @Test
    fun `결제 실패시 예외가 발샐한다`() {
        //given
        val paymentCreateRequest = PaymentCreateRequest.fixture()
        val json = om.writeValueAsString(paymentCreateRequest)


        given(paymentService.createPayment(paymentCreateRequest)).willThrow(PgCommunicationException("PG 통신 실패"))

        //when & then
        mvc.post("/api/payments") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }
            .andExpect { status { is5xxServerError() }}
            .andExpect { jsonPath("$.message") { value("PG 통신 실패") } }
            .andDo { println() }
    }

    @Test
    fun `결제취소가 성공적으로 이루어진다`() {
        //given
        val paymentCancelRequest = PaymentCancelRequest("재고차감 실패")
        val paymentId = 1L
        val json = om.writeValueAsString(paymentCancelRequest)

        val response = PaymentCancelResponse(
            paymentId = paymentId,
            canceledAt = LocalDateTime.of(2025,8,12,9,0,0,0),
            transactionKey = "cancel_fed5aa96-8a95-4647-b351-699095d1485e",
            message = "성공적으로 취소 되었습니다."
        )

        given(paymentService.cancelPayment(paymentId, paymentCancelRequest)).willReturn(response)

        //when & then
        mvc.patch("/api/payments/$paymentId/cancel") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.paymentId") { value(response.paymentId) } }
            .andExpect { jsonPath("$.canceledAt") { value(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(response.canceledAt))} }
            .andExpect { jsonPath("$.transactionKey") { value(response.transactionKey) } }
            .andExpect { jsonPath("$.message") { value(response.message) } }
            .andDo { println() }
    }

    @Test
    fun `결제취소 실패시 예외가 발샐한다`() {
        //given
        val paymentCancelRequest = PaymentCancelRequest("재고차감 실패")
        val paymentId = 1L
        val json = om.writeValueAsString(paymentCancelRequest)


        given(paymentService.cancelPayment(paymentId, paymentCancelRequest)).willThrow(PgCommunicationException("PG사의 응답이 없습니다."))



        //when & then
        mvc.patch("/api/payments/$paymentId/cancel") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }
            .andExpect { status { is5xxServerError() }}
            .andExpect { jsonPath("$.message") { value("PG사의 응답이 없습니다.") } }
            .andDo { println() }
    }

}

