package hana.lovepet.orderservice.api.controller.dto.response

data class PrepareOrderResponse (
    val orderId: String,
    val amount: Long
){
}
