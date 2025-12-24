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
    private String id;               // Mongo ObjectId

    private Long orderId;            // теперь Long

    private UUID userId;             // теперь UUID

    private String status;           // SUCCESS / FAILED

    private Instant timestamp;

    private BigDecimal paymentAmount;

}
