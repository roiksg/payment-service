package com.example.paymentservice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.paymentservice.dto.PaymentCreateDto;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
//@EmbeddedKafka(partitions = 1, topics = {"payment-events"}, brokerProperties = {"listeners=PLAINTEXT://localhost:0"})
class PaymentControllerIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
        .withKraft()
        .waitingFor(
            Wait.forListeningPort()
                .withStartupTimeout(Duration.ofSeconds(120))
        )
        .withEnv("KAFKA_LOG4J_LOGGERS", "kafka=WARN");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        registry.add("external.random-api", () -> "http://localhost:8089/integers/?num=1&min=1&max=100&col=1&base=10&format=plain&rnd=new");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules()
        .registerModule(new JavaTimeModule());

    private WireMockServer wireMockServer;

//    @Value("${external.random-api}")
//    private String randomApiUrl;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();

        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }


    @Test
    void create_Positive() throws Exception {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/integers/.*"))
            .willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "text/plain")
                .withBody("4")));

        PaymentCreateDto dto = new PaymentCreateDto();
        dto.setOrderId(1L);
        dto.setUserId(UUID.randomUUID());
        dto.setPaymentAmount(BigDecimal.valueOf(100));

        ResultActions result = mockMvc.perform(post("/payments/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(dto)));

        result.andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS")) // Assuming even -> SUCCESS
            .andExpect(jsonPath("$.paymentAmount").value(100));
    }

    @Test
    void create_Negative_ValidationError() throws Exception {
        PaymentCreateDto dto = new PaymentCreateDto(); // missing fields

        ResultActions result = mockMvc.perform(post("/payments/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(dto)));

        result.andExpect(status().isBadRequest());
    }

    @Test
    void search_Positive() throws Exception {
        UUID userId = UUID.randomUUID();
        Payment p = new Payment();
        p.setUserId(userId);
        p.setOrderId(1L);
        p.setStatus("SUCCESS");
        p.setTimestamp(Instant.now());
        p.setPaymentAmount(BigDecimal.valueOf(100));
        repository.save(p);

        ResultActions result = mockMvc.perform(get("/payments/search")
            .param("userId", userId.toString()));

        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.size()").value(1))
            .andExpect(jsonPath("$[0].userId").value(userId.toString()));
    }

    @Test
    void search_Negative_EmptyResult() throws Exception {
        ResultActions result = mockMvc.perform(get("/payments/search")
            .param("userId", UUID.randomUUID().toString())); // non-existing

        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.size()").value(0));
    }

    @Test
    void getMyTotal_Positive() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Payment p = new Payment();
        p.setUserId(userId);
        p.setPaymentAmount(BigDecimal.valueOf(100));
        p.setTimestamp(now);
        repository.save(p);

        Instant from = now.minus(1, ChronoUnit.DAYS);
        Instant to = now.plus(1, ChronoUnit.DAYS);

        ResultActions result = mockMvc.perform(get("/payments/total/me")
            .param("userId", userId.toString())
            .param("from", from.toString())
            .param("to", to.toString()));

        result.andExpect(status().isOk())
            .andExpect(jsonPath("$").value(100));
    }

    @Test
    void getMyTotal_Negative_InvalidDates() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant from = Instant.now().plus(1, ChronoUnit.DAYS); // from > to
        Instant to = Instant.now().minus(1, ChronoUnit.DAYS);

        ResultActions result = mockMvc.perform(get("/payments/total/me")
            .param("userId", userId.toString())
            .param("from", from.toString())
            .param("to", to.toString()));

        result.andExpect(status().isOk())
            .andExpect(jsonPath("$").value(0)); // assuming it returns 0 for invalid range
    }

    @Test
    void getAllTotal_Positive() throws Exception {
        Instant now = Instant.now();
        Payment p = new Payment();
        p.setPaymentAmount(BigDecimal.valueOf(100));
        p.setTimestamp(now);
        repository.save(p);

        Instant from = now.minus(1, ChronoUnit.DAYS);
        Instant to = now.plus(1, ChronoUnit.DAYS);

        ResultActions result = mockMvc.perform(get("/payments/total/all")
            .param("from", from.toString())
            .param("to", to.toString()));

        result.andExpect(status().isOk())
            .andExpect(jsonPath("$").value(100));
    }

    @Test
    void getAllTotal_Negative_InvalidDates() throws Exception {
        Instant from = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().minus(1, ChronoUnit.DAYS);

        ResultActions result = mockMvc.perform(get("/payments/total/all")
            .param("from", from.toString())
            .param("to", to.toString()));

        result.andExpect(status().isOk())
            .andExpect(jsonPath("$").value(0));
    }
}
