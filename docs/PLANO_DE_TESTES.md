# Plano de testes

## Objetivo e escopo

Demonstrar com evidências objetivas que o ReservaHub cumpre as regras críticas (sem sobreposição, sem dupla
reserva, aprovação por responsável, auditoria), é seguro por perfil e se comporta de forma robusta.
Fora de escopo: teste de interface (UI) automatizado e testes de usabilidade.

## Princípio

Mocks apenas em testes unitários isolados. **Persistência e integrações críticas são verificadas com ambiente
real** (PostgreSQL em container, servidor HTTP simulado com WireMock).

## Níveis, ferramentas e arquivos

| Nível | Técnica | Ferramentas | Arquivos |
|---|---|---|---|
| Unitário | Caixa-branca, cobertura de ramos | JUnit 5, Mockito (strict stubs) | `ReservaServiceTest`, `ReservaServiceRegrasTest`, `ManutencaoServiceTest`, `CatalogoServiceTest`, `NotificacaoServiceTest`, controllers, `ApiExceptionHandler*Test`, `RestricoesPostgresTest`, `RelatorioConflitosTest`, `StatusReservaTest` |
| Unitário com HTTP real | Servidor do JDK | `com.sun.net.httpserver` | `NotificacaoWebhookTest` |
| Parametrizado | Classes de equivalência e valores limite | `@ParameterizedTest` | matriz de 49 transições, períodos inválidos, `ConflitosRepositoryIT`, matriz perfil × endpoint |
| Integração com banco | Consultas e restrições em PostgreSQL real | Testcontainers 1.21.4 | `ConflitosRepositoryIT`, `RestricaoExclusaoBancoIT` |
| Concorrência | N requisições simultâneas, invariante de não sobreposição | `ExecutorService` + `CyclicBarrier` | `ConcorrenciaReservaIT` |
| API caixa-preta | Contrato HTTP, autorização, erros | `HttpClient` do JDK + Jackson | `AutorizacaoApiIT`, `RegrasDeNegocioApiIT`, `ManutencaoApiIT` |
| Ponta a ponta (API) | Ciclo completo com auditoria e relatório | idem | `FluxoReservaApiIT` |
| Integração externa | Serviço simulado | WireMock | `NotificacaoWebhookWireMockIT` |
| Carga | Latência, vazão e concorrência sob carga | JMeter + script Python | `performance/` |
| Estático | Bugs, vulnerabilidades, code smells | SonarCloud | CI |

Convenção: classes `*Test` rodam no Surefire (`mvn test`); classes `*IT` rodam no Failsafe (`mvn verify`, exige Docker).

## Dados de teste

Cada teste cria seus próprios recursos (nome e e-mail com UUID) e usa datas futuras distintas, para que a
ordem de execução não interfira nos resultados.

## Critérios de entrada e saída

- **Entrada:** código compila; Docker disponível para os `*IT`.
- **Saída:** todos os testes passam; cobertura ≥ 80% de linhas e ≥ 70% de ramos (o build falha abaixo disso); nenhum bug ou vulnerabilidade crítica no SonarCloud; invariante de concorrência comprovada; evidências guardadas em `docs/evidencias/`.

## Riscos e testes que os cobrem

| Risco | Probabilidade | Impacto | Teste |
|---|---|---|---|
| Dupla reserva sob concorrência | Média | Crítico | `ConcorrenciaReservaIT`, `RestricaoExclusaoBancoIT` |
| Falha de autorização / acesso a reserva alheia | Média | Crítico | `AutorizacaoApiIT` |
| Sobreposição não detectada (limite de intervalo) | Média | Alto | `ConflitosRepositoryIT` |
| Transição de estado inválida | Média | Alto | matriz de 49 transições |
| Vazamento de detalhes internos em erros | Baixa | Médio | `RegrasDeNegocioApiIT`, `ApiExceptionHandlerHttpTest` |
| Falha da integração externa derrubar a reserva | Média | Médio | `NotificacaoWebhookTest`, `NotificacaoWebhookWireMockIT` |
| Desempenho insuficiente sob carga | Média | Médio | `performance/` |

## Como executar

```bash
./mvnw test                 # unitários
./mvnw verify               # tudo + cobertura + gate (Docker)
python3 performance/carga_reservas.py   # carga e concorrência contra a aplicação no ar
```

## Estado

Ver `RTM.md` (legenda V/C/P) e `docs/RELATORIO_FINAL_QUALIDADE.md`.
