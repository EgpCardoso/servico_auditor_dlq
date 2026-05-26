package com.example.demo.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.model.AuditStatus;
import com.example.demo.domain.model.Severity;
import com.example.demo.infrastructure.adapters.in.messaging.dlq.dto.OrderItemDTO;
import com.example.demo.infrastructure.adapters.in.messaging.dlq.dto.PedidoDTO;
import com.example.demo.infrastructure.adapters.out.persistence.AuditRecordEntity;
import com.example.demo.infrastructure.adapters.out.persistence.AuditRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DlqAuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DlqAuditService.class);

    private final ObjectMapper objectMapper;
    private final AuditRecordRepository auditRecordRepository;

    public DlqAuditService(
            ObjectMapper objectMapper,
            AuditRecordRepository auditRecordRepository) {
        this.objectMapper = objectMapper;
        this.auditRecordRepository = auditRecordRepository;
    }

    @Transactional
    public void audit(String queueName, String rawPayload, String failureReason) {
        Optional<PedidoDTO> pedido = parsePayload(rawPayload);
        Severity severity = pedido
                .map(PedidoDTO::getOrderItems)
                .map(this::classifySeverity)
                .orElse(Severity.LOW);
        String resolvedFailureReason = normalizeFailureReason(failureReason, pedido.isEmpty());

        AuditRecordEntity entity = new AuditRecordEntity();
        entity.setErrorId(UUID.randomUUID());
        entity.setQueueName(queueName);
        entity.setPayload(rawPayload);
        entity.setTimestamp(Instant.now());
        entity.setStatus(AuditStatus.PENDING_ANALYSIS);
        entity.setSeverity(severity);
        entity.setFailureReason(resolvedFailureReason);

        auditRecordRepository.save(entity);
    }

    private Optional<PedidoDTO> parsePayload(String rawPayload) {
        try {
            return Optional.of(objectMapper.readValue(rawPayload, PedidoDTO.class));
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Payload da DLQ nao esta no formato esperado de PedidoDTO. O payload bruto sera salvo no banco.", ex);
            return Optional.empty();
        }
    }

    private Severity classifySeverity(List<OrderItemDTO> orderItems) {
        int totalAmount = orderItems.stream()
                .mapToInt(OrderItemDTO::getAmount)
                .sum();

        if (totalAmount > 100) {
            return Severity.HIGH;
        }
        if (totalAmount >= 50) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }

    private String normalizeFailureReason(String failureReason, boolean payloadParsingFailed) {
        if (payloadParsingFailed) {
            String baseReason = (failureReason == null || failureReason.isBlank())
                    ? "Original processing error not provided by message attributes"
                    : failureReason;
            return baseReason + " | Payload could not be parsed as PedidoDTO";
        }
        if (failureReason == null || failureReason.isBlank()) {
            return "Original processing error not provided by message attributes";
        }
        return failureReason;
    }
}
