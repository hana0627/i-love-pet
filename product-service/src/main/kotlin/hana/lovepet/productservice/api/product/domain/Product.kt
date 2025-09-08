package hana.lovepet.productservice.api.product.domain

import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import hana.lovepet.productservice.common.clock.TimeProvider
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "products")
class Product (
    @Column(nullable = false, name = "name", length = 255)
    val name: String,

    @Column(nullable = false)
    var price: Long,

    @Column(nullable = false)
    var stock: Int,

    @Column(nullable = false, name = "created_at")
    val createdAt: LocalDateTime,
){

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = true, name = "updated_at")
    var updatedAt: LocalDateTime? = null



    companion object {
        fun fixture(
            name: String = "로얄캐닌 고양이 사료",
            price: Long = 35000L,
            stock: Int = 1000,
            timeProvider: TimeProvider
        ): Product {
            return Product(
                name = name,
                price = price,
                stock = stock,
                createdAt = timeProvider.now()
            )
        }
    }


    fun decreaseStock(quantity: Int, timeProvider: TimeProvider) {
        if(stock < quantity) {
            throw ApplicationException(ErrorCode.NOT_ENOUGH_STOCK, ErrorCode.NOT_ENOUGH_STOCK.message)
        }
        stock -= quantity
        updatedAt = timeProvider.now()
    }

    fun increaseStock(quantity: Int, timeProvider: TimeProvider) {
        stock += quantity
        updatedAt = timeProvider.now()
    }


}
