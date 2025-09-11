package hana.lovepet.orderservice.api.domain.constant

enum class OrderStatus(
    val value: String
) {
    CREATED("주문 생성"),
    VALIDATING("상품 검증 중"),
    VALIDATION_SUCCESS("상품 검증 성공"),
    VALIDATION_FAILED("상품 검증 실패"),
    PROCESSING_FAILED("주문 처리 실패"),
    PREPARED("결제 준비 완료"),
    PAYMENT_PREPARE_FAIL("결제정보 생성중 오류발생"),
    DECREASE_STOCK("재고 차감 요청"),
    DECREASE_STOCK_FAIL("재고 차감 살패"),
    PAYMENT_PENDING("결제 진행 중"),
    PAYMENT_FAILED("결제 실패"),
    CANCELED("주문 취소"),
    CONFIRMED("주문 확정 (결제 완료)"),
    FAIL("주문실패")
}
