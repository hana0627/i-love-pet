package hana.lovepet.orderservice.unit

import hana.lovepet.orderservice.api.domain.Order
import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.common.clock.TimeProvider
import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode
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

    val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

    @BeforeEach
    fun setUp() {
        given(timeProvider.now()).willReturn(currentTime)
    }

    @Test
    fun `주문 생성에 성공한다`() {
        //given
        val userId = 1000L
        val userName = "박하나"
        val orderNo = "2025080300000001"
        val paymentMethod = "카드"

        //when
        val order = Order.create(userId, userName, orderNo, paymentMethod, timeProvider)

        //then
        assertThat(order).isNotNull
        assertThat(order.userId).isEqualTo(userId)
        assertThat(order.userName).isEqualTo(userName)
        assertThat(order.orderNo).isEqualTo(orderNo)
        assertThat(order.paymentMethod).isEqualTo(paymentMethod)
        assertThat(order.status).isEqualTo(OrderStatus.CREATED)
        assertThat(order.createdAt).isEqualTo(currentTime)
        assertThat(order.updatedAt).isNull()
        assertThat(order.price).isEqualTo(0L)
        assertThat(order.paymentId).isNull()
        assertThat(order.description).isNull()
    }

    @Test
    fun `주문 생성시 paymentMethod가 null이면 UNKOWN으로 설정된다`() {
        //given
        val userId = 1000L
        val userName = "박하나"
        val orderNo = "2025080300000001"

        //when
        val order = Order.create(userId, userName, orderNo, null, timeProvider)

        //then
        assertThat(order.paymentMethod).isEqualTo("UNKOWN")
    }

    @Test
    fun `주문 상태 업데이트에 성공한다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)
        val newStatus = OrderStatus.VALIDATING

        //when
        order.updateStatus(newStatus, timeProvider)

        //then
        assertThat(order.status).isEqualTo(newStatus)
        assertThat(order.updatedAt).isEqualTo(currentTime)
    }

    @Test
    fun `주문 확정에 성공한다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)

        //when
        order.confirm(timeProvider)

        //then
        assertThat(order.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(order.updatedAt).isEqualTo(currentTime)
    }

    @Test
    fun `CREATED가 아닌 상태에서는 주문 확정이 불가능하다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)
        order.updateStatus(OrderStatus.VALIDATING, timeProvider)

        //when & then
        val result = assertThrows<ApplicationException> {
            order.confirm(timeProvider)
        }

        assertThat(result.errorCode).isEqualTo(ErrorCode.ILLEGALSTATE)
        assertThat(result.message).isEqualTo("CREADTED인 상품만 CONFIRM이 가능합니다.")
    }

    @Test
    fun `주문 실패 처리에 성공한다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)

        //when
        order.fail(timeProvider)

        //then
        assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        assertThat(order.updatedAt).isEqualTo(currentTime)
    }

    @Test
    fun `주문 취소에 성공한다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)

        //when
        order.cancel(timeProvider)

        //then
        assertThat(order.status).isEqualTo(OrderStatus.CANCELED)
        assertThat(order.updatedAt).isEqualTo(currentTime)
    }

    @Test
    fun `이미 취소된 주문은 다시 취소할 수 없다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)
        order.cancel(timeProvider)

        //when & then
        val result = assertThrows<ApplicationException> {
            order.cancel(timeProvider)
        }

        assertThat(result.errorCode).isEqualTo(ErrorCode.ILLEGALSTATE)
        assertThat(result.message).isEqualTo("이미 취소된 상품입니다.")
    }

    @Test
    fun `총 가격 업데이트에 성공한다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)
        val totalPrice = 150000L

        //when
        order.updateTotalPrice(totalPrice)

        //then
        assertThat(order.price).isEqualTo(totalPrice)
    }

    @Test
    fun `paymentId 매핑에 성공한다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)
        val paymentId = 2000L

        //when
        order.mappedPaymentId(paymentId)

        //then
        assertThat(order.paymentId).isEqualTo(paymentId)
    }

    @Test
    fun `설명 업데이트에 성공한다`() {
        //given
        val order = Order.fixture(timeProvider = timeProvider)
        val description = "결제 실패 - 카드 한도 초과"

        //when
        order.updateDescription(description)

        //then
        assertThat(order.description).isEqualTo(description)
    }
}

