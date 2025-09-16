package hana.lovepet.productservice.api.product.controller

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.productservice.api.product.controller.dto.request.ProductRegisterRequest
import hana.lovepet.productservice.api.product.controller.dto.response.ProductInformationResponse
import hana.lovepet.productservice.api.product.controller.dto.response.ProductRegisterResponse
import hana.lovepet.productservice.api.product.service.ProductService
import hana.lovepet.productservice.common.exception.RestControllerHandler
import jakarta.persistence.EntityNotFoundException
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(ProductController::class)
@Import(RestControllerHandler::class)
class ProductControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockitoBean
    lateinit var productService: ProductService

    @Autowired
    lateinit var om: ObjectMapper

    @Test
    fun `상품 등록에 성공한다`() {
        //given
        val request = ProductRegisterRequest.fixture()
        val json = om.writeValueAsString(request)

        given(productService.register(request)).willReturn(ProductRegisterResponse(1L))

        //when & then

        mvc.post("/api/products") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andExpect {
            status { isCreated() }
            jsonPath("$.productId") { value(1L) }
        }.andDo { print() }

    }

    @Test
    fun `상품 id로 상품을 조회한다`() {
        //given
        val productId = 1L
        val response = ProductInformationResponse.fixture()

        given(productService.getProductInformation(productId)).willReturn(response)

        //when & then
        mvc.get("/api/products/$productId", productId)
            .andExpect {
                status { isOk() }
                jsonPath("$.productName") { value(response.productName) }
                jsonPath("$.price") { value(response.price) }
                jsonPath("$.stock") { value(response.stock) }
            }.andDo { print() }
    }

    @Test
    fun `없는 상품을 조회하면 예외가 발생한다`() {
        //given
        val wrongProductId = 9999L

        given(productService.getProductInformation(wrongProductId)).willThrow(EntityNotFoundException("상품을 찾을 수 없습니다. [id = $wrongProductId]"))

        //when & then
        mvc.get("/api/products/$wrongProductId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.message", equalTo("상품을 찾을 수 없습니다. [id = $wrongProductId]"))
            }
    }

    @Test
    fun `상품목록을 조회한다`() {
        //given
        val response = listOf(
            ProductInformationResponse.fixture(),
            ProductInformationResponse.fixture(
                productName = "로얄캐닌 고양이 사료 키튼",
                price = 38000L,
                stock = 500,
            ),
            ProductInformationResponse.fixture(
                productName = "로얄캐닌 고양이 사료 인도어",
                price = 37000L,
                stock = 500,
            )
        )

        given(productService.getAllProducts()).willReturn(response)

        //when & then
        mvc.get("/api/products")
            .andExpect {
                status { isOk() }
                jsonPath("$.size()") { value(response.size) }
                jsonPath("$[0].productName") { value(response[0].productName) }
                jsonPath("$[1].price") { value(response[1].price) }
                jsonPath("$[2].stock") { value(response[2].stock) }
            }
    }


}


//    @Test
//    fun `상품id 리스트를 통해 여러상품 조회가 가능하다`() {
//        //given
//        val response = listOf(
//            ProductInformationResponse.fixture(),
//            ProductInformationResponse.fixture(
//                productName = "로얄캐닌 고양이 사료 키튼",
//                price = 38000L,
//                stock = 500,
//            ),
//            ProductInformationResponse.fixture(
//                productName = "로얄캐닌 고양이 사료 인도어",
//                price = 37000L,
//                stock = 500,
//            )
//        )
//
//        val ids: List<Long> = listOf(1L, 2L, 3L)
//        given(productService.getProductsInformation(ids)).willReturn(response)
//
//        //when & then
//        mvc.get("/api/products") {
//            param("ids", ids.joinToString(","))
//        }
//            .andExpect {
//                status { isOk() }
//                jsonPath("$[0].productName") { value(response[0].productName) }
//                jsonPath("$[1].price") { value(response[1].price) }
//                jsonPath("$[2].stock") { value(response[2].stock) }
//            }
//    }
//
//
//    @Test
//    fun `상품id 리스트를 통해 조회할 때, 없는 상품이 있다면 예외가 발생한다`() {
//        //given
//        val ids: List<Long> = listOf(1L, 2L, 3L)
//        given(productService.getProductsInformation(ids)).willThrow(EntityNotFoundException("다음 상품을 찾을 수 없습니다: ${ids[1]}"))
//
//        //when & then
//        mvc.get("/api/products") {
//            param("ids", ids.joinToString(","))
//        }
//            .andExpect {
//                status { isNotFound() }
//                jsonPath("$.message", equalTo("다음 상품을 찾을 수 없습니다: ${ids[1]}"))
//            }
//    }
//
//    @Test
//    fun `상품 재고감소 요청에 성공한다`() {
//        //given
//        val requests = listOf(
//            ProductStockDecreaseRequest(productId = 1L, quantity = 1),
//            ProductStockDecreaseRequest(productId = 2L, quantity = 2),
//            ProductStockDecreaseRequest(productId = 3L, quantity = 3),
//        )
//
//        val json = om.writeValueAsString(requests)
//
//        given(productService.decreaseStock(requests)).willReturn(ProductStockDecreaseResponse(true))
//
//
//        //when & then
//        mvc.patch("/api/products/decrease-stock") {
//            contentType = MediaType.APPLICATION_JSON
//            content = json
//        }
//            .andExpect {
//                status { isOk() }
//                jsonPath("$.isSuccess", equalTo(true))
//            }
//            .andDo { print() }
//    }
//    @Test
//    fun `상품 재고감소시 예외가 발생할 수 있다`() {
//        //given
//        val requests = listOf(
//            ProductStockDecreaseRequest(productId = 1L, quantity = 1),
//            ProductStockDecreaseRequest(productId = 2L, quantity = 2),
//            ProductStockDecreaseRequest(productId = 3L, quantity = 3),
//        )
//
//        val json = om.writeValueAsString(requests)
//
//        given(productService.decreaseStock(requests)).willThrow(EntityNotFoundException("상품 ${requests[1].productId} 없음"))
//
//
//        //when & then
//        mvc.patch("/api/products/decrease-stock") {
//            contentType = MediaType.APPLICATION_JSON
//            content = json
//        }
//            .andExpect {
//                status { isNotFound() }
//                jsonPath("$.message", equalTo("상품 ${requests[1].productId} 없음"))
//            }
//            .andDo { print() }
//    }

//    @Test
//    fun `상품 재고 조회에 성공한다`() {
//        //given
//        val productId = 1L
//        given(productService.getStock(productId)).willReturn(3000)
//
//        //when & then
//        mvc.get("/api/products/$productId/stock")
//            .andExpect {
//                status { isOk() }
//                content { 3000 }
//            }
//    }