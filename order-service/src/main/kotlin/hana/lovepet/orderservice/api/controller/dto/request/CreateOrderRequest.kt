package hana.lovepet.orderservice.api.controller.dto.request

data class CreateOrderRequest(
    val userId: Long,
    val method: String? = "카드",
    val items: List<CreateOrderItemRequest>
) {
}
