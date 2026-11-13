# Relatório final de qualidade

**Projeto:** ReservaHub — Reservas Acadêmicas · **Autor:** Murilo Henrique Santana Rodrigues

## 1. Resumo e decisão de qualidade

O núcleo funcional estava correto, mas a versão original tinha **uma vulnerabilidade crítica** (sobrescrita de
reserva alheia por `id`), respondia 409 no lugar de 403, devolvia 500 para erros de cliente e **não tinha nenhuma
prova automatizada da proteção contra dupla reserva em banco real**. Esses pontos foram corrigidos e provados.

**Decisão:** o sistema está **apto como release candidate** para a entrega acadêmica, **condicionado** a:
(1) rodar `./mvnw verify` com Docker e anexar a saída; (2) um run verde no GitHub Actions; (3) executar o
SonarCloud e triar os achados; (4) executar o plano JMeter; (5) registrar o ciclo de TDD com seus commits.
Os itens 1–5 dependem de ações do autor (ver seção 7).

## 2. Metas do enunciado

| Meta | Alvo | Resultado | Status |
|---|---|---|---|
| Cobertura de linhas | ≥ 80% | 97,3% (756/777) | Atingida¹ |
| Cobertura de ramos | ≥ 70% | 90,9% (200/220) | Atingida¹ |
| Requisitos críticos na RTM | 100% | 100% (RF01–RF14, RC01–RC07) | Atingida |
| Bugs/vulnerabilidades críticas | 0 | Não medido (SonarCloud não executado) | **Pendente** |
| Dupla reserva impossível | 1 aceita | 1 aceita em 20 e em 50 simultâneas (PostgreSQL 16 real) | Atingida¹ |

¹ Medido com JaCoCo 0.8.11 em ambiente de desenvolvimento (unitários + integração). Confirmar com `./mvnw verify`
(JaCoCo 0.8.13), que também impõe o gate de 80/70 no build.

**Cobertura não é qualidade.** A suíte original tinha 93% de instruções, quase tudo com mocks: consultas de
conflito e locks nunca rodavam em banco real. A confiança vem de testes que **falhariam se a regra quebrasse**
(teste de concorrência, valores limite em PostgreSQL real, invariante de não sobreposição).

## 3. Testes executados

| Suíte | Quantidade | Resultado | Onde |
|---|---|---|---|
| Unitários (`*Test`) | 195 (+3 de contexto Spring/H2 a rodar no Maven) | 195 aprovados | `docs/evidencias/testes_execucao_local.txt` |
| Integração e API (`*IT`, 8 classes) | 104 | 104 aprovados | idem |
| Concorrência (script HTTP) | 4 cenários | invariantes OK | `docs/evidencias/concorrencia_postgres16.txt` |
| Carga e concorrência (Python) | 3 cenários | 0 erros 5xx; invariantes OK | `docs/evidencias/carga_python_resultado.md` |

Os `*IT` foram executados com PostgreSQL 16 local e substitutos locais de Spring Test/Testcontainers/WireMock
(`testes_execucao_local.txt`). **Rode `./mvnw verify` no seu ambiente** para a execução oficial.

## 4. Defeitos

Nove defeitos registrados (1 crítico, 3 altos, 4 médios, 1 baixo), todos corrigidos; cinco **reproduzidos** na
versão original e na corrigida. Detalhes em [`REGISTRO_DE_DEFEITOS.md`](REGISTRO_DE_DEFEITOS.md).

## 5. Desempenho

| Cenário | Resultado (1 vCPU) |
|---|---|
| Leitura autenticada, 20 usuários × 25 | 12,4 req/s; p50 1,5 s; p95 1,8 s; 0 erros |
| 50 reservas simultâneas, mesmo recurso | 1 aceita, 49 × 409; 0 erros 5xx |
| 50 reservas simultâneas, recursos diferentes | 50/50 aceitas; 0 erros 5xx |

**Gargalo identificado e medido:** a autenticação. HTTP Basic reverifica a senha a cada requisição e o BCrypt
(custo 10) leva ≈ 69 ms: teto de ~14 req/s por núcleo (`bcrypt_medicao.txt`). A vazão **não é limitada pelas
reservas nem pelo banco**. Em máquina com mais núcleos a vazão cresce proporcionalmente. Evolução: token/sessão
(ADR 0004). O plano JMeter (`performance/reservas-carga.jmx`) deve ser **executado por você** e seu resultado anexado.

## 6. Riscos e dívida técnica remanescente

| Item | Risco | Plano |
|---|---|---|
| Usuários e senhas fixos em memória | Alto em produção | Banco + hash + política de senha |
| Autenticação Basic a cada requisição | Desempenho e CSRF residual | Token (JWT/OAuth) ou sessão |
| Entidades JPA como corpo das requisições | Mass assignment (mitigado) | DTOs |
| Relatório sem paginação, carrega tudo em memória | Desempenho com muitos dados | Consultas agregadas |
| Webhook síncrono sem retentativa | Notificação perdida | Padrão outbox |
| Sem teste de UI | Regressão visual | Playwright |
| Painel sem tela de manutenção programada | Funcionalidade só via API | Tela no painel |
| Sem teste de mutação | Força dos testes não medida | PIT |

## 7. O que depende do autor (antes da entrega)

1. `./mvnw verify` (com Docker) e guardar a saída em `docs/evidencias/`.
2. Publicar no GitHub e obter um run **verde** do Actions.
3. Configurar o SonarCloud, tratar os achados e guardar capturas em `docs/evidencias/sonarcloud/`.
4. Executar `performance/reservas-carga.jmx` e anexar o resultado (ou citar o resultado do script Python como complementar).
5. Fazer o ciclo de TDD (`docs/TDD_BDD.md`) com commits próprios.
6. Ajustar o parágrafo "Uso de IA" do README com suas palavras e estudar o `Guia de estudo` para a prova oral.

## 8. Lições aprendidas

- Provar a propriedade (uma reserva aceita) com concorrência real vale mais que muitos testes unitários.
- Testes com mocks não substituem banco real: as consultas de conflito eram o ponto mais crítico e estavam sem prova.
- Comparar a versão original e a corrigida lado a lado deu evidência objetiva dos defeitos.
- Medir antes de concluir: a vazão baixa parecia problema de reservas, e a medição mostrou ser a autenticação.
