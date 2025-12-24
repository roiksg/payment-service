package com.example.paymentservice.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class PaymentSearchDto {

    private UUID userId;
    private Long orderId;
    private String status;

}
