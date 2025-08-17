package hana.lovepet.productservice.api.product.service

import hana.lovepet.productservice.api.product.controller.dto.request.ProductRegisterRequest
import hana.lovepet.productservice.api.product.controller.dto.request.ProductStockDecreaseRequest
import hana.lovepet.productservice.api.product.controller.dto.response.ProductInformationResponse
import hana.lovepet.productservice.api.product.domain.Product
import hana.lovepet.productservice.api.product.repository.ProductRepository
import hana.lovepet.productservice.api.product.service.impl.ProductServiceImpl
import hana.lovepet.productservice.common.clock.TimeProvider
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class ProductServiceTest {

    @Mock
    lateinit var productRepository: ProductRepository

    @Mock
    lateinit var timeProvider: TimeProvider

    lateinit var productService: ProductService

    @BeforeEach
    fun setUp() {
        productService = ProductServiceImpl(productRepository, timeProvider)
    }

    @Test
    fun `상품이 등록되고 productId가 반환된다`() {
        // given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val productRegisterRequest = ProductRegisterRequest.fixture()
        val product = Product.fixture(timeProvider = timeProvider).apply { id = 1L }

        given(productRepository.save(any())).willReturn(product)

        // when
        val result = productService.register(productRegisterRequest)

        // then
        then(productRepository).should().save(any())

        assertThat(result.productId).isEqualTo(product.id)
    }

    @Test
    fun `상품조회에 성공한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))
        val productId = 1L
        val product = Product.fixture(timeProvider = timeProvider).apply { id = productId }

        given(productRepository.findById(productId)).willReturn(Optional.of(product))

        //when
        val result = productService.getProductInformation(productId)

        //then
        then(productRepository).should().findById(productId)

        assertThat(result.productName).isEqualTo(product.name)
        assertThat(result.price).isEqualTo(product.price)
        assertThat(result.stock).isEqualTo(product.stock)
    }

    @Test
    fun `없는id로 상품 조회시 예외가 발생한다`() {
        //given
        val productId = 9999L
        given(productRepository.findById(productId)).willReturn(Optional.empty())

        //when
        val result = assertThrows<EntityNotFoundException> { productService.getProductInformation(productId) }

        //then
        then(productRepository).should().findById(productId)

        assertThat(result.message).isEqualTo("상품을 찾을 수 없습니다. [id = $productId]")
    }

    @Test
    fun `모든상품을_조회한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))
        val product1 = Product.fixture(timeProvider = timeProvider).apply { id = 1L }
        val product2 = Product.fixture(
            name = "로얄캐닌 고양이 사료 키튼",
            price = 38000L,
            stock = 500,
            timeProvider = timeProvider
        ).apply { id = 2L }
        val product3 = Product.fixture(
            name = "로얄캐닌 고양이 사료 인도어",
            price = 37000L,
            stock = 500,
            timeProvider = timeProvider
        ).apply { id = 3L }

        val products = listOf(product1, product2, product3)
        given(productRepository.findAll()).willReturn(products)

        val responses = products.map {
            ProductInformationResponse(
                productId = it.id!!,
                productName = it.name,
                price = it.price,
                stock = it.stock,
            )
        }


        //when
        val result = productService.getAllProducts()

        //then
        then(productRepository).should().findAll()

        assertThat(result).isEqualTo(responses)
    }

    @Test
    fun `상품id리스트를 통해 여러 상품조회가 가능하다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val ids: List<Long> = listOf(1L, 2L, 3L)
        val product1 = Product.fixture(timeProvider = timeProvider).apply { id = 1L }
        val product2 = Product.fixture(
            name = "로얄캐닌 고양이 사료 키튼",
            price = 38000L,
            stock = 500,
            timeProvider = timeProvider
        ).apply { id = 2L }
        val product3 = Product.fixture(
            name = "로얄캐닌 고양이 사료 인도어",
            price = 37000L,
            stock = 500,
            timeProvider = timeProvider
        ).apply { id = 3L }

        val products: List<Product> = listOf(product1, product2, product3)


        given(productRepository.findAllById(ids)).willReturn(products)

        //when
        val result = productService.getProductsInformation(ids)

        //then
        assertThat(result.size).isEqualTo(products.size)
        assertThat(result[0].productName).isEqualTo(product1.name)
        assertThat(result[1].price).isEqualTo(product2.price)
        assertThat(result[2].stock).isEqualTo(product3.stock)

    }

    @Test
    fun `상품id리스트로 조회시  없는 상품이 있다면 예외가 발생한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))
        val ids: List<Long> = listOf(1L, 2L, 3L)

        val product1 = Product.fixture(timeProvider = timeProvider).apply { id = 1L }
        val product2 = Product.fixture(
            name = "로얄캐닌 고양이 사료 키튼",
            price = 38000L,
            stock = 500,
            timeProvider = timeProvider
        ).apply { id = 2L }

        val products: List<Product> = listOf(product1, product2)

        given(productRepository.findAllById(ids)).willReturn(products)

        //when
        val result = assertThrows<EntityNotFoundException> { productService.getProductsInformation(ids) }

        //then
        assertThat(result.message).isEqualTo("다음 상품을 찾을 수 없습니다: ${listOf(ids[2])}")

    }

    @Test
    fun `재고감소에 성공한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val beforeStock1 = 100
        val beforeStock2 = 100
        val beforeStock3 = 100

        val requests = listOf(
            ProductStockDecreaseRequest(productId = 1L, quantity = 1),
            ProductStockDecreaseRequest(productId = 2L, quantity = 2),
            ProductStockDecreaseRequest(productId = 3L, quantity = 3),
        )

        val products = listOf(
            Product.fixture(stock = beforeStock1, timeProvider = timeProvider).apply { id = 1L },
            Product.fixture(
                name = "로얄캐닌 고양이 사료 키튼",
                price = 38000L,
                stock = beforeStock2,
                timeProvider = timeProvider
            ).apply { id = 2L },
            Product.fixture(
                name = "로얄캐닌 고양이 사료 인도어",
                price = 38000L,
                stock = beforeStock3,
                timeProvider = timeProvider
            ).apply { id = 3L },
        )

        given(productRepository.findAllById(requests.map{it.productId})).willReturn(products)

        //when
        productService.decreaseStock(requests)

        //then
        then(productRepository).should().findAllById(requests.map{it.productId})
        then(productRepository).should().saveAll(products)

        assertThat(beforeStock1).isEqualTo(products[0].stock + requests[0].quantity)
        assertThat(beforeStock1).isEqualTo(products[1].stock + requests[1].quantity)
        assertThat(beforeStock1).isEqualTo(products[2].stock + requests[2].quantity)
    }



    @Test
    fun `없는상품에 대한 재고감소시 예외가 발생한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val requests = listOf(
            ProductStockDecreaseRequest(productId = 1L, quantity = 1),
            ProductStockDecreaseRequest(productId = 2L, quantity = 2),
        )

        val products = listOf(
            Product.fixture(timeProvider = timeProvider).apply { id = 1L },
        )

        given(productRepository.findAllById(requests.map{it.productId})).willReturn(products)

        //when
        val result = assertThrows<EntityNotFoundException> {productService.decreaseStock(requests)}

        //then
        then(productRepository).should().findAllById(requests.map{it.productId})

        assertThat(result.message).isEqualTo("상품 ${requests[1].productId} 없음")

    }

//    @Test
//    fun `재고조회에 성공한다`() {
//        //given
//        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))
//        val productId = 1L
//        val product = Product.fixture(timeProvider = timeProvider)
//
//        given(productRepository.findById(productId)).willReturn(Optional.of(product))
//
//        //when
//        val result = productService.getStock(productId)
//
//        //then
//        then(productRepository).should().findById(productId)
//
//        assertThat(result).isEqualTo(product.stock)
//    }
//
//    @Test
//    fun `없는id로 재고조회시 예외가 발생한다`() {
//        //given
//        val productId = 9999L
//        given(productRepository.findById(productId)).willReturn(Optional.empty())
//
//        //when
//        val result = assertThrows<EntityNotFoundException> {productService.getStock(productId)}
//
//        //then
//        then(productRepository).should().findById(productId)
//
//        assertThat(result.message).isEqualTo("상품을 찾을 수 없습니다. [id = $productId]")
//    }

}
