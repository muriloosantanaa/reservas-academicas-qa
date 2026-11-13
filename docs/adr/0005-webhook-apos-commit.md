# ADR 0005 — Webhook de notificação depois do commit, com timeout

- **Status:** aceita
- **Contexto:** a integração externa não pode comprometer a reserva nem segurar locks do banco.
- **Decisão:** a notificação é sempre persistida; se `notificacao.webhook.url` existir, o POST JSON é enviado em `afterCommit`, com timeout (2 s) e qualquer falha apenas registrada em log.
- **Consequências:** nunca se notifica algo que sofreu rollback; chamada lenta não segura lock. Sem retentativa nem fila (uma notificação pode ser perdida).
- **Evolução recomendada:** padrão *outbox* com retentativas.
