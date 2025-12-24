package com.example.paymentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class PaymentCreateDto {

    @NotNull
    private Long orderId;

    @NotNull
    private UUID userId;

    @Positive
    private BigDecimal paymentAmount;

}
