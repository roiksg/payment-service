package com.example.paymentservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.paymentservice.dto.PaymentCreateDto;
import com.example.paymentservice.dto.PaymentResponseDto;
import com.example.paymentservice.dto.PaymentSearchDto;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.mapper.PaymentMapper;
import com.example.paymentservice.producer.PaymentEventProducer;
import com.example.paymentservice.repository.PaymentRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository repository;

    @Mock
    private PaymentMapper mapper;

    @Mock
    private PaymentEventProducer producer;

    @Mock
    private WebClient webClient;

    @InjectMocks
    private PaymentService service;

    private ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());

        WebClient realWebClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build();

        ReflectionTestUtils.setField(service, "webClient", realWebClient);

        ReflectionTestUtils.setField(service, "randomApiUrl", baseUrl + "/random");
    }
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private PaymentCreateDto createValidDto() {
        PaymentCreateDto dto = new PaymentCreateDto();
        dto.setOrderId(100L);
        dto.setUserId(UUID.randomUUID());
        dto.setPaymentAmount(BigDecimal.valueOf(999.99));
        return dto;
    }

    private Payment createSavedPayment(String id, String status) {
        Payment p = new Payment();
        p.setId(id);
        p.setOrderId(100L);
        p.setUserId(UUID.randomUUID());
        p.setStatus(status);
        p.setTimestamp(Instant.now());
        p.setPaymentAmount(BigDecimal.valueOf(999.99));
        return p;
    }

    private PaymentResponseDto createResponseDto(Payment payment) {
        PaymentResponseDto dto = new PaymentResponseDto();
        dto.setId(payment.getId());
        dto.setOrderId(payment.getOrderId());
        dto.setUserId(payment.getUserId());
        dto.setStatus(payment.getStatus());
        dto.setTimestamp(payment.getTimestamp());
        dto.setPaymentAmount(payment.getPaymentAmount());
        return dto;
    }

    @Test
    void create_SuccessfulCreationWithEvenRandom() throws InterruptedException {
        // Подготовка: сервер вернёт чётное число → SUCCESS
        mockWebServer.enqueue(new MockResponse()
            .setBody("4") // чётное
            .addHeader("Content-Type", "text/plain"));

        PaymentCreateDto dto = createValidDto();

        Payment mappedPayment = new Payment();
        mappedPayment.setOrderId(dto.getOrderId());
        mappedPayment.setUserId(dto.getUserId());
        mappedPayment.setPaymentAmount(dto.getPaymentAmount());

        when(mapper.toEntity(dto)).thenReturn(mappedPayment);

        Payment savedPayment = createSavedPayment("123", "SUCCESS");
        when(repository.save(any())).thenReturn(savedPayment);

        PaymentResponseDto responseDto = new PaymentResponseDto();
        // заполни поля по аналогии
        responseDto.setId("123");
        responseDto.setOrderId(dto.getOrderId());
        responseDto.setUserId(dto.getUserId());
        responseDto.setStatus("SUCCESS");
        responseDto.setTimestamp(savedPayment.getTimestamp());
        responseDto.setPaymentAmount(dto.getPaymentAmount());

        when(mapper.toDto(savedPayment)).thenReturn(responseDto);

        PaymentResponseDto result = service.create(dto);

        assertEquals("SUCCESS", result.getStatus());
        verify(producer).sendCreatePaymentEvent(savedPayment);

        // Проверка, что запрос к внешнему API был сделан
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/random", request.getPath());
        assertEquals("GET", request.getMethod());
    }

    @Test
    void create_SuccessfulCreationWithOddRandom() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .setBody("7") // нечётное
            .addHeader("Content-Type", "text/plain"));

        PaymentCreateDto dto = createValidDto();

        when(mapper.toEntity(dto)).thenReturn(new Payment(/* ... */));
        Payment savedPayment = createSavedPayment("123", "FAILED");
        when(repository.save(any())).thenReturn(savedPayment);
        when(mapper.toDto(any())).thenReturn(createResponseDto(savedPayment));

        PaymentResponseDto result = service.create(dto);

        assertEquals("FAILED", result.getStatus());
        verify(producer).sendCreatePaymentEvent(savedPayment);

        mockWebServer.takeRequest();
    }


    @Test
    void search_ByUserId_Positive() {
        PaymentSearchDto filter = new PaymentSearchDto();
        UUID userId = UUID.randomUUID();
        filter.setUserId(userId);

        List<Payment> payments = List.of(new Payment());
        when(repository.findByUserId(userId)).thenReturn(payments);

        PaymentResponseDto dto = new PaymentResponseDto();
        when(mapper.toDto(any())).thenReturn(dto);

        List<PaymentResponseDto> result = service.search(filter);

        assertEquals(1, result.size());
        verify(repository).findByUserId(userId);
    }

    @Test
    void search_ByOrderId_Positive() {
        PaymentSearchDto filter = new PaymentSearchDto();
        Long orderId = 1L;
        filter.setOrderId(orderId);

        List<Payment> payments = List.of(new Payment());
        when(repository.findByOrderId(orderId)).thenReturn(payments);

        PaymentResponseDto dto = new PaymentResponseDto();
        when(mapper.toDto(any())).thenReturn(dto);

        List<PaymentResponseDto> result = service.search(filter);

        assertEquals(1, result.size());
        verify(repository).findByOrderId(orderId);
    }

    @Test
    void search_ByStatus_Positive() {
        PaymentSearchDto filter = new PaymentSearchDto();
        String status = "SUCCESS";
        filter.setStatus(status);

        List<Payment> payments = List.of(new Payment());
        when(repository.findByStatus(status)).thenReturn(payments);

        PaymentResponseDto dto = new PaymentResponseDto();
        when(mapper.toDto(any())).thenReturn(dto);

        List<PaymentResponseDto> result = service.search(filter);

        assertEquals(1, result.size());
        verify(repository).findByStatus(status);
    }

    @Test
    void search_All_Positive() {
        PaymentSearchDto filter = new PaymentSearchDto(); // all null

        List<Payment> payments = List.of(new Payment());
        when(repository.findAll()).thenReturn(payments);

        PaymentResponseDto dto = new PaymentResponseDto();
        when(mapper.toDto(any())).thenReturn(dto);

        List<PaymentResponseDto> result = service.search(filter);

        assertEquals(1, result.size());
        verify(repository).findAll();
    }

    @Test
    void search_RepositoryException_Negative() {
        PaymentSearchDto filter = new PaymentSearchDto();
        UUID userId = UUID.randomUUID();
        filter.setUserId(userId);

        when(repository.findByUserId(userId)).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> service.search(filter));
    }

    @Test
    void getTotalForUser_Positive() {
        UUID userId = UUID.randomUUID();
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        Payment p1 = new Payment();
        p1.setPaymentAmount(BigDecimal.valueOf(50));
        Payment p2 = new Payment();
        p2.setPaymentAmount(BigDecimal.valueOf(50));

        when(repository.findByUserIdAndTimestampBetween(userId, from, to)).thenReturn(List.of(p1, p2));

        BigDecimal result = service.getTotalForUser(userId, from, to);

        assertEquals(BigDecimal.valueOf(100), result);
    }

    @Test
    void getTotalForUser_EmptyList_Positive() {
        UUID userId = UUID.randomUUID();
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        when(repository.findByUserIdAndTimestampBetween(userId, from, to)).thenReturn(Collections.emptyList());

        BigDecimal result = service.getTotalForUser(userId, from, to);

        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void getTotalForUser_RepositoryException_Negative() {
        UUID userId = UUID.randomUUID();
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        when(repository.findByUserIdAndTimestampBetween(userId, from, to)).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> service.getTotalForUser(userId, from, to));
    }

    @Test
    void getTotalForAll_Positive() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        Payment p1 = new Payment();
        p1.setPaymentAmount(BigDecimal.valueOf(50));
        Payment p2 = new Payment();
        p2.setPaymentAmount(BigDecimal.valueOf(50));

        when(repository.findByTimestampBetween(from, to)).thenReturn(List.of(p1, p2));

        BigDecimal result = service.getTotalForAll(from, to);

        assertEquals(BigDecimal.valueOf(100), result);
    }

    @Test
    void getTotalForAll_EmptyList_Positive() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        when(repository.findByTimestampBetween(from, to)).thenReturn(Collections.emptyList());

        BigDecimal result = service.getTotalForAll(from, to);

        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void getTotalForAll_RepositoryException_Negative() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        when(repository.findByTimestampBetween(from, to)).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> service.getTotalForAll(from, to));
    }
}
