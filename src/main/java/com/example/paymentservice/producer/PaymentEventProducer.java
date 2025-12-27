package com.example.paymentservice.producer;

import com.example.paymentservice.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendCreatePaymentEvent(Payment payment) {
        var event = new PaymentEvent(payment.getId(), payment.getStatus());
        kafkaTemplate.send("payment-events", event);
        log.info("Отправлено событие CREATE_PAYMENT: {}", event);
    }

    public record PaymentEvent(String paymentId, String status) {}

}
