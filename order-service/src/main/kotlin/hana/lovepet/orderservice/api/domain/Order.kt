package hana.lovepet.orderservice.api.domain

import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.api.domain.constant.OrderStatus.*
import hana.lovepet.orderservice.common.clock.TimeProvider
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class Order (
    @Column(nullable = false, name = "user_id")
    val userId: Long,

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

    @Column(nullable = true, name = "created_at")
    var updatedAt: LocalDateTime? = null


    companion object{
        fun create(
            userId: Long,
            timeProvider: TimeProvider
        ): Order{
            return Order(
                userId = userId,
                status = CREATED,
                createdAt = timeProvider.now(),
            )
        }


        fun fixture(
            userId: Long = 1L,
            timeProvider: TimeProvider
        ): Order{
            return Order(userId, CREATED, timeProvider.now())
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
}
