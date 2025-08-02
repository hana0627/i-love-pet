package hana.lovepet.orderservice.api.domain

import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.common.clock.TimeProvider
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
class OrderTest {

    @Mock
    lateinit var timeProvider: TimeProvider

    @BeforeEach
    fun setUp() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025,7,21,9,0,0))
    }

    @Test
    fun `주문생성에 성공한다`() {
        //given
        val userId = 1L


        //when
        val order = Order.fixture(userId = userId, timeProvider = timeProvider)

        //then
        assertThat(order).isNotNull
        assertThat(order.userId).isEqualTo(userId)
        assertThat(order.orderNo).isEqualTo("2025080100000001")
        assertThat(order.status).isEqualTo(OrderStatus.CREATED)
        assertThat(order.createdAt).isEqualTo(timeProvider.now())
        assertThat(order.updatedAt).isNull()
    }

    @Test
    fun `주문확정에 성공한다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)

        //when
        order.confirm(timeProvider = timeProvider)

        //then
        assertThat(order.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(order.updatedAt).isNotNull()
    }

    @Test
    fun `CREATED아닌 상품은 주문확정이 불가능하다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)
        order.confirm(timeProvider = timeProvider)

        //when
        val result = assertThrows<IllegalStateException> {order.confirm(timeProvider = timeProvider)}


        //then
        assertThat(result.message).isEqualTo("CREADTED인 상품만 CONFIRM이 가능합니다.")

    }

    @Test
    fun `주문취소가 가능하다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)

        //when
        order.cancel(timeProvider = timeProvider)

        //then
        assertThat(order.status).isEqualTo(OrderStatus.CANCELED)
        assertThat(order.updatedAt).isEqualTo(timeProvider.now())
    }

    @Test
    fun `이미 주문취소된 경우, 주문취소가 불가능하다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)
        order.cancel(timeProvider = timeProvider)

        //when
        val result = assertThrows<IllegalStateException> {order.cancel(timeProvider = timeProvider)}

        //then
        assertThat(result.message).isEqualTo("이미 취소된 상품입니다.")
    }
}

