package hana.lovepet.paymentservice.api.payment.repository

import hana.lovepet.paymentservice.api.payment.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository


interface PaymentRepository : JpaRepository<Payment, Long>{
    fun findByOrderId(orderId : Long): Payment?
}
