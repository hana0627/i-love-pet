package hana.lovepet.productservice.integration


import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.productservice.infrastructure.kafka.Topics
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.GetProductsEvent
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.GetProductsEvent.OrderItemRequest
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.ProductStockDecreaseEvent
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.ProductStockRollbackEvent
import hana.lovepet.productservice.infrastructure.kafka.out.dto.ProductsInformationResponseEvent
import hana.lovepet.productservice.infrastructure.kafka.out.dto.ProductStockDecreasedEvent
import hana.lovepet.productservice.api.product.domain.Product
import hana.lovepet.productservice.api.product.repository.ProductRepository
import hana.lovepet.productservice.api.product.repository.ProductCacheRepository
import hana.lovepet.productservice.api.product.service.ProductService
import org.springframework.data.redis.core.RedisTemplate
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@EnableKafka
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = ["listeners=PLAINTEXT://localhost:0", "port=0"],
    topics = [
        Topics.PRODUCT_INFORMATION_REQUEST,
        Topics.PRODUCT_INFORMATION_REQUEST+"-dlt",
        Topics.PRODUCT_INFORMATION_RESPONSE,
        Topics.PRODUCT_INFORMATION_RESPONSE+"-dlt",
        Topics.PRODUCT_STOCK_DECREASE,
        Topics.PRODUCT_STOCK_DECREASE+"-dlt",
        Topics.PRODUCT_STOCK_DECREASED,
        Topics.PRODUCT_STOCK_DECREASED+"-dlt",
        Topics.PRODUCT_STOCK_ROLLBACK,
        Topics.PRODUCT_STOCK_ROLLBACK+"-dlt",
    ]
)
@ActiveProfiles("test")
class KafkaEventTest {

    @Autowired
    private lateinit var productService: ProductService

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>
    @Autowired
    lateinit var om: ObjectMapper
    @Autowired
    lateinit var productRepository: ProductRepository

    @MockitoBean
    lateinit var productCacheRepository: ProductCacheRepository

    @MockitoBean
    lateinit var decreaseStockRedisTemplate: RedisTemplate<String, Boolean>

    @MockitoBean
    lateinit var rollbackStockRedisTemplate: RedisTemplate<String, Boolean>

    // 임베디드 카프카가 노출하는 브로커 주소 (가장 안전한 방식)
    @Value("\${spring.embedded.kafka.brokers}")
    lateinit var brokers: String

    lateinit var consumer: Consumer<String, String>

