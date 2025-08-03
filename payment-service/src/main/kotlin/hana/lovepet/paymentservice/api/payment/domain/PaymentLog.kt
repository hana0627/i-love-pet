package hana.lovepet.paymentservice.api.payment.domain

import hana.lovepet.paymentservice.api.payment.domain.constant.LogType
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "payment_logs",
    indexes = [Index(name = "idx_payment_id", columnList = "payment_id")]
)
class PaymentLog(
    @Column(name="payment_id", nullable = false)
    val paymentId: Long,

    @Column(name = "log_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val logType: LogType, // REQUEST, RESPONSE, ERROR 등

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    val message: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null


    companion object {
        fun request(paymentId: Long, requestJson: String) = PaymentLog(paymentId, LogType.REQUEST, requestJson)
        fun response(paymentId: Long, responseJson: String) = PaymentLog(paymentId, LogType.RESPONSE, responseJson)
        fun error(paymentId: Long, errorMessage: String) = PaymentLog(paymentId, LogType.ERROR, errorMessage)
    }

}

