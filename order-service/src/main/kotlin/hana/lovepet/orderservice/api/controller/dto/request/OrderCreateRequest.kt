package hana.lovepet.orderservice.api.controller.dto.request

data class OrderCreateRequest(
    val userId: Long,
    val items: List<OrderItemRequest>
) {
}
