package hana.lovepet.paymentservice.api.payment.domain.constant

enum class PaymentStatus(
    val value: String
) {
    PENDING("결제 요청"),   // 결제 승인 대기중
    SUCCESS("결제 성공"),   // 결제 정상 완료
    FAIL("결제 실패"),      // 결제 실패
    CANCELED("결제 취소"),  // 승인 후 취소 (주로 사용자가 직접, 또는 시스템상 오류 등)
    REFUNDED("환불"),  // 결제 후 환불 (부분/전체)
}
