package hana.lovepet.paymentservice.infrastructure.webclient.payment.impl

import hana.lovepet.paymentservice.common.clock.TimeProvider
import hana.lovepet.paymentservice.common.exception.PgCommunicationException
import hana.lovepet.paymentservice.common.uuid.UUIDGenerator
import hana.lovepet.paymentservice.infrastructure.webclient.payment.PgClient
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.PgApproveRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgApproveResponse
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgCancelResponse
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class FakePgClient(
    private val uuidGenerator: UUIDGenerator,
    private val timeProvider: TimeProvider,
) : PgClient {
    override fun approve(request: PgApproveRequest): PgApproveResponse {
        // 10% 확률로 통신실패
        if (Random.nextInt(10) == 0) {
            throw PgCommunicationException("PG사의 응답이 없습니다.")
        }


        // 30% 확률로 결제실패
        return if (Random.nextInt(3) == 0) {
            PgApproveResponse.fail(
                paymentKey = "fail_${uuidGenerator.generate()}",
                code = "LIMIT_OVER",
                message = "한도초과"
            )
        } else {
            PgApproveResponse.success(
                paymentKey = "success_${uuidGenerator.generate()}",
                amount = request.amount,
                method = request.method ?: "카드",
            )
        }

    }

    override fun cancel(paymentKey: String, cancelReason: String): PgCancelResponse {
        // 10% 확률로 통신실패
        if (Random.nextInt(10) == 0) {
            throw PgCommunicationException("PG사의 응답이 없습니다.")
        }

        // 10% 확률로 중복 취소요청
        return if (Random.nextInt(10) == 0) {
            PgCancelResponse.fail(
                paymentKey = paymentKey,
                code = "ALREADY_CANCELED_PAYMENT",
                message = "이미 취소된 결제 입니다."
            )
        } else {
            PgCancelResponse.success(
                paymentKey = paymentKey,
                transactionKey = "cancel_${uuidGenerator.generate()}",
                cancelAt = timeProvider.now()
            )
        }
    }
}
