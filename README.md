# Serviço Auditor DLQ

Este serviço lê mensagens de uma Dead Letter Queue (DLQ), avalia a gravidade do erro e salva um registro de auditoria para análise.

## Estrutura

Foi usada uma arquitetura hexagonal simplificada:

- `in.adapters` → entrada do serviço (consumer SQS e DTOs da fila).
- `application.service` → regra principal: auditar a mensagem.
- `domain.model` → conceitos fixos de negócio, como `Severity` e `AuditStatus`.
- `out.persistence` → persistência no banco (JPA/H2).

Essa separação ajuda a manter a lógica de negócio independente da AWS ou do banco. Se mudar a fila ou o banco, só o adapter correspondente precisa ser ajustado.

## Regra de negócio

A severidade é calculada pelo total (`amount`) dos itens:

- `HIGH` → maior que 100
- `MEDIUM` → entre 50 e 100
- `LOW` → menor que 50

O registro salvo inclui também o motivo da falha (`failureReason`), quando disponível.

## Fluxo

1. O listener lê a fila `queue.order-events`.
2. A mensagem bruta é guardada.
3. O serviço interpreta o payload, calcula severidade e gera um `UUID` para `errorId`.
4. O registro é salvo com status `PENDING_ANALYSIS`.
5. Se houver erro ao salvar, a mensagem não é removida da fila.

## Banco

Por padrão, usa H2 em memória para facilitar testes locais. O Hibernate cria a tabela automaticamente.

## Configuração

No arquivo `application.properties` ficam:

- `queue.order-events` → fila DLQ
- `queue.order-events-original` → fila original
- Credenciais AWS (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`)

## Observação

Nem sempre a DLQ traz o motivo da falha. Quando não há informação, o serviço registra um texto padrão dizendo que a causa não foi fornecida.
