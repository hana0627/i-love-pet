package hana.lovepet.orderservice.api.repository

import hana.lovepet.orderservice.api.domain.OrderItem
import org.springframework.data.jpa.repository.JpaRepository

interface OrderItemRepository : JpaRepository<OrderItem, Long> {
}
