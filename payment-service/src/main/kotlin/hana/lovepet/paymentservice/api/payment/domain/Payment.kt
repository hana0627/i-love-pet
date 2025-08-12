package hana.lovepet.paymentservice.api.payment.domain

import hana.lovepet.paymentservice.api.payment.domain.constant.PaymentStatus
import hana.lovepet.paymentservice.common.clock.TimeProvider
import jakarta.persistence.*
import java.time.LocalDateTime

// ERD update
@Entity
@Table(name = "payments")
class Payment(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val orderId: Long,

    @Column(name = "payment_key", nullable = false, unique = true)
    var paymentKey: String,

    @Column(nullable = false)
    val amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING,

    @Column(name = "method", length = 30)
    var method: String? = null,

    @Column(name = "requested_at", nullable = false)
    val requestedAt: LocalDateTime,

    @Column(name = "approved_at")
    var approvedAt: LocalDateTime? = null, // 성공일시

    @Column(name = "failed_at")
    var failedAt: LocalDateTime? = null, // 실패일시

    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = null, // 취소일시

    @Column(nullable = true)
    var refundedAt: LocalDateTime? = null, // 환불일시

    @Column(name = "fail_reason", length = 200)
    var failReason: String? = null, // 취소사유

//    @Column(name = "pg_response", columnDefinition = "TEXT")
//    var pgResponse: String? = null,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var description: String? = null

    /**
     * 결제 성공
     */
    fun approve(timeProvider: TimeProvider, paymentKey: String) {
        if (this.status != PaymentStatus.PENDING) throw IllegalStateException("결제 승인 불가 상태입니다.")
        this.status = PaymentStatus.SUCCESS
        this.approvedAt = timeProvider.now()
        this.paymentKey = paymentKey
        this.updatedAt = timeProvider.now()
    }

    /**
     * 결제 실패
     */
    fun fail(timeProvider: TimeProvider, paymentKey: String?, failReason: String?) {
        if (this.status != PaymentStatus.PENDING) throw IllegalStateException("결제 실패 불가 상태입니다.")
        this.status = PaymentStatus.FAIL
        this.paymentKey = paymentKey ?: this.paymentKey
        this.failedAt = timeProvider.now()
        this.failReason = failReason
        this.updatedAt = timeProvider.now()
    }

    /**
     * 결제 취소
     */
    fun cancel(timeProvider: TimeProvider, description: String?) {
        if (status == PaymentStatus.CANCELED) {throw IllegalStateException("이미 취소된 요청입니다.")}
        else if (status != PaymentStatus.SUCCESS) {throw IllegalStateException("승인된 결제만 취소할 수 있습니다.")}
        this.status = PaymentStatus.CANCELED
        this.canceledAt = timeProvider.now()
        this.description = description
        this.updatedAt = timeProvider.now()
    }

    /**
     * 결제 환불
     */
    fun refund(timeProvider: TimeProvider, description: String?) {
        if (status != PaymentStatus.SUCCESS && status != PaymentStatus.CANCELED) throw IllegalStateException("환불 처리 불가 상태입니다.")
        this.status = PaymentStatus.REFUNDED
        this.refundedAt = timeProvider.now()
        this.description = description
        this.updatedAt = timeProvider.now()
    }

    companion object {
        fun fixture(
            userId: Long = 1L,
            orderId: Long = 1001L,
            paymentKey: String = "temp_pgid_UUID",
            amount: Long = 120000L,
            method: String = "카드",
            timeProvider: TimeProvider
        ): Payment {
            return Payment(
                userId = userId,
                orderId = orderId,
                paymentKey = paymentKey,
                amount = amount,
                status = PaymentStatus.PENDING,
                method = method,
                requestedAt = timeProvider.now(),
            )
        }
    }
}

