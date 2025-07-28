package hana.lovepet.productservice.api.product.domain

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


    fun decreaseStock(amount: Int, timeProvider: TimeProvider) {
        if(stock < amount) {
            throw IllegalStateException("재고가 부족합니다.")
        }
        stock -= amount
        updatedAt = timeProvider.now()
    }

    fun increaseStock(amount: Int, timeProvider: TimeProvider) {
        stock += amount
        updatedAt = timeProvider.now()
    }


}
