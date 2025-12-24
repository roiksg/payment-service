package com.example.paymentservice.repository;

import com.example.paymentservice.entity.Payment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    List<Payment> findByUserId(UUID userId);   // UUID

    List<Payment> findByOrderId(Long orderId); // Long

    List<Payment> findByStatus(String status);

    @Query(value = "{ userId: ?0, timestamp: { $gte: ?1, $lte: ?2 } }", fields = "{ paymentAmount: 1 }")
    List<Payment> findByUserIdAndTimestampBetween(UUID userId, Instant from, Instant to);

    @Query(value = "{ timestamp: { $gte: ?0, $lte: ?1 } }", fields = "{ paymentAmount: 1 }")
    List<Payment> findByTimestampBetween(Instant from, Instant to);
}