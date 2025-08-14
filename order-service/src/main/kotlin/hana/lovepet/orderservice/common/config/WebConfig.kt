package hana.lovepet.orderservice.common.config

import org.springframework.stereotype.Component
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Component
class WebConfig : WebMvcConfigurer {

    private val frontEndUrl: String = "http://localhost:3000"

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(frontEndUrl)
            .allowedMethods("GET","POST","PATCH","DELETE")
            .allowCredentials(true)
    }

}
