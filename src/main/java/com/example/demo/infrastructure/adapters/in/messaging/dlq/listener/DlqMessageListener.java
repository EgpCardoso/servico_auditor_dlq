package com.example.demo.infrastructure.adapters.in.messaging.dlq.listener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.demo.application.service.DlqAuditService;

import io.awspring.cloud.sqs.annotation.SqsListener;

@Component
@ConditionalOnProperty(value = "auditor.sqs.listener.enabled", havingValue = "true", matchIfMissing = true)
public class DlqMessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DlqMessageListener.class);

    private final DlqAuditService dlqAuditService;
    private final String queueName;

    public DlqMessageListener(
            DlqAuditService dlqAuditService,
            @Value("${queue.order-events}") String queueName) {
        this.dlqAuditService = dlqAuditService;
        this.queueName = queueName;
    }

    @SqsListener("${queue.order-events}")
    public void receive(Message<String> message) {
        String failureReason = extractFailureReason(message.getHeaders());

        LOGGER.info("Mensagem recebida da DLQ {}. Payload size: {} bytes.", queueName, message.getPayload().length());
        LOGGER.info("Failure reason resolvido para a DLQ {}: {}", queueName, failureReason);

        dlqAuditService.audit(queueName, message.getPayload(), failureReason);

        LOGGER.info("Mensagem da DLQ {} persistida com sucesso no banco de auditoria.", queueName);
    }

    private String extractFailureReason(MessageHeaders headers) {
        String[] candidateKeys = {
                "errorMessage",
                "ErrorMessage",
                "x-exception-message",
                "exception-message",
                "failureReason"
        };

        for (String key : candidateKeys) {
            Object value = headers.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }

        return "Original processing error not provided by message attributes";
    }
}
