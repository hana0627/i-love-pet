package hana.lovepet.orderservice.common.exception.constant

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String
) {
    UNHEALTHY_SERVER_COMMUNICATION(HttpStatus.INTERNAL_SERVER_ERROR, "서버간 통신에 실패했습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품정보를 찾을 수 없습니다."),
    PAYMENTS_REQUEST_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "결제요청에 실패했습니다."),
    PAYMENTS_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "결제에 실패했습니다."),
    NOT_ENOUGH_STOCK(HttpStatus.INTERNAL_SERVER_ERROR, "상품 재고가 부족합니다."),
}