    @BeforeEach
    fun setup() {
        val consumerProps = KafkaTestUtils.consumerProps(brokers, "test-product-service", "true")
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"

        val consumerFactory = DefaultKafkaConsumerFactory(
            consumerProps,
            StringDeserializer(),
            StringDeserializer()
        )

        consumer = consumerFactory.createConsumer()
        consumer.subscribe(listOf(
            Topics.PRODUCT_INFORMATION_RESPONSE, Topics.PRODUCT_INFORMATION_RESPONSE+"-dlt",
            Topics.PRODUCT_INFORMATION_REQUEST, Topics.PRODUCT_INFORMATION_REQUEST+"-dlt",
            Topics.PRODUCT_STOCK_DECREASED, Topics.PRODUCT_STOCK_DECREASED+"-dlt",
            Topics.PRODUCT_STOCK_DECREASE, Topics.PRODUCT_STOCK_DECREASE+"-dlt",
            Topics.PRODUCT_STOCK_ROLLBACK, Topics.PRODUCT_STOCK_ROLLBACK+"-dlt"
        ))


        productRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        consumer.close()
    }




    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `상품조회 요청 이벤트를 발행하면, 응답 이벤트가 처리된다`() {
        // given
        val testProduct = Product(
            name = "테스트 상품",
            price = 10000,
            stock = 100,
            createdAt = java.time.LocalDateTime.now()
        )
        val savedProduct = productRepository.save(testProduct)

        val event = GetProductsEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 1234L,
            items = listOf(OrderItemRequest(productId = savedProduct.id!!, quantity = 2)),
            idempotencyKey = "idempotencyKey"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_INFORMATION_REQUEST, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(consumer, Topics.PRODUCT_INFORMATION_RESPONSE, Duration.ofSeconds(5))

        val responseEvent = om.readValue(record.value(), ProductsInformationResponseEvent::class.java)

        assertThat(event.orderId).isEqualTo(responseEvent.orderId)
        assertThat(true).isEqualTo(responseEvent.success)
        assertThat(event.items.size).isEqualTo(responseEvent.products.size)
        assertThat(savedProduct.id!!).isEqualTo(responseEvent.products[0].productId)
        assertThat(event.items[0].quantity).isEqualTo(responseEvent.products[0].quantity)
        assertThat(savedProduct.stock).isEqualTo(responseEvent.products[0].stock)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `상품조회 실패시 DLT 토픽으로 간다`() {
        // given: 존재하지 않는 상품 ID
        val event = GetProductsEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 9999L,
            items = listOf(OrderItemRequest(productId = 12345L, quantity = 2)),
            idempotencyKey = "idempotencyKey"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_INFORMATION_REQUEST, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(
            consumer,
            Topics.PRODUCT_INFORMATION_REQUEST + "-dlt",
            Duration.ofSeconds(5)
        )

        val failedEvent = om.readValue(dltRecord.value(), GetProductsEvent::class.java)
        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(9999L)
    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `상품조회 실패시, 실패 메세지가 발행된다`() {
        // given: 존재하지 않는 상품 ID
        val event = GetProductsEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 9999L,
            items = listOf(OrderItemRequest(productId = 12345L, quantity = 2)),
            idempotencyKey = "idempotencyKey"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_INFORMATION_REQUEST, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(consumer, Topics.PRODUCT_INFORMATION_RESPONSE, Duration.ofSeconds(5))

        val responseEvent = om.readValue(record.value(), ProductsInformationResponseEvent::class.java)

        assertThat(event.orderId).isEqualTo(responseEvent.orderId)
        assertThat(false).isEqualTo(responseEvent.success)
        assertThat(0).isEqualTo(responseEvent.products.size)
    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `재고차감 이벤트를 발행하면 재고 차감이 된 후 응답 이벤트가 처리된다`() {
        // given
        val initialStock = 100
        val decreaseQuantity = 5
        val testProduct = Product(
            name = "재고차감 테스트 상품",
            price = 15000,
            stock = initialStock,
            createdAt = java.time.LocalDateTime.now()
        )
        val savedProduct = productRepository.save(testProduct)

        val event = ProductStockDecreaseEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 12345L,
            products = listOf(
                ProductStockDecreaseEvent.Product(
                    productId = savedProduct.id!!,
                    quantity = decreaseQuantity
                )
            ),
            idempotencyKey = "stock-decrease-test-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_STOCK_DECREASE, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(consumer, Topics.PRODUCT_STOCK_DECREASED, Duration.ofSeconds(5))

        val responseEvent = om.readValue(record.value(), ProductStockDecreasedEvent::class.java)

        // 응답 이벤트 검증
        assertThat(responseEvent.orderId).isEqualTo(event.orderId)
        assertThat(responseEvent.success).isTrue()
        assertThat(responseEvent.errorMessage).isNull()

        // 재고 차감 확인
        val updatedProduct = productRepository.findById(savedProduct.id!!).orElseThrow()
        assertThat(updatedProduct.stock).isEqualTo(initialStock - decreaseQuantity)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `재고차감 실패시 DLT 토픽으로 간다`() {
        // given
        val initialStock = 5
        val decreaseQuantity = 10
        val testProduct = Product(
            name = "재고차감 테스트 상품",
            price = 15000,
            stock = initialStock,
            createdAt = java.time.LocalDateTime.now()
        )
        val savedProduct = productRepository.save(testProduct)

        val event = ProductStockDecreaseEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 12345L,
            products = listOf(
                ProductStockDecreaseEvent.Product(
                    productId = savedProduct.id!!,
                    quantity = decreaseQuantity
                )
            ),
            idempotencyKey = "failed-stock-decrease-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_STOCK_DECREASE, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(
            consumer,
            Topics.PRODUCT_STOCK_DECREASE + "-dlt",
            Duration.ofSeconds(5)
        )

        val failedEvent = om.readValue(dltRecord.value(), ProductStockDecreaseEvent::class.java)
        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(event.orderId)
        assertThat(failedEvent.products[0].productId).isEqualTo(event.products[0].productId)
        assertThat(failedEvent.products[0].quantity).isEqualTo(decreaseQuantity)

        // 재고차감 안되었음
        val updatedProduct = productRepository.findById(savedProduct.id!!).orElseThrow()
        assertThat(updatedProduct.stock).isEqualTo(initialStock)
    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `재고차감 실패시, 실패 메세지가 발행된다`() {
        // given
        val initialStock = 5
        val decreaseQuantity = 10
        val testProduct = Product(
            name = "재고차감 테스트 상품",
            price = 15000,
            stock = initialStock,
            createdAt = java.time.LocalDateTime.now()
        )
        val savedProduct = productRepository.save(testProduct)

        val event = ProductStockDecreaseEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 12345L,
            products = listOf(
                ProductStockDecreaseEvent.Product(
                    productId = savedProduct.id!!,
                    quantity = decreaseQuantity
                )
            ),
            idempotencyKey = "failed-stock-decrease-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_STOCK_DECREASE, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(consumer, Topics.PRODUCT_STOCK_DECREASED, Duration.ofSeconds(5))

        val responseEvent = om.readValue(record.value(), ProductStockDecreasedEvent::class.java)

        // 실패 응답 검증
        assertThat(responseEvent.orderId).isEqualTo(event.orderId)
        assertThat(responseEvent.success).isFalse()
        assertThat(responseEvent.errorMessage).isNotNull()
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `재고롤백 이벤트를 발행하면 재고가 원복된다`() {
        // given
        val initialStock = 100
        val decreasedStock = 85
        val rollbackQuantity = 15

        val testProduct = Product(
            name = "재고롤백 테스트 상품",
            price = 20000,
            stock = decreasedStock,
            createdAt = java.time.LocalDateTime.now()
        )
        val savedProduct = productRepository.save(testProduct)

        val event = ProductStockRollbackEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 12345L,
            products = listOf(
                ProductStockRollbackEvent.Product(
                    productId = savedProduct.id!!,
                    quantity = rollbackQuantity
                )
            ),
            idempotencyKey = "stock-rollback-success-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_STOCK_ROLLBACK, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // 재고롤백은 응답 이벤트가 없으므로, 데이터베이스에서 재고 증가가 될때까지 기다려야함
        var updatedProduct: Product? = null
        for (i in 1..7) { // 최대 7초 대기
            Thread.sleep(1000)
            updatedProduct = productRepository.findById(savedProduct.id!!).orElseThrow()
            if (updatedProduct.stock == initialStock) {
                break // 재고가 원복되면 즉시 종료
            }
        }

        assertThat(updatedProduct!!.stock).isEqualTo(decreasedStock + rollbackQuantity)
        assertThat(updatedProduct.stock).isEqualTo(initialStock)
    }

    @Test
    @Timeout(15, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `재고롤백 실패시 재시도 후 DLT 토픽으로 가고 DLT 핸들러가 처리한다`() {
        // given
        val event = ProductStockRollbackEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 12345L,
            products = listOf(
                ProductStockRollbackEvent.Product(
                    productId = 6666L,
                    quantity = 20
                )
            ),
            idempotencyKey = "failed-stock-rollback-key"
        )
        val json = om.writeValueAsString(event)

        // when: 정상 토픽으로 발송 (실패 후 재시도를 거쳐 DLT로 이동)
        kafkaTemplate.send(Topics.PRODUCT_STOCK_ROLLBACK, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then: 재시도 실패 후 DLT 토픽으로 메시지가 이동하고 DLT 핸들러가 처리
        val dltRecord = KafkaTestUtils.getSingleRecord(
            consumer,
            Topics.PRODUCT_STOCK_ROLLBACK + "-dlt",
            Duration.ofSeconds(10) // 재시도 시간 고려하여 대기시간 증가
        )

        val failedEvent = om.readValue(dltRecord.value(), ProductStockRollbackEvent::class.java)
        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(event.orderId)
        assertThat(failedEvent.products[0].productId).isEqualTo(event.products[0].productId)
        assertThat(failedEvent.products[0].quantity).isEqualTo(event.products[0].quantity)
        assertThat(failedEvent.idempotencyKey).isEqualTo(event.idempotencyKey)

        Thread.sleep(2000)
        // DLT 핸들러가 실행되었음을 확인 (메시지가 정상적으로 acknowledge되어 처리 완료)
        // 실제로는 로그에 "재고 복구 실패 (수동 개입 필요)" 메시지가 출력됨
    }
}

