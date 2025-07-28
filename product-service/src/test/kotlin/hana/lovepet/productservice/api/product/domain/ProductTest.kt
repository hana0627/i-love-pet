package hana.lovepet.productservice.api.product.domain

import hana.lovepet.productservice.common.clock.TimeProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ProductTest {

    @Mock
    lateinit var timeProvider: TimeProvider

    @BeforeEach
    fun setUp() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 26, 9, 0, 0))
    }

    @Test
    fun `상품생성에 성공한다`() {
        //given


        //when
        val product = Product.fixture(
            name = "로얄캐닌 고양이 사료",
            price = 35000L,
            stock = 1000,
            timeProvider = timeProvider
        )

        //then
        assertThat(product.name).isEqualTo("로얄캐닌 고양이 사료")
        assertThat(product.price).isEqualTo(35000L)
        assertThat(product.stock).isEqualTo(1000)
        assertThat(product.createdAt).isEqualTo(timeProvider.now())

    }

    @Test
    fun `재고감소가 이루어진다`() {
        //given
        val product = Product.fixture(timeProvider = timeProvider)
        val beforeStock = product.stock

        //when
        product.decreaseStock(1, timeProvider)

        //then
        assertThat(product.stock).isEqualTo(beforeStock - 1)
        assertThat(product.updatedAt).isEqualTo(timeProvider.now())

    }

    @Test
    fun `재고는 음수가 될 수 없다`() {
        //given
        val product = Product.fixture(stock = 10, timeProvider = timeProvider)

        //when
        val result = assertThrows<IllegalStateException> {product.decreaseStock(9999, timeProvider)}

        //then
        assertThat(result.message).isEqualTo("재고가 부족합니다.")

    }

    @Test
    fun `재고증가가 이루어진다`() {
        //given
        val product = Product.fixture(timeProvider = timeProvider)
        val beforeStock = product.stock

        //when
        product.increaseStock(1, timeProvider)

        //then
        assertThat(product.stock).isEqualTo(beforeStock + 1)
        assertThat(product.updatedAt).isEqualTo(timeProvider.now())


    }

}