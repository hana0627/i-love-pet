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
            content = om.writeValueAsString(request)
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
                jsonPath("$.name") { value(response.name) }
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
                name = "로얄캐닌 고양이 사료 키튼",
                price = 38000L,
                stock = 500,
            ),
            ProductInformationResponse.fixture(
                name = "로얄캐닌 고양이 사료 인도어",
                price = 37000L,
                stock = 500,
            )
        )

        given(productService.getAllProducts()).willReturn(response)

        //when & then
        mvc.get("/api/products")
            .andExpect {
                status { isOk() }
                jsonPath("$.size()") { value(response.size)}
                jsonPath("$[0].name") { value(response[0].name)}
                jsonPath("$[1].price") { value(response[1].price)}
                jsonPath("$[2].stock") { value(response[2].stock)}
            }
    }

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

}

