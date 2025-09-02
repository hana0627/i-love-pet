//package hana.lovepet.orderservice.api.controller
//
//import com.fasterxml.jackson.databind.ObjectMapper
//import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderRequest
//import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderItemRequest
//import hana.lovepet.orderservice.api.controller.dto.response.OrderCreateResponse
//import hana.lovepet.orderservice.api.service.OrderService
//import hana.lovepet.orderservice.common.exception.RestControllerHandler
//import org.junit.jupiter.api.Test
//import org.mockito.BDDMockito.given
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
//import org.springframework.context.annotation.Import
//import org.springframework.http.MediaType
//import org.springframework.test.context.bean.override.mockito.MockitoBean
//import org.springframework.test.web.servlet.MockMvc
//import org.springframework.test.web.servlet.post
//
//@WebMvcTest(OrderController::class)
//@Import(RestControllerHandler::class)
//class OrderControllerTest {
//    @Autowired
//    lateinit var mvc: MockMvc
//
//    @MockitoBean
//    lateinit var orderService: OrderService
//
//    @Autowired
//    lateinit var om: ObjectMapper
//
//    @Test
//    fun `주문 생성에 성공한다`() {
//        //given
//        val userId = 1L
//        val items = getItems()
//        val method = "카드"
//        val request = CreateOrderRequest(userId, method, items)
//
//        val json = om.writeValueAsString(request)
//
//        given(orderService.createOrder(request)).willReturn(OrderCreateResponse(1L))
//
//        //when & then
//        mvc.post("/api/orders") {
//            contentType = MediaType.APPLICATION_JSON
//            content = json
//        }
//            .andExpect { status { isCreated() }}
//            .andExpect { jsonPath("$.orderId") { value(1L) } }
//            .andDo { println() }
//    }
//
//    @Test
//    fun `주문에 실패할 수 있다`() {
//        //given
//        val userId = 1L
//        val items = getItems()
//        val method = "카드"
//        val request = CreateOrderRequest(userId, method, items)
//
//        val json = om.writeValueAsString(request)
//
//        given(orderService.createOrder(request)).willThrow(IllegalStateException("재고 부족: ${items[0].productId}"))
//
//        //when & then
//        mvc.post("/api/orders") {
//            contentType = MediaType.APPLICATION_JSON
//            content = json
//        }
//            .andExpect { status { is5xxServerError() }}
//            .andExpect { jsonPath("$.message") { value("재고 부족: ${items[0].productId}") } }
//            .andDo { println() }
//
//
//    }
//
//
//    private fun getItems(): List<CreateOrderItemRequest> {
//        return listOf(
//            CreateOrderItemRequest(1L, "로얄캐닌 고양이 사료", 30000L, 1),
//            CreateOrderItemRequest(2L, "로얄캐닌 고양이 사료 키튼", 35000L, 1),
//            CreateOrderItemRequest(3L, "로얄캐닌 고양이 사료 인도어", 40000L, 5),
//            CreateOrderItemRequest(4L, "챠오츄르 마구로", 55000L, 7),
//            CreateOrderItemRequest(5L, "챠오츄르 이카", 30000L, 10),
//        )
//    }
//}
