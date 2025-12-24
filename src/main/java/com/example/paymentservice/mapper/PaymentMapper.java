package com.example.paymentservice.mapper;

import com.example.paymentservice.dto.PaymentCreateDto;
import com.example.paymentservice.dto.PaymentResponseDto;
import com.example.paymentservice.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    Payment toEntity(PaymentCreateDto dto);

    PaymentResponseDto toDto(Payment payment);

}
