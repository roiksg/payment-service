package com.example.paymentservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class PaymentResponseDto {

    private String id;
    private Long orderId;
    private UUID userId;
    private String status;
    private Instant timestamp;
    private BigDecimal paymentAmount;

}
