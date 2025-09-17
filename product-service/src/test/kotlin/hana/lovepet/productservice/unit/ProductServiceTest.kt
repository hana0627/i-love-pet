package hana.lovepet.productservice.api.product.service

import hana.lovepet.productservice.common.exception.ApplicationException
import hana.lovepet.productservice.common.exception.constant.ErrorCode
import hana.lovepet.productservice.api.product.controller.dto.request.ProductRegisterRequest
import hana.lovepet.productservice.api.product.controller.dto.response.ProductInformationResponse
import hana.lovepet.productservice.api.product.domain.Product
import hana.lovepet.productservice.api.product.repository.ProductCacheRepository
import hana.lovepet.productservice.api.product.repository.ProductRepository
import hana.lovepet.productservice.api.product.service.impl.ProductServiceImpl
import hana.lovepet.productservice.common.clock.TimeProvider
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.GetProductsEvent.OrderItemRequest
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.ProductStockDecreaseEvent
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.ProductStockRollbackEvent
import hana.lovepet.productservice.infrastructure.kafka.out.ProductEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class ProductServiceTest {

    @Mock
    lateinit var productRepository: ProductRepository

    @Mock
    lateinit var timeProvider: TimeProvider

    lateinit var productService: ProductService

    @Mock
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Mock
    lateinit var productEventPublisher: ProductEventPublisher

    @Mock
    lateinit var productCacheRepository: ProductCacheRepository

    @BeforeEach
    fun setUp() {
        productService = ProductServiceImpl(
            productRepository = productRepository,
            timeProvider = timeProvider,
            applicationEventPublisher = applicationEventPublisher,
            productEventPublisher = productEventPublisher,
            productCacheRepository = productCacheRepository,
        )
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
        val result = assertThrows<ApplicationException> { productService.getProductInformation(productId) }

        //then
        then(productRepository).should().findById(productId)

        assertThat(result.errorCode).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND)
        assertThat(result.message).isEqualTo("다음 상품을 찾을 수 없습니다: $productId")
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

        val orderId = 1L

        val ids: List<Long> = listOf(1L, 2L, 3L)
        val product1 = Product.fixture(timeProvider = timeProvider).apply { id = ids[0] }
        val product2 = Product.fixture(
            name = "로얄캐닌 고양이 사료 키튼",
            price = 38000L,
            stock = 500,
            timeProvider = timeProvider
        ).apply { id = ids[1] }
        val product3 = Product.fixture(
            name = "로얄캐닌 고양이 사료 인도어",
            price = 37000L,
            stock = 500,
            timeProvider = timeProvider
        ).apply { id = ids[2] }


        val products: List<Product> = listOf(product1, product2, product3)

        val orderItems = listOf<OrderItemRequest>(
            OrderItemRequest(ids[0], 1),
            OrderItemRequest(ids[1], 1),
            OrderItemRequest(ids[2], 3)
        )

        given(productRepository.findAllById(ids)).willReturn(products)

        //when
        productService.getProductsInformation(orderId, orderItems)

        //then
        then(productRepository).should().findAllById(ids)
        then(productEventPublisher).should().publishProductsInformation(any())
    }

    @Test
    fun `상품id리스트로 조회시 없는 상품이 있다면 예외가 발생한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val orderId = 1L

        val ids: List<Long> = listOf(1L, 2L, 3L)

        val product1 = Product.fixture(timeProvider = timeProvider).apply { id = ids[0] }
        val product2 = Product.fixture(
            name = "로얄캐닌 고양이 사료 키튼",
            price = 38000L,
            stock = 500,
            timeProvider = timeProvider
        ).apply { id = ids[1] }

        val products: List<Product> = listOf(product1, product2)

        val orderItems = listOf<OrderItemRequest>(
            OrderItemRequest(ids[0], 1),
            OrderItemRequest(ids[1], 1),
            OrderItemRequest(ids[2], 3)
        )

        given(productRepository.findAllById(ids)).willReturn(products)

        //when
        val result = assertThrows<ApplicationException> { productService.getProductsInformation(orderId, orderItems) }

        //then
        assertThat(result.errorCode).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND)
        assertThat(result.message).isEqualTo("다음 상품을 찾을 수 없습니다: ${listOf(ids[2])}")
        then(productEventPublisher).shouldHaveNoInteractions()
    }



    @Test
    fun `예외상황 테스트 - "없는상품"이라는 이름이 있는 상품 조회시 예외가 발생한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val orderId = 1L

        val ids: List<Long> = listOf(1L, 2L, 3L)

        val product1 = Product.fixture(timeProvider = timeProvider).apply { id = ids[0] }
        val product2 = Product.fixture(
            name = "없는상품",
            price = 38000L,
            stock = 500,
            timeProvider = timeProvider
        ).apply { id = ids[1] }
        val product3 = Product.fixture(
            name = "로얄캐닌 고양이 사료 인도어",
            price = 37000L,
            stock = 500,
            timeProvider = timeProvider
        ).apply { id = ids[2] }

        val products: List<Product> = listOf(product1, product2, product3)

        val orderItems = listOf<OrderItemRequest>(
            OrderItemRequest(ids[0], 1),
            OrderItemRequest(ids[1], 1),
            OrderItemRequest(ids[2], 3)
        )

        given(productRepository.findAllById(ids)).willReturn(products)

        //when
        val result = assertThrows<ApplicationException> { productService.getProductsInformation(orderId, orderItems) }

        //then
        assertThat(result.errorCode).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND)
        assertThat(result.message).isEqualTo("다음 상품을 찾을 수 없습니다: ${ids[1]}")
        then(productEventPublisher).shouldHaveNoInteractions()
    }


    @Test
    fun `재고감소에 성공한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val beforeStock1 = 100
        val beforeStock2 = 100
        val beforeStock3 = 100

        val orderId = 1L

        val requests = listOf(
            ProductStockDecreaseEvent.Product(productId = 1L, quantity = 1),
            ProductStockDecreaseEvent.Product(productId = 2L, quantity = 2),
            ProductStockDecreaseEvent.Product(productId = 3L, quantity = 3),
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

        given(productRepository.findAllByIdWithLock(requests.map{it.productId})).willReturn(products)
        given(productCacheRepository.getDecreased(orderId)).willReturn(false)

        //when
        productService.decreaseStock(orderId, requests)

        //then
        then(productRepository).should().findAllByIdWithLock(requests.map{it.productId})
        then(productRepository).should().saveAll(products)
        then(productCacheRepository).should().getDecreased(orderId)
        then(productCacheRepository).should().setDecreased(orderId)

        assertThat(beforeStock1).isEqualTo(products[0].stock + requests[0].quantity)
        assertThat(beforeStock1).isEqualTo(products[1].stock + requests[1].quantity)
        assertThat(beforeStock1).isEqualTo(products[2].stock + requests[2].quantity)
    }



    @Test
    fun `없는상품에 대한 재고감소시 예외가 발생한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val orderId =1L
        val requests = listOf(
            ProductStockDecreaseEvent.Product(productId = 1L, quantity = 1),
            ProductStockDecreaseEvent.Product(productId = 2L, quantity = 2),
        )

        val products = listOf(
            Product.fixture(timeProvider = timeProvider).apply { id = 1L },
        )

        given(productCacheRepository.getDecreased(orderId)).willReturn(false)
        given(productRepository.findAllByIdWithLock(requests.map{it.productId})).willReturn(products)

        //when
        val result = assertThrows<ApplicationException> {productService.decreaseStock(orderId, requests)}

        //then
        then(productRepository).should().findAllByIdWithLock(requests.map{it.productId})
        then(productCacheRepository).should().getDecreased(orderId)

        assertThat(result.errorCode).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND)

        assertThat(result.message).isEqualTo("다음 상품을 찾을 수 없습니다: ${requests[1].productId}")
    }



    @Test
    fun `멱등처리 - 이미 재고 차감된 상품에 대해서는 재고차감이 진행되지 않는다`() {
        //given
//        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val orderId =1L
        val requests = listOf(
            ProductStockDecreaseEvent.Product(productId = 1L, quantity = 1),
            ProductStockDecreaseEvent.Product(productId = 2L, quantity = 2),
        )

        given(productCacheRepository.getDecreased(orderId)).willReturn(true)

        //when
        productService.decreaseStock(orderId, requests)

        //then
        then(productRepository).shouldHaveNoInteractions()
        then(productCacheRepository).should().getDecreased(orderId)

    }


    @Test
    fun `예외상황 테스트 - "재고부족"이란 이름이 들어간 상품 재고감소시 예외가 발생한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val orderId =1L
        val requests = listOf(
            ProductStockDecreaseEvent.Product(productId = 1L, quantity = 1),
            ProductStockDecreaseEvent.Product(productId = 2L, quantity = 2),
        )

        val products = listOf(
            Product.fixture(timeProvider = timeProvider, name = "재고부족").apply { id = 1L },
        )

        given(productCacheRepository.getDecreased(orderId)).willReturn(false)
        given(productRepository.findAllByIdWithLock(requests.map{it.productId})).willReturn(products)

        //when
        val result = assertThrows<ApplicationException> {productService.decreaseStock(orderId, requests)}

        //then
        then(productRepository).should().findAllByIdWithLock(requests.map{it.productId})
        then(productCacheRepository).should().getDecreased(orderId)

        assertThat(result.errorCode).isEqualTo(ErrorCode.NOT_ENOUGH_STOCK)
        assertThat(result.message).isEqualTo(ErrorCode.NOT_ENOUGH_STOCK.message+"productId: ${products[0].id}")
    }


    @Test
    fun `주문량이 재고보다 많을 경우 예외가 발생한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val orderId =1L
        val requests = listOf(
            ProductStockDecreaseEvent.Product(productId = 1L, quantity = 200),
        )

        val products = listOf(
            Product.fixture(timeProvider = timeProvider, stock = 100).apply { id = 1L },
        )

        given(productCacheRepository.getDecreased(orderId)).willReturn(false)
        given(productRepository.findAllByIdWithLock(requests.map{it.productId})).willReturn(products)

        //when
        val result = assertThrows<ApplicationException> {productService.decreaseStock(orderId, requests)}

        //then
        then(productRepository).should().findAllByIdWithLock(requests.map{it.productId})
        then(productCacheRepository).should().getDecreased(orderId)

        assertThat(result.errorCode).isEqualTo(ErrorCode.NOT_ENOUGH_STOCK)
        assertThat(result.message).isEqualTo(ErrorCode.NOT_ENOUGH_STOCK.message+"productId: ${products[0].id}")
    }


    @Test
    fun `재고롤백에 성공한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val beforeStock1 = 100
        val beforeStock2 = 100
        val beforeStock3 = 100

        val orderId = 1L

        val requests = listOf(
            ProductStockRollbackEvent.Product(productId = 1L, quantity = 1),
            ProductStockRollbackEvent.Product(productId = 2L, quantity = 2),
            ProductStockRollbackEvent.Product(productId = 3L, quantity = 3),
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

        given(productRepository.findAllByIdWithLock(requests.map{it.productId})).willReturn(products)
        given(productCacheRepository.getRollbacked(orderId)).willReturn(false)

        //when
        productService.rollbackStock(orderId, requests)

        //then
        then(productRepository).should().findAllByIdWithLock(requests.map{it.productId})
        then(productRepository).should().saveAll(products)
        then(productCacheRepository).should().getRollbacked(orderId)
        then(productCacheRepository).should().setRollbacked(orderId)

        assertThat(beforeStock1 + requests[0].quantity).isEqualTo(products[0].stock)
        assertThat(beforeStock2 + requests[1].quantity).isEqualTo(products[1].stock)
        assertThat(beforeStock3 + requests[2].quantity).isEqualTo(products[2].stock)
    }

    @Test
    fun `없는상품에 대한 재고롤백시 예외가 발생한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val orderId = 1L
        val requests = listOf(
            ProductStockRollbackEvent.Product(productId = 1L, quantity = 1),
            ProductStockRollbackEvent.Product(productId = 2L, quantity = 2),
        )

        val products = listOf(
            Product.fixture(timeProvider = timeProvider).apply { id = 1L },
        )

        given(productCacheRepository.getRollbacked(orderId)).willReturn(false)
        given(productRepository.findAllByIdWithLock(requests.map{it.productId})).willReturn(products)

        //when
        val result = assertThrows<ApplicationException> {productService.rollbackStock(orderId, requests)}

        //then
        then(productRepository).should().findAllByIdWithLock(requests.map{it.productId})
        then(productCacheRepository).should().getRollbacked(orderId)

        assertThat(result.errorCode).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND)
        assertThat(result.message).isEqualTo("다음 상품을 찾을 수 없습니다: ${requests[1].productId}")
    }

    @Test
    fun `멱등처리 - 이미 재고 롤백된 주문에 대해서는 재고롤백이 진행되지 않는다`() {
        //given
//        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))

        val orderId = 1L
        val requests = listOf(
            ProductStockRollbackEvent.Product(productId = 1L, quantity = 1),
            ProductStockRollbackEvent.Product(productId = 2L, quantity = 2),
        )

        given(productCacheRepository.getRollbacked(orderId)).willReturn(true)

        //when
        productService.rollbackStock(orderId, requests)

        //then
        then(productRepository).shouldHaveNoInteractions()
        then(productCacheRepository).should().getRollbacked(orderId)
    }



}
