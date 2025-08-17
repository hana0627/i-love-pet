package hana.lovepet.orderservice.api.repository.impl

import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import hana.lovepet.orderservice.api.controller.dto.request.OrderSearchCondition
import hana.lovepet.orderservice.api.controller.dto.response.GetOrdersResponse
import hana.lovepet.orderservice.api.domain.QOrder.order
import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.api.repository.OrderCustomRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class OrderCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : OrderCustomRepository {

    override fun searchOrders(
        condition: OrderSearchCondition,
        pageable: Pageable,
    ): Page<GetOrdersResponse> {

        val content = queryFactory.select(
            Projections.constructor(
                GetOrdersResponse::class.java,
                order.id,
                order.orderNo,
                order.userId,
                order.userName,
                order.status,
                order.price,
                order.createdAt,
                order.paymentId,
            )
        ).from(order)
            .where(
                userIdEq(condition.userId),
                statusEq(condition.status),
                orderNoContains(condition.searchQuery)
            )
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .orderBy(order.id.desc())
            .fetch()


        val total = queryFactory
            .select(order.id.count())
            .from(order)
            .where(
                userIdEq(condition.userId),
                statusEq(condition.status),
                orderNoContains(condition.searchQuery)
            )
            .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)


    }


    private fun userIdEq(userId: Long?): BooleanExpression? =
        userId?.let { order.userId.eq(it) }

    private fun statusEq(status: OrderStatus?): BooleanExpression? =
        status?.let { order.status.eq(it) }

    private fun orderNoContains(query: String?):  BooleanExpression? {
        val orderNo = query?.trim().orEmpty()
        if (orderNo.isEmpty()) return null

        // orderNo만 검색
        return order.orderNo.containsIgnoreCase(orderNo)
    }

}
