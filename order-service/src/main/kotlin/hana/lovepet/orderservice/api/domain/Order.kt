package hana.lovepet.orderservice.api.domain

import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.api.domain.constant.OrderStatus.*
import hana.lovepet.orderservice.common.clock.TimeProvider
import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "orders",
    indexes = [Index(name = "idx_order_no", columnList = "order_no", unique = true)]
)
class Order (
    @Column(nullable = false, name = "user_id")
    val userId: Long,

    @Column(nullable = false, name = "user_name")
    val userName: String,

    @Column(nullable = false, name = "order_no", unique = true, length = 32)
    val orderNo: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = CREATED,

    val paymentMethod: String,

    @Column(nullable = false, name = "created_at")
    val createdAt: LocalDateTime,
){
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var price: Long = 0

    @Column(name = "payment_id")
    var paymentId: Long? = null

    @Column(nullable = true, name = "updated_at")
    var updatedAt: LocalDateTime? = null

    var description: String? = null


    companion object{
        fun create(
            userId: Long,
            userName: String,
            orderNo: String,
            paymentMethod: String?,
            timeProvider: TimeProvider
        ): Order{
            return Order(
                userId = userId,
                userName = userName,
                orderNo = orderNo,
                status = CREATED,
                paymentMethod = paymentMethod?:"UNKOWN",
                createdAt = timeProvider.now(),
            )
        }


        fun fixture(
            userId: Long = 1L,
            userName: String = "박하나",
            orderNo: String = "2025080100000001",
            paymentMethod: String = "카드",
            timeProvider: TimeProvider
        ): Order{
            return Order(userId, userName, orderNo, CREATED, paymentMethod, timeProvider.now())
        }
    }


    fun updateStatus(status: OrderStatus, timeProvider: TimeProvider) {
        this.status = status
        this.updatedAt = timeProvider.now()
    }



    fun confirm(timeProvider: TimeProvider) {
        if(this.status != CREATED){
            throw ApplicationException(ErrorCode.ILLEGALSTATE,"CREADTED인 상품만 CONFIRM이 가능합니다.")
        } else {
            this.status = CONFIRMED
            this.updatedAt = timeProvider.now()
        }

    }

    fun fail(timeProvider: TimeProvider) {
        this.status = PAYMENT_FAILED
        this.updatedAt = timeProvider.now()
    }

    fun cancel(timeProvider: TimeProvider) {
        if(this.status == CANCELED) {
            throw ApplicationException(ErrorCode.ILLEGALSTATE, "이미 취소된 상품입니다.")
        }
        this.status = CANCELED
        this.updatedAt = timeProvider.now()
    }

    fun updateTotalPrice(totalPrice: Long) {
        this.price = totalPrice
    }

    fun mappedPaymentId(paymentId: Long) {
        this.paymentId = paymentId
    }

    fun updateDescription(description: String) {
        this.description = description
    }
}
