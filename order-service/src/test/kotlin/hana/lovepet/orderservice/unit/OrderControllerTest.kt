package hana.lovepet.orderservice.unit

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.orderservice.api.controller.OrderController
import hana.lovepet.orderservice.api.controller.dto.request.ConfirmOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderItemRequest
import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.OrderSearchCondition
import hana.lovepet.orderservice.api.controller.dto.response.*
import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.api.service.OrderService
import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.RestControllerHandler
import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@WebMvcTest(OrderController::class)
@Import(RestControllerHandler::class)
class OrderControllerTest {
    @Autowired
    lateinit var mvc: MockMvc

    @MockitoBean
    lateinit var orderService: OrderService

    @Autowired
    lateinit var om: ObjectMapper

    @Test
    fun `주문 준비에 성공한다`() {
        //given
        val userId = 1L
        val items = getItems()
        val method = "카드"
        val request = CreateOrderRequest(userId, method, items)
        val json = om.writeValueAsString(request)

        val response = PrepareOrderResponse(
            orderId = "123L",
            eventId = "EVENT_123",
            amount = 365000L,
            status = OrderStatus.VALIDATING
        )

        BDDMockito.given(orderService.prepareOrder(request)).willReturn(response)

        //when & then
        mvc.post("/api/orders/prepare") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }
            .andExpect { status { isCreated() }}
            .andExpect { jsonPath("$.orderId") { value(response.orderId) } }
            .andExpect { jsonPath("$.eventId") { value(response.eventId) } }
            .andExpect { jsonPath("$.amount") { value(365000L) } }
            .andExpect { jsonPath("$.status") { value(OrderStatus.VALIDATING.toString()) } }
            .andDo { println() }
    }

    @Test
    fun `주문 준비에 실패할 수 있다`() {
        //given
        val userId = 1L
        val items = getItems()
        val method = "카드"
        val request = CreateOrderRequest(userId, method, items)
        val json = om.writeValueAsString(request)

        BDDMockito.given(orderService.prepareOrder(request)).willThrow(
            ApplicationException(
                ErrorCode.UNHEALTHY_SERVER_COMMUNICATION,
                "error occurred while retrieving user exists [id : $userId]"
            )
        )

        //when & then
        mvc.post("/api/orders/prepare") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }
            .andExpect { status { ErrorCode.UNHEALTHY_SERVER_COMMUNICATION.status }}
            .andExpect { jsonPath("$.message") { value("error occurred while retrieving user exists [id : $userId]") } }
            .andDo { println() }
    }

    @Test
    fun `주문 상태 조회에 성공한다`() {
        //given
        val orderNo = "ORDER_123"
        val response = OrderStatusResponse(
            orderNo = orderNo,
            status = OrderStatus.VALIDATING,
            amount = 365000L,
            errorMessage = null
        )

        BDDMockito.given(orderService.getStatus(orderNo)).willReturn(response)

        //when & then
        mvc.get("/api/orders/$orderNo/status")
            .andExpect { status { isOk() }}
            .andExpect { jsonPath("$.orderNo") { value(response.orderNo) } }
            .andExpect { jsonPath("$.status") { value(OrderStatus.VALIDATING.toString()) }}
            .andExpect { jsonPath("$.amount") { value(response.amount) } }
            .andDo { println() }
    }

    @Test
    fun `없는 주문번호로 주문 상태 조회시 예외가 발생한다`() {
        //given
        val orderNo = "ORDER_123"
        val response = OrderStatusResponse(
            orderNo = orderNo,
            status = OrderStatus.VALIDATING,
            amount = 365000L,
            errorMessage = null
        )
        BDDMockito.given(orderService.getStatus(orderNo))
            .willThrow(ApplicationException(ErrorCode.ORDER_NOT_FOUND, ErrorCode.ORDER_NOT_FOUND.message))

        //when & then
        mvc.get("/api/orders/$orderNo/status")
            .andExpect { status { ErrorCode.ORDER_NOT_FOUND.status }}
            .andExpect { jsonPath("$.message") { value(ErrorCode.ORDER_NOT_FOUND.message) } }
            .andDo { println() }
    }

    @Test
    fun `주문 목록 조회에 성공한다`() {
        //given
        val userId = 1L
        val searchCondition = OrderSearchCondition(userId, null, null)
        val pageable = PageRequest.of(0, 20)

        val orders = listOf(
            GetOrdersResponse(
                orderId = 1L,
                orderNo = "ORDER_123",
                userId = userId,
                userName = "홍길동",
                status = OrderStatus.VALIDATING,
                price = 365000L,
                createdAt = LocalDateTime.now(),
                paymentId = null
            )
        )
        val page = PageImpl(orders, pageable, 1)

        BDDMockito.given(orderService.getOrders(searchCondition, pageable)).willReturn(page)

        //when & then
        mvc.get("/api/orders") {
            param("userId", userId.toString())
        }
            .andExpect { status { isOk() }}
            .andExpect { jsonPath("$.content[0].orderNo") { value(page.content[0].orderNo) } }
            .andExpect { jsonPath("$.content[0].userId") { value(page.content[0].userId) } }
            .andExpect { jsonPath("$.content[0].status") { value(page.content[0].status.toString()) } }
            .andExpect { jsonPath("$.content[0].price") { value(page.content[0].price) } }
            .andDo { println() }
    }

    @Test
    fun `주문 아이템 조회에 성공한다`() {
        //given
        val orderNo = "ORDER_123"
        val orderItems = listOf(
            GetOrderItemsResponse(
                productId = 1L,
                productName = "로얄캐닌 고양이 사료",
                quantity = 1,
                unitPrice = 30000L,
                lineTotal = 30000L
            ),
            GetOrderItemsResponse(
                productId = 2L,
                productName = "로얄캐닌 고양이 사료 키튼",
                quantity = 1,
                unitPrice = 35000L,
                lineTotal = 35000L
            )
        )

        BDDMockito.given(orderService.getOrderItems(orderNo)).willReturn(orderItems)

        //when & then
        mvc.get("/api/orders/$orderNo/items")
            .andExpect { status { isOk() }}
            .andExpect { jsonPath("$[0].productId") { value(orderItems[0].productId) } }
            .andExpect { jsonPath("$[0].productName") { value(orderItems[0].productName) } }
            .andExpect { jsonPath("$[0].quantity") { value(orderItems[0].quantity) } }
            .andExpect { jsonPath("$[1].unitPrice") { value(orderItems[1].unitPrice) } }
            .andExpect { jsonPath("$[1].lineTotal") { value(orderItems[1].lineTotal) } }
            .andDo { println() }
    }

    @Test
    fun `주문 확정에 성공한다`() {
        //given
        val request = ConfirmOrderRequest(
            paymentKey = "payment_key_123",
            orderId = "ORDER_123",
            amount = 365000L
        )
        val json = om.writeValueAsString(request)

        val response = ConfirmOrderResponse(
            success = true,
            orderNo = "ORDER_123",
            paymentId = 1L,
            message = null
        )

        BDDMockito.given(orderService.OrderItemDecreaseRequest(request)).willReturn(response)

        //when & then
        mvc.patch("/api/orders/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }
            .andExpect { status { isOk() }}
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.orderNo") { value(response.orderNo) } }
            .andExpect { jsonPath("$.paymentId") { value(response.paymentId) } }
            .andDo { println() }
    }

    @Test
    fun `주문 확정에 실패할 수 있다`() {
        //given
        val request = ConfirmOrderRequest(
            paymentKey = "payment_key_123",
            orderId = "ORDER_123",
            amount = 365000L
        )
        val json = om.writeValueAsString(request)

        val response = ConfirmOrderResponse(
            success = false,
            orderNo = "ORDER_123",
            message = "결제 금액 불일치: expected=${400000L}, actual=${365000L}"
        )

        BDDMockito.given(orderService.OrderItemDecreaseRequest(request)).willReturn(response)

        //when & then
        mvc.patch("/api/orders/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }
            .andExpect { status { isBadRequest() }}
            .andExpect { jsonPath("$.success") { value(false) } }
            .andExpect { jsonPath("$.orderNo") { value(response.orderNo) } }
            .andExpect { jsonPath("$.message") { value(response.message) } }
            .andDo { println() }
    }

    private fun getItems(): List<CreateOrderItemRequest> {
        return listOf(
            CreateOrderItemRequest(1L, "로얄캐닌 고양이 사료", 30000L, 1),
            CreateOrderItemRequest(2L, "로얄캐닌 고양이 사료 키튼", 35000L, 1),
            CreateOrderItemRequest(3L, "로얄캐닌 고양이 사료 인도어", 40000L, 5),
            CreateOrderItemRequest(4L, "챠오츄르 마구로", 55000L, 7),
            CreateOrderItemRequest(5L, "챠오츄르 이카", 30000L, 10),
        )
    }
}