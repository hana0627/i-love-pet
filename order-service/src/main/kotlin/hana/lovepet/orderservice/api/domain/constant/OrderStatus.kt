package hana.lovepet.orderservice.api.domain.constant

enum class OrderStatus(
    val value: String
) {
    CREATED("주문 생성"),
    VALIDATING("상품 검증 중"),
    VALIDATION_FAILED("상품 검증 실패"),
    CONFIRMED("주문 확정 (결제 준비 완료)"),
    PAYMENT_PENDING("결제 진행 중"),
    PAYMENT_COMPLETED("결제 완료"),
    PAYMENT_FAILED("결제 실패"),
    CANCELED("주문 취소"),
//    CREATED("주문생성"),
//    CONFIRMED("주문확정"),
    FAIL("주문실패")
//    CANCELED("주문취소"),
}
