package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentCreateDto;
import com.example.paymentservice.dto.PaymentResponseDto;
import com.example.paymentservice.dto.PaymentSearchDto;
import com.example.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService service;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponseDto create(@Valid @RequestBody PaymentCreateDto dto) {
        return service.create(dto);
    }

    @GetMapping("/search")
    public List<PaymentResponseDto> search(PaymentSearchDto filter) {
        return service.search(filter);
    }

    // Сумма для текущего пользователя
    @GetMapping("/total/me")
    public BigDecimal getMyTotal(@RequestParam UUID userId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.getTotalForUser(userId, from, to);
    }

    // Сумма для админа
    @GetMapping("/total/all")
    public BigDecimal getAllTotal(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.getTotalForAll(from, to);
    }

}
