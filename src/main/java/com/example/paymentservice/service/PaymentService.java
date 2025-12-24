package com.example.paymentservice.service;


import com.example.paymentservice.dto.PaymentCreateDto;
import com.example.paymentservice.dto.PaymentResponseDto;
import com.example.paymentservice.dto.PaymentSearchDto;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.mapper.PaymentMapper;
import com.example.paymentservice.repository.PaymentRepository;
import groovy.util.logging.Slf4j;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final PaymentMapper mapper;
    private final PaymentEventProducer producer;
    private final WebClient webClient;

    @Value("${external.random-api}")
    private String randomApiUrl;

    public PaymentResponseDto create(PaymentCreateDto dto) {
        // Генерируем случайное число
        Integer random = webClient.get()
            .uri(randomApiUrl)
            .retrieve()
            .bodyToMono(Integer.class)
            .block();

        String status = (random % 2 == 0) ? "SUCCESS" : "FAILED";

        Payment payment = mapper.toEntity(dto);
        payment.setStatus(status);
        payment.setTimestamp(Instant.now());

        payment = repository.save(payment);

        // Отправляем событие в Kafka
        producer.sendCreatePaymentEvent(payment);

        return mapper.toDto(payment);
    }

    public List<PaymentResponseDto> search(PaymentSearchDto filter) {
        List<Payment> list;
        if (filter.getUserId() != null) {
            list = repository.findByUserId(filter.getUserId());
        } else if (filter.getOrderId() != null) {
            list = repository.findByOrderId(filter.getOrderId());
        } else if (filter.getStatus() != null) {
            list = repository.findByStatus(filter.getStatus());
        } else {
            list = repository.findAll();
        }
        return list.stream().map(mapper::toDto).toList();
    }

    public BigDecimal getTotalForUser(UUID userId, Instant from, Instant to) {
        return repository.findByUserIdAndTimestampBetween(userId, from, to)
            .stream()
            .map(Payment::getPaymentAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalForAll(Instant from, Instant to) {
        return repository.findByTimestampBetween(from, to)
            .stream()
            .map(Payment::getPaymentAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
