package hana.lovepet.orderservice.api.repository

import hana.lovepet.orderservice.api.domain.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {
}
