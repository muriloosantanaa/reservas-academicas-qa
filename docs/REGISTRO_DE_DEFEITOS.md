# Registro de defeitos

Defeitos encontrados na revisão de qualidade da versão original, priorizados e corrigidos.
**Severidade:** Crítica = permite violar dados ou segurança de outro usuário; Alta = quebra regra obrigatória do enunciado
ou o pipeline; Média = resposta incorreta sem corromper dados; Baixa = documentação/forma.

**Comprovação:** *Reproduzido* = a versão original e a corrigida foram executadas lado a lado contra o mesmo banco
(`docs/evidencias/defeitos_versao_original.txt` e `validacao_funcional_nova_versao.txt`). *Inspeção* = identificado
por leitura do código ou por fonte externa, **não reproduzido por execução**.

| ID | Defeito | Sev. | Comprovação | Correção | Teste de regressão | Estado |
|---|---|---|---|---|---|---|
| DEF-01 | `POST /api/reservas` com o `id` de outra reserva a **sobrescrevia** (finalidade, sala e dono) | Crítica | Reproduzido: reserva alheia ficou com finalidade "ataque" e o dono trocado | `ReservaService.criar` zera `id`, `retiradaEm`, `devolvidaEm` | `RegrasDeNegocioApiIT.idDoClienteNaoSobrescreveOutraReserva`, `ReservaServiceRegrasTest.criarIgnoraCamposDeControleDoCliente` | Corrigido |
| DEF-02 | Negação de permissão no serviço respondia **409** (cancelar reserva alheia, ler histórico alheio) | Alta | Reproduzido (409 em vez de 403) | `AcessoNegadoException` + handler 403 | `AutorizacaoApiIT`, `ApiExceptionHandlerHttpTest` | Corrigido |
| DEF-03 | Erros de cliente respondiam **500**: JSON malformado, `text/plain`, rota inexistente, `DELETE /api/reservas`, id não numérico | Média | Reproduzido | Handlers 400/404/405/415 | `RegrasDeNegocioApiIT.errosDeClienteSaoQuatroXX`, `ApiExceptionHandlerHttpTest` | Corrigido |
| DEF-04 | Reserva **cancelada** podia ser alterada (200) | Média | Reproduzido | `alterar` recusa estados finais | `ReservaServiceRegrasTest`, `FluxoReservaApiIT` | Corrigido |
| DEF-05 | Reserva aprovada de material restrito podia ser **movida sem nova aprovação** | Alta | Reproduzido: status continuou APROVADA | Volta para SOLICITADA se o período mudar | `ReservaServiceRegrasTest` | Corrigido |
| DEF-06 | `alterar` não revalidava sala/professor/material (inativo, em manutenção) | Média | Inspeção | Revalidação após o lock | `ReservaServiceRegrasTest` | Corrigido |
| DEF-07 | Testcontainers 1.21.3 incompatível com Docker 29 dos runners do GitHub (`client version 1.32 is too old`) | Alta (CI) | Inspeção: relatos públicos do mesmo erro | Versão 1.21.4 no `pom.xml` | Execução do CI | Corrigido (a confirmar no CI) |
| DEF-08 | Excluir sala com reservas gerava erro de integridade tratado como 500 | Média | Inspeção | `DataIntegrityViolationException` → 409 | `RegrasDeNegocioApiIT.excluirSalaComReservas` | Corrigido |
| DEF-09 | Documentação desatualizada (24% de cobertura, "testes não executados") | Baixa | Comparação com os relatórios | Documentos reescritos | n/a | Corrigido |

## Achado de desempenho (não é defeito funcional)

| ID | Achado | Evidência | Estado |
|---|---|---|---|
| PERF-01 | HTTP Basic reautentica a cada requisição; BCrypt (custo 10) leva ≈ 69 ms, limitando a vazão a ~14 req/s **por núcleo**. Em leitura autenticada observou-se 12,4 req/s em 1 vCPU | `docs/evidencias/bcrypt_medicao.txt`, `carga_python_resultado.md` | Aberto — evolução: token/sessão (ADR 0004) |

## Triagem

Prioridade de correção seguiu a severidade: DEF-01 (segurança) → DEF-05 e DEF-02 (regras e autorização) →
DEF-07 (pipeline) → demais. Defeitos novos encontrados daqui em diante devem ser registrados nesta tabela
com teste de regressão antes da correção.
