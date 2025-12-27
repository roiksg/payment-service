package com.example.paymentservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payments")
public class Payment {
    @Id
    private String id;

    private Long orderId;

    private UUID userId;

    private String status;

    private Instant timestamp;

    private BigDecimal paymentAmount;

}
