package hana.lovepet.paymentservice.api.payment.repository

import hana.lovepet.paymentservice.api.payment.domain.PaymentLog
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository


interface PaymentLogRepository : JpaRepository<PaymentLog, Long>{
    fun findAllByPaymentIdOrderByIdDesc(paymentId: Long, pageable: Pageable): List<PaymentLog>
}
