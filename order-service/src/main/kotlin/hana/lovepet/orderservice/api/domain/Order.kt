package hana.lovepet.orderservice.api.domain

import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.api.domain.constant.OrderStatus.*
import hana.lovepet.orderservice.common.clock.TimeProvider
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "orders",
    indexes = [Index(name = "idx_order_no", columnList = "order_no", unique = true)]
)
//TODO ERD update
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


    companion object{
        fun create(
            userId: Long,
            userName: String,
            orderNo: String,
            timeProvider: TimeProvider
        ): Order{
            return Order(
                userId = userId,
                userName = userName,
                orderNo = orderNo,
                status = CREATED,
                createdAt = timeProvider.now(),
            )
        }


        fun fixture(
            userId: Long = 1L,
            userName: String = "박하나",
            orderNo: String = "2025080100000001",
            timeProvider: TimeProvider
        ): Order{
            return Order(userId, userName, orderNo, CREATED, timeProvider.now())
        }
    }

    fun confirm(timeProvider: TimeProvider) {
        if(this.status != CREATED){
            throw IllegalStateException("CREADTED인 상품만 CONFIRM이 가능합니다.")
        } else {
            this.status = CONFIRMED
            this.updatedAt = timeProvider.now()
        }

    }

    fun fail(timeProvider: TimeProvider) {
        if(this.status != CREATED){
            throw IllegalStateException("CREATED인 상품만 FAIL이 가능합니다.")
        }
        this.status = FAIL
        this.updatedAt = timeProvider.now()
    }

    fun cancel(timeProvider: TimeProvider) {
        if(this.status == CANCELED) {
            throw IllegalStateException("이미 취소된 상품입니다.")
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

}
