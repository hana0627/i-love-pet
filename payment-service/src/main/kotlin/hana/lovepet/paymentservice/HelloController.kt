package hana.lovepet.paymentservice

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController


@RestController
class HelloController {

    @GetMapping("/")
    fun index(): String {
        return "payment-service is health"
    }

    @GetMapping("/hello")
    fun hello(): String {
        return "payment-service hello"
    }
}