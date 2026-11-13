# ReservaHub — Reservas Acadêmicas

Aplicação web e API REST para gestão de reservas de **salas, professores e materiais** sem conflito de horário.
Projeto individual da disciplina de Qualidade de Software.

**Autor:** Murilo Henrique Santana Rodrigues

[![quality-checks](https://github.com/SEU_USUARIO/reservas-academicas/actions/workflows/ci.yml/badge.svg)](https://github.com/SEU_USUARIO/reservas-academicas/actions/workflows/ci.yml)

> Troque `SEU_USUARIO` pelo seu usuário do GitHub neste arquivo e, no `pom.xml`, preencha as propriedades `sonar.*`.

## Estado da qualidade (leia primeiro)

| Meta do enunciado | Resultado | Como foi obtido |
|---|---|---|
| Cobertura de linhas ≥ 80% | **97,3%** | JaCoCo, unitários + integração, em ambiente de desenvolvimento (`docs/evidencias/`) |
| Cobertura de ramos ≥ 70% | **90,9%** | idem |
| Testes automatizados | **195 unitários + 104 de integração** passando; 3 de contexto Spring a rodar no Maven | ver `docs/evidencias/testes_execucao_local.txt` |
| Dupla reserva impossível | **1 aceita em 50 simultâneas** (PostgreSQL 16 real) | `docs/evidencias/concorrencia_postgres16.txt`, `ConcorrenciaReservaIT` |
| 100% dos requisitos críticos na RTM | **Sim** | `RTM.md` |
| 0 bugs/vulnerabilidades críticas | **Pendente: executar o SonarCloud** | ver "SonarCloud" abaixo |
| Pipeline de CI | **Configurado; pendente o primeiro run verde no GitHub** | `.github/workflows/ci.yml` |
| Teste de carga (JMeter) | **Plano pronto; pendente a execução** | `performance/` |
| Evidência de TDD | **Pendente: seus commits RED/GREEN/REFACTOR** | `docs/TDD_BDD.md` |

Os números de cobertura e testes foram medidos fora do Maven (ver `docs/evidencias/testes_execucao_local.txt`).
**Confirme-os rodando `./mvnw verify` no seu computador e guarde a saída.**

## Tecnologias

Java 21 · Spring Boot 4.1.2 · Spring MVC, Data JPA, Security e Validation · PostgreSQL 16 ·
JUnit 5, Mockito, Testcontainers, WireMock, JaCoCo · HTML/CSS/JS para o painel.

## Executar localmente

Pré-requisitos: JDK 21 e Docker Desktop.

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

No PowerShell: `.\mvnw.cmd spring-boot:run`. Acesse http://localhost:8080.
Banco padrão: `reservas_academicas`, usuário e senha `postgres`, porta `5432`
(variáveis `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).

### Usuários de demonstração

| Perfil | Usuário | Senha |
|---|---|---|
| Administrador | `admin` | `Admin123!` |
| Responsável | `responsavel` | `Resp123!` |
| Solicitante | `professor` | `Prof123!` |

Credenciais fixas, **somente para demonstração acadêmica**. Não usar em produção.

## Testes e cobertura

```bash
./mvnw test      # unitários (Surefire, classes *Test) — não precisa de Docker
./mvnw verify    # unitários + integração (Failsafe, classes *IT, exige Docker) + JaCoCo + gate de cobertura
```

Relatório de cobertura: `target/site/jacoco/index.html`.
O build **falha** se a cobertura ficar abaixo de 80% de linhas ou 70% de ramos.

| Nível | Classes | Banco real? |
|---|---|---|
| Unitário | `ReservaServiceTest`, `ReservaServiceRegrasTest`, `ManutencaoServiceTest`, controllers, handler, `NotificacaoWebhookTest`... | Não |
| Integração | `ConflitosRepositoryIT`, `RestricaoExclusaoBancoIT` | PostgreSQL (Testcontainers) |
| Concorrência | `ConcorrenciaReservaIT` | PostgreSQL (Testcontainers) |
| API caixa-preta / E2E | `AutorizacaoApiIT`, `RegrasDeNegocioApiIT`, `FluxoReservaApiIT`, `ManutencaoApiIT` | Sim |
| Integração externa | `NotificacaoWebhookWireMockIT` | WireMock |

Detalhes em `docs/PLANO_DE_TESTES.md`.

## Funcionalidades

- Login HTTP Basic e autorização por papéis (ADMIN, RESPONSAVEL, SOLICITANTE) e por titularidade da reserva.
- CRUD administrativo de salas, professores e materiais; pesquisa por tipo, capacidade, localização e competência.
- Reservas: solicitação, alteração, aprovação, rejeição, início, conclusão, cancelamento e não comparecimento.
- Detecção de sobreposição de sala, professor e material; **proteção contra dupla reserva** (lock pessimista + restrição de exclusão no PostgreSQL).
- **Manutenção programada** de salas e materiais por período.
- Registro de retirada e devolução de material; histórico de auditoria; notificação simulada e por **webhook** opcional.
- Relatório de utilização: por sala, professor e material, carga horária e **conflitos evitados**.
- Painel web responsivo e mensagens de erro padronizadas (JSON).

API completa em [`docs/API.md`](docs/API.md).

## Regras principais

1. Término posterior ao início; início não pode estar no passado.
2. Sem sobreposição para a mesma sala, professor ou material em reservas não finais; intervalos adjacentes são permitidos.
3. Sala/professor inativo, recurso em manutenção (imediata ou programada) ou material indisponível não podem ser reservados.
4. Material restrito gera `SOLICITADA` e exige aprovação de responsável; os demais são aprovados automaticamente.
5. `SOLICITADA → APROVADA → EM_USO → CONCLUIDA`; alternativos: `REJEITADA`, `CANCELADA`, `NAO_COMPARECEU`.
6. Mover reserva aprovada de recurso restrito exige nova aprovação.
7. Toda mudança de estado gera auditoria.

## Documentação

| Documento | Conteúdo |
|---|---|
| [`RTM.md`](RTM.md) | Rastreabilidade requisito → risco → teste → evidência |
| [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) | Diagramas C4 (contexto, contêineres, componentes) e sequências críticas |
| [`docs/adr/`](docs/adr) | Decisões de arquitetura (ADRs) |
| [`docs/PLANO_DE_TESTES.md`](docs/PLANO_DE_TESTES.md) | Estratégia, níveis, critérios de saída |
| [`docs/SEGURANCA.md`](docs/SEGURANCA.md) | Checklist e mapeamento OWASP |
| [`docs/REGISTRO_DE_DEFEITOS.md`](docs/REGISTRO_DE_DEFEITOS.md) | Defeitos encontrados, priorizados e corrigidos |
| [`docs/TDD_BDD.md`](docs/TDD_BDD.md) | Cenários BDD e protocolo de evidência de TDD |
| [`docs/RELATORIO_FINAL_QUALIDADE.md`](docs/RELATORIO_FINAL_QUALIDADE.md) | Relatório final e decisão de qualidade |
| [`performance/README.md`](performance/README.md) | Plano e resultado do teste de carga |
| [`docs/evidencias/`](docs/evidencias) | Saídas brutas das execuções |

## CI e SonarCloud

O pipeline (`.github/workflows/ci.yml`) roda a cada push e pull request:
`./mvnw clean verify` (testes unitários e de integração, JaCoCo e gate de cobertura), publica os relatórios
e, se o secret `SONAR_TOKEN` existir, executa a análise do SonarCloud.

**Configurar o SonarCloud (uma vez):**

1. Em `sonarcloud.io`, entre com o GitHub e use **+ → Analyze new project** no repositório.
2. Escolha análise via **GitHub Actions** e **desligue a "Automatic Analysis"** (ela não lê a cobertura).
3. Anote a *organization key* e a *project key*; gere um token em **My Account → Security**.
4. No GitHub: **Settings → Secrets and variables → Actions → New repository secret**, nome `SONAR_TOKEN`.
5. No `pom.xml`, troque `sonar.organization` e `sonar.projectKey` pelos valores do passo 3.
6. Faça um push ou abra um pull request e guarde capturas do painel em `docs/evidencias/sonarcloud/`.

## Publicar no GitHub

```bash
git init -b main
git add .
git commit -m "feat: ReservaHub - projeto de Qualidade de Software"
git remote add origin https://github.com/SEU_USUARIO/reservas-academicas.git
git push -u origin main
```

No Windows, garanta a permissão de execução do wrapper: `git update-index --chmod=+x mvnw`
(o CI também executa `chmod +x mvnw`). Para a evidência de TDD e a revisão por pares, trabalhe com
branches e pull requests (ver `docs/TDD_BDD.md`).

## Uso de IA generativa

Este projeto contou com apoio de IA generativa (Claude, da Anthropic) na revisão de qualidade, na correção de
defeitos, na escrita de testes e da documentação. A autoria e a responsabilidade pelo projeto são de
Murilo Henrique Santana Rodrigues. *(Ajuste este parágrafo para descrever, com suas palavras, o que você
revisou, entendeu e decidiu.)*

## Limitações conhecidas

Usuários e senhas fixos em memória; entidades JPA usadas como corpo das requisições (mitigado, ver
`docs/REGISTRO_DE_DEFEITOS.md`); CSRF desabilitado com HTTP Basic; relatório sem paginação; webhook síncrono sem
retentativa; sem teste de UI; **HTTP Basic com BCrypt a cada requisição limita a vazão** (ver
`performance/README.md`); painel web sem tela para manutenção programada. Lista completa em
`docs/RELATORIO_FINAL_QUALIDADE.md`.
