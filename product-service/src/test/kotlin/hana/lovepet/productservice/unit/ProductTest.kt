package hana.lovepet.productservice.unit

import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import hana.lovepet.productservice.api.product.domain.Product
import hana.lovepet.productservice.common.clock.TimeProvider
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ProductTest {

    @Mock
    lateinit var timeProvider: TimeProvider

    @BeforeEach
    fun setUp() {
        BDDMockito.given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))
    }

    @Test
    fun `상품생성에 성공한다`() {
        //given


        //when
        val product = Product.Companion.fixture(
            name = "로얄캐닌 고양이 사료",
            price = 35000L,
            stock = 1000,
            timeProvider = timeProvider
        )

        //then
        Assertions.assertThat(product.name).isEqualTo("로얄캐닌 고양이 사료")
        Assertions.assertThat(product.price).isEqualTo(35000L)
        Assertions.assertThat(product.stock).isEqualTo(1000)
        Assertions.assertThat(product.createdAt).isEqualTo(timeProvider.now())

    }

    @Test
    fun `재고감소가 이루어진다`() {
        //given
        val product = Product.Companion.fixture(timeProvider = timeProvider)
        val beforeStock = product.stock

        //when
        product.decreaseStock(1, timeProvider)

        //then
        Assertions.assertThat(product.stock).isEqualTo(beforeStock - 1)
        Assertions.assertThat(product.updatedAt).isEqualTo(timeProvider.now())

    }

    @Test
    fun `재고는 음수가 될 수 없다`() {
        //given
        val product = Product.Companion.fixture(stock = 10, timeProvider = timeProvider)
        product.id = 1L

        //when
        val result = assertThrows<ApplicationException> { product.decreaseStock(9999, timeProvider) }

        //then
        Assertions.assertThat(result.errorCode).isEqualTo(ErrorCode.NOT_ENOUGH_STOCK)
        Assertions.assertThat(result.message).isEqualTo(ErrorCode.NOT_ENOUGH_STOCK.message + "productId: ${product.id}")

    }

    @Test
    fun `재고증가가 이루어진다`() {
        //given
        val product = Product.Companion.fixture(timeProvider = timeProvider)
        val beforeStock = product.stock

        //when
        product.increaseStock(1, timeProvider)

        //then
        Assertions.assertThat(product.stock).isEqualTo(beforeStock + 1)
        Assertions.assertThat(product.updatedAt).isEqualTo(timeProvider.now())


    }

}