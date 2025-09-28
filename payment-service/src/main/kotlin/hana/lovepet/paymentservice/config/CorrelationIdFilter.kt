package hana.lovepet.paymentservice.config

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CorrelationIdFilter : Filter {

    override fun doFilter(request: ServletRequest?, response: ServletResponse?, chain: FilterChain?) {
        val httpReq = request as HttpServletRequest
        val httpRes = response as HttpServletResponse

        // 클라이언트가 X-Trace-Id를 보냈으면 재사용, 없으면 새로 생성
        val incoming = httpReq.getHeader("X-Trace-Id")
        val traceId = incoming ?: UUID.randomUUID().toString()

        MDC.put("traceId", traceId)
        httpRes.setHeader("X-Trace-Id", traceId)

        try {
            chain?.doFilter(request, response)
        } finally {
            MDC.remove("traceId")
        }
    }
}
