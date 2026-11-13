# Matriz de Rastreabilidade de Requisitos (RTM)

Liga **requisito → risco → implementação → teste → evidência**.

**Legenda de evidência**

- **V** — Verificado: executado e aprovado em ambiente de desenvolvimento (saídas em `docs/evidencias/`).
- **C** — Confirmar: o teste existe e passou lá, mas precisa rodar no *seu* ambiente (`./mvnw verify`) e/ou no CI.
- **P** — Pendente: depende de uma ação sua (executar JMeter/SonarCloud, fazer commits de TDD).

Um requisito só é dado como coberto com teste **e** resultado de execução. O que não tem execução fica **P**.

## Requisitos funcionais

| ID | Requisito | Risco se falhar | Implementação | Testes | Ev. |
|---|---|---|---|---|---|
| RF01 | Autenticação e autorização por perfil | Acesso indevido; **limitador de nota** | `SecurityConfig`, `ReservaService.exigirDonoOuResponsavel` | `AutorizacaoApiIT` (matriz perfil × endpoint, titularidade), `ReservaServiceRegrasTest` | V/C |
| RF02 | Cadastro e consulta de salas, professores e materiais | Dados inconsistentes | `CatalogoController/Service` | `CatalogoServiceTest`, `CatalogoControllerTest`, `RegrasDeNegocioApiIT` | V/C |
| RF03 | Pesquisa por tipo, capacidade, localização, competência e disponibilidade | Busca incorreta | `/api/*/pesquisa`, repositories | `CatalogoServiceTest` | V |
| RF04 | Criar, alterar e cancelar reservas | Reserva perdida ou inválida | `ReservaService.criar/alterar/mudarStatus` | `ReservaServiceTest`, `ReservaServiceRegrasTest`, `FluxoReservaApiIT` | V/C |
| RF05 | Sem sobreposição de sala, professor e material | Aula duplicada | `ReservaRepository` (consultas JPQL) + `checarConflitos` | `ConflitosRepositoryIT` (valores limite, PostgreSQL real), `RegrasDeNegocioApiIT` | V/C |
| RF06 | Proteção contra dupla reserva simultânea | Dupla reserva; **limitador de nota** | Lock pessimista + `RestricoesPostgres` (EXCLUDE) | `ConcorrenciaReservaIT`, `RestricaoExclusaoBancoIT`; `docs/evidencias/concorrencia_postgres16.txt` | V/C |
| RF07 | Aprovação obrigatória para recursos restritos | Burlar aprovação | Status inicial `SOLICITADA`; reaprovação ao mover | `FluxoReservaApiIT`, `ReservaServiceRegrasTest` (matriz de 49 transições) | V/C |
| RF08 | Bloqueio de recursos em manutenção | Reservar recurso indisponível | Flag `emManutencao` + `ManutencaoService` (períodos) | `ManutencaoServiceTest`, `ManutencaoApiIT`, `RegrasDeNegocioApiIT` | V/C |
| RF09 | Retirada e devolução de materiais | Perda de custódia | `registrarRetirada/Devolucao` | `ReservaServiceTest`, `FluxoReservaApiIT` | V/C |
| RF10 | Histórico auditável de mudanças | Perda de rastreabilidade | `Auditoria` + `auditar(...)` | `FluxoReservaApiIT` (verifica cada ação auditada) | V/C |
| RF11 | Notificação simulada ou integração externa | Usuário não informado | `NotificacaoService` (persistida + webhook) | `NotificacaoWebhookTest`, `NotificacaoWebhookWireMockIT` | V/C |
| RF12 | Relatório: utilização por recurso, carga horária e conflitos evitados | Gestão sem dados | `RelatorioController`, `ConflitoEvitado` | `RelatorioControllerTest`, `RelatorioConflitosTest`, `ConcorrenciaReservaIT`, `FluxoReservaApiIT` | V/C |
| RF13 | Interface responsiva e erros compreensíveis | Usabilidade | `static/`, `ApiExceptionHandler` | `ApiExceptionHandlerTest`, `ApiExceptionHandlerHttpTest`, `RegrasDeNegocioApiIT` (a UI não tem teste automatizado) | V/C |
| RF14 | Documentação de API e fluxos | Uso incorreto | `README.md`, `docs/API.md`, `docs/ARQUITETURA.md` | Revisão | V |

## Regras críticas

| ID | Regra | Teste | Ev. |
|---|---|---|---|
| RC01 | Término posterior ao início | `ReservaServiceRegrasTest` (parametrizado), `RegrasDeNegocioApiIT` | V/C |
| RC02 | Reservas do mesmo recurso não se sobrepõem (sala, material, professor) | `ConflitosRepositoryIT`, `RegrasDeNegocioApiIT` | V/C |
| RC03 | Duas solicitações simultâneas → uma reserva aceita | `ConcorrenciaReservaIT` | V/C |
| RC04 | Recurso em manutenção não pode ser reservado | `ManutencaoApiIT`, `RegrasDeNegocioApiIT` | V/C |
| RC05 | Somente responsáveis aprovam recursos restritos | `AutorizacaoApiIT`, `FluxoReservaApiIT` | V/C |
| RC06 | Reservas iniciadas não podem ser apagadas | `FluxoReservaApiIT` (não há exclusão; `DELETE` → 405; cancelar em uso → 409) | V/C |
| RC07 | Toda mudança de estado gera auditoria | `FluxoReservaApiIT`, `ReservaServiceRegrasTest` | V/C |

## Estratégia obrigatória de qualidade

| ID | Requisito | Situação | Ev. |
|---|---|---|---|
| Q01 | JUnit 5, parametrizados, integração, API caixa-preta, E2E | Implementado (ver `docs/PLANO_DE_TESTES.md`). E2E é de **API** (sem teste de UI) | V/C |
| Q02 | Testcontainers e WireMock | `PostgresContainerBase` (Testcontainers 1.21.4) e `NotificacaoWebhookWireMockIT` | C |
| Q03 | Teste automatizado de concorrência | `ConcorrenciaReservaIT` | V/C |
| Q04 | TDD ou BDD em ao menos uma funcionalidade | BDD: `ManutencaoApiIT`, `ManutencaoServiceTest`. **TDD: protocolo em `docs/TDD_BDD.md`; os commits são do autor** | P |
| Q05 | GitHub Actions em PR, JaCoCo, SonarCloud | `ci.yml` + JaCoCo com gate 80/70 prontos; **falta o 1º run verde e o SonarCloud** | P |
| Q06 | JMeter, autorização, validação, tratamento seguro de erros | Plano `performance/reservas-carga.jmx` **a executar**; autorização e erros: V/C | P/V |

## Metas mensuráveis

| Meta | Resultado | Ev. |
|---|---|---|
| Linhas ≥ 80% | 97,3% | V (confirmar no CI) |
| Ramos ≥ 70% | 90,9% | V (confirmar no CI) |
| 100% dos requisitos críticos na RTM | Todos mapeados acima | V |
| 0 bugs/vulnerabilidades críticas | Sonar não executado | P |
