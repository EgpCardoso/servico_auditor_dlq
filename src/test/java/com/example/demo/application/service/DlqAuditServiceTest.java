package com.example.demo.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.demo.domain.model.AuditStatus;
import com.example.demo.domain.model.Severity;
import com.example.demo.infrastructure.adapters.out.persistence.AuditRecordEntity;
import com.example.demo.infrastructure.adapters.out.persistence.AuditRecordRepository;

@SpringBootTest(properties = "auditor.sqs.listener.enabled=false")
class DlqAuditServiceTest {

    @Autowired
    private DlqAuditService dlqAuditService;

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @Test
    void shouldPersistAuditRecordWithMediumSeverity() {
        String payload = """
                {
                  "zipCode": "80010000",
                  "customerId": 1,
                  "orderItems": [
                    {
                      "sku": 1,
                      "amount": 20
                    },
                    {
                      "sku": 2,
                      "amount": 30
                    }
                  ],
                  "origin": "SQS_QUEUE",
                  "occurredAt": "2024-05-20T14:30:00Z"
                }
                """;

        dlqAuditService.audit("T03N_EDSOM_GABRIEL_POMMER_DLQ.fifo", payload, "Falha de validacao");

        AuditRecordEntity entity = auditRecordRepository.findAll().get(0);

        assertEquals("T03N_EDSOM_GABRIEL_POMMER_DLQ.fifo", entity.getQueueName());
        assertEquals(AuditStatus.PENDING_ANALYSIS, entity.getStatus());
        assertEquals(Severity.MEDIUM, entity.getSeverity());
        assertEquals("Falha de validacao", entity.getFailureReason());
        assertEquals(payload, entity.getPayload());
    }

    @Test
    void shouldPersistAuditRecordEvenWhenPayloadIsInvalid() {
        String invalidPayload = "---- payload invalido recebido da dlq ----";

        dlqAuditService.audit("T03N_EDSOM_GABRIEL_POMMER_DLQ.fifo", invalidPayload, null);

        AuditRecordEntity entity = auditRecordRepository.findAll()
                .stream()
                .filter(record -> invalidPayload.equals(record.getPayload()))
                .findFirst()
                .orElseThrow();

        assertEquals("T03N_EDSOM_GABRIEL_POMMER_DLQ.fifo", entity.getQueueName());
        assertEquals(AuditStatus.PENDING_ANALYSIS, entity.getStatus());
        assertEquals(Severity.LOW, entity.getSeverity());
        assertEquals(
                "Original processing error not provided by message attributes | Payload could not be parsed as PedidoDTO",
                entity.getFailureReason());
        assertEquals(invalidPayload, entity.getPayload());
    }
}
