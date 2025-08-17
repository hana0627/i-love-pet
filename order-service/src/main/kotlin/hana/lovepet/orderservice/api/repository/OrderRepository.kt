package hana.lovepet.orderservice.api.repository

import hana.lovepet.orderservice.api.domain.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface OrderRepository : JpaRepository<Order, Long>, OrderCustomRepository {
    @Query("SELECT MAX(o.orderNo) FROM Order o WHERE o.orderNo LIKE :today")
    fun findMaxOrderNoByToday(today: String): String?
    fun findByOrderNo(orderNo: String): Order?
}
