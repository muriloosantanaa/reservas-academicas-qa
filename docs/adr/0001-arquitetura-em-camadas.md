# ADR 0001 — Arquitetura em camadas com Spring Boot

- **Status:** aceita
- **Contexto:** o enunciado exige regras de negócio verificáveis, testes unitários isolados e testes de integração com banco real.
- **Decisão:** camadas `controller → service → repository → PostgreSQL`; regra de negócio e transação ficam no *service*; o controller só traduz HTTP e valida a entrada.
- **Consequências:** o service é testado com repositórios simulados (Mockito); o repositório com PostgreSQL real; a API como caixa-preta. Custo: entidades JPA acabaram usadas como corpo de requisição (ver `REGISTRO_DE_DEFEITOS.md`, DEF-01), o que pede DTOs numa evolução.
- **Alternativas:** hexagonal/DDD (mais indicada para sistemas maiores; excessiva aqui).
