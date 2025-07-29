package hana.lovepet.orderservice.api.domain.constant

enum class OrderStatus(
    val value: String
) {
    CREATED("주문생성"),
    CONFIRMED("주문확정"),
    CANCELED("주문취소"),
}
