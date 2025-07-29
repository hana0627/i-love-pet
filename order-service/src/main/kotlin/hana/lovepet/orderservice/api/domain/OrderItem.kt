package hana.lovepet.orderservice.api.domain

import jakarta.persistence.*

@Entity
@Table(name = "order_items")
class OrderItem (
    @Column(nullable = false, name = "product_id")
    val productId: Long,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false)
    val price: Long,

    @Column(nullable = false)
    var orderId: Long? = null,
){
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

}
