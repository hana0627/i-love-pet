package hana.lovepet.paymentservice.infrastructure.webclient.payment.impl

import hana.lovepet.paymentservice.common.uuid.UUIDGenerator
import hana.lovepet.paymentservice.infrastructure.webclient.payment.PgClient
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.PgApproveRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgApproveResponse
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class FakePgClient(
    private val uuidGenerator: UUIDGenerator,
): PgClient {
    override fun approve(request: PgApproveRequest): PgApproveResponse {

        // 30% 확률로 결제실패
        return if (Random.nextInt(3) == 0) {
            PgApproveResponse.fail(
                paymentKey = "fail_${uuidGenerator.generate()}",
                failReason = "한도초과",
                rawJson = """{"code":"LIMIT_OVER","message":"한도초과"}"""
            )
        }

        else {
            PgApproveResponse.success(
                paymentKey = "success_${uuidGenerator.generate()}",
                amount = request.amount,
                method = request.method ?: "카드",
                rawJson = "{\"result\":\"ok\"}"
            )
        }

    }
}
