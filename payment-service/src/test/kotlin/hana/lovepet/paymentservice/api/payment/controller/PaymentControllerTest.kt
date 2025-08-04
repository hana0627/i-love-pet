package hana.lovepet.paymentservice.api.payment.controller

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.paymentservice.api.payment.controller.dto.PaymentController
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCreateRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCreateResponse
import hana.lovepet.paymentservice.api.payment.service.PaymentService
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
import org.springframework.test.web.servlet.post

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

}

