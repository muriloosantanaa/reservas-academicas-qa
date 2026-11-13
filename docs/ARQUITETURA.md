# Arquitetura

Documento no estilo **C4** (contexto → contêineres → componentes) mais as sequências críticas.
Os diagramas são Mermaid e são renderizados pelo GitHub.

## Nível 1 — Contexto

```mermaid
flowchart LR
    SOL["Solicitante<br/>(professor ou coordenador)"]
    RESP["Responsável<br/>(aprova e controla retirada)"]
    ADM["Administrador<br/>(cadastros e manutenção)"]
    SYS["ReservaHub<br/>Sistema de reservas acadêmicas"]
    WH["Serviço externo de notificação<br/>(webhook, opcional)"]

    SOL -->|consulta e reserva| SYS
    RESP -->|aprova, inicia, conclui| SYS
    ADM -->|gerencia recursos| SYS
    SYS -->|POST JSON após o commit| WH
```

## Nível 2 — Contêineres

```mermaid
flowchart LR
    NAV["Navegador<br/>painel HTML/CSS/JS"]
    API["API REST<br/>Spring Boot 4 · Java 21"]
    DB[("PostgreSQL 16<br/>JPA/Hibernate")]
    WH["Webhook externo"]

    NAV -->|HTTPS · HTTP Basic| API
    API -->|JDBC| DB
    API -->|HTTP, timeout 2 s| WH
```

## Nível 3 — Componentes da API

```mermaid
flowchart TB
    SEC["SecurityConfig<br/>autenticação e papéis por URL"]
    subgraph Controllers
        RC["ReservaController"]
        CC["CatalogoController"]
        MC["ManutencaoController"]
        REL["RelatorioController"]
    end
    subgraph Services
        RS["ReservaService<br/>regras, transação, locks"]
        MS["ManutencaoService"]
        CS["CatalogoService"]
        NS["NotificacaoService"]
    end
    subgraph Repositories
        RR["ReservaRepository<br/>JPQL de sobreposição"]
        OUTROS["Sala · Professor · Material<br/>Manutenção · Auditoria<br/>Notificação · ConflitoEvitado"]
    end
    EH["ApiExceptionHandler<br/>exceção → HTTP"]
    RP["RestricoesPostgres<br/>EXCLUDE no banco"]
    DB[("PostgreSQL")]

    SEC --> RC & CC & MC & REL
    RC --> RS
    CC --> CS
    MC --> MS
    REL --> RR
    RS --> MS
    RS --> NS
    RS --> RR
    RS --> OUTROS
    MS --> RR
    CS --> OUTROS
    NS --> OUTROS
    RR --> DB
    OUTROS --> DB
    RP -. cria restrições .-> DB
    RS -. exceções .-> EH
```

**Regra de dependência:** controller → service → repository. O controller não contém regra de negócio.

## Sequência crítica: criar reserva sob concorrência

```mermaid
sequenceDiagram
    actor A as Solicitante A
    actor B as Solicitante B
    participant S as ReservaService
    participant DB as PostgreSQL

    A->>S: criar(sala 1, 10h-12h)
    B->>S: criar(sala 1, 10h-12h)
    S->>DB: A: SELECT sala 1 FOR UPDATE (obtém o lock)
    S->>DB: B: SELECT sala 1 FOR UPDATE (espera)
    S->>DB: A: consulta de conflito (livre)
    S->>DB: A: INSERT reserva + auditoria
    S->>DB: A: COMMIT (libera o lock)
    DB-->>S: B obtém o lock
    S->>DB: B: consulta de conflito (agora vê a reserva de A)
    S-->>B: ConflitoHorarioException → HTTP 409
    Note over S,DB: noRollbackFor: o registro de conflito evitado é confirmado
    Note over DB: Rede de segurança: a restrição EXCLUDE recusaria o INSERT de B mesmo sem o lock
```

## Estados da reserva

```mermaid
stateDiagram-v2
    [*] --> SOLICITADA: material restrito
    [*] --> APROVADA: recurso comum
    SOLICITADA --> APROVADA: responsável aprova
    SOLICITADA --> REJEITADA: responsável rejeita
    SOLICITADA --> CANCELADA: dono ou responsável
    APROVADA --> EM_USO: responsável inicia
    APROVADA --> CANCELADA: dono ou responsável
    APROVADA --> NAO_COMPARECEU: responsável
    APROVADA --> SOLICITADA: período alterado (material restrito)
    EM_USO --> CONCLUIDA: responsável conclui
    REJEITADA --> [*]
    CANCELADA --> [*]
    NAO_COMPARECEU --> [*]
    CONCLUIDA --> [*]
```

A volta `APROVADA → SOLICITADA` só ocorre dentro de `alterar` (nova aprovação), nunca pela API de status.

## Decisões de arquitetura

Registradas como ADRs em [`docs/adr/`](adr):

1. [Arquitetura em camadas com Spring Boot](adr/0001-arquitetura-em-camadas.md)
2. [Dupla reserva: lock pessimista + restrição de exclusão](adr/0002-dupla-reserva-lock-e-exclusao.md)
3. [`noRollbackFor` em vez de `REQUIRES_NEW`](adr/0003-norollbackfor-conflito-evitado.md)
4. [Autenticação HTTP Basic para o escopo acadêmico](adr/0004-autenticacao-http-basic.md)
5. [Webhook após o commit, com timeout](adr/0005-webhook-apos-commit.md)

## Atributos de qualidade (utility tree resumida)

| Atributo | Cenário | Tática | Risco / trade-off |
|---|---|---|---|
| Consistência | Duas pessoas reservam o mesmo recurso ao mesmo tempo | Lock pessimista + EXCLUDE | Serializa o mesmo recurso |
| Segurança | Usuário acessa reserva alheia | Autorização por URL e por objeto | CSRF desabilitado com Basic |
| Testabilidade | Validar regras sem banco e a persistência com banco real | Camadas + Testcontainers | Testes de integração exigem Docker |
| Desempenho | Muitos usuários simultâneos | Locks por recurso, índices | **BCrypt a cada requisição limita a vazão** |
| Resiliência | Serviço de notificação lento ou fora | Timeout, após o commit, falha isolada | Notificação pode ser perdida (sem fila) |
