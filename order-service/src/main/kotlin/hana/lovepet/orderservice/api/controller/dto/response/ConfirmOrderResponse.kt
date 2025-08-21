package hana.lovepet.orderservice.api.controller.dto.response

data class ConfirmOrderResponse(
    val success: Boolean,
    val orderNo: String,
    val paymentId: Long? = null,
    val message: String? = null
) {
}
