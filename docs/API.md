# API e fluxos públicos

Base: `http://localhost:8080`. Corpo e resposta em JSON. Autenticação HTTP Basic (exceto `GET /api/health`).

## Erros

Formato: `{ "timestamp", "status", "error", "message" }`. Nunca contém stack trace nem detalhes internos.

| Código | Quando |
|---|---|
| 400 | Validação de campos, JSON malformado, parâmetro inválido |
| 401 | Sem credenciais ou credenciais inválidas |
| 403 | Autenticado sem permissão (perfil ou titularidade) |
| 404 | Recurso ou rota inexistente |
| 405 / 415 | Método HTTP não permitido / `Content-Type` não suportado |
| 409 | Regra de negócio ou conflito (horário ocupado, estado inválido, manutenção, integridade) |
| 500 | Falha inesperada (detalhe apenas no log do servidor) |

## Catálogo

- `GET /api/salas`, `/api/professores`, `/api/materiais` — qualquer usuário autenticado.
- `GET /api/salas/pesquisa?capacidade=30&localizacao=Bloco`, `/api/professores/pesquisa?competencia=Java`, `/api/materiais/pesquisa?tipo=PROJETOR`.
- `POST/PUT/DELETE /api/admin/salas[/{id}]`, `/api/admin/professores[/{id}]`, `/api/admin/materiais[/{id}]` — **ADMIN**.

## Reservas

| Método | Rota | Quem |
|---|---|---|
| GET | `/api/reservas` | Autenticado (solicitante vê só as suas; responsável e admin veem todas) |
| GET | `/api/reservas/{id}` | Dono, responsável, admin |
| POST | `/api/reservas` | Autenticado |
| PUT | `/api/reservas/{id}` | Dono, responsável, admin |
| POST | `/api/reservas/{id}/aprovar`, `/rejeitar`, `/iniciar`, `/concluir`, `/nao-compareceu` | Responsável, admin |
| POST | `/api/reservas/{id}/cancelar` | Dono, responsável, admin |
| POST | `/api/reservas/{id}/retirada`, `/devolucao` | Responsável, admin |
| GET | `/api/reservas/{id}/historico`, `/notificacoes` | Dono, responsável, admin |

Exemplo de corpo para criar reserva (`id`, `status`, `retiradaEm` e `devolvidaEm` enviados pelo cliente são **ignorados**):

```json
{
  "sala": { "id": 1 }, "professor": { "id": 1 }, "material": { "id": 1 },
  "inicio": "2027-02-10T08:00:00", "fim": "2027-02-10T10:00:00",
  "finalidade": "Aula de laboratório"
}
```

Não existe operação de exclusão de reservas: `DELETE /api/reservas/{id}` retorna 405.

## Manutenção programada

| Método | Rota | Quem | Resposta |
|---|---|---|---|
| GET | `/api/manutencoes` | Autenticado | 200 |
| POST | `/api/admin/manutencoes` | ADMIN | 201 |
| DELETE | `/api/admin/manutencoes/{id}` | ADMIN | 204 |

```json
{ "tipoRecurso": "SALA", "recursoId": 1,
  "inicio": "2027-03-01T08:00:00", "fim": "2027-03-03T18:00:00", "motivo": "Pintura" }
```

`tipoRecurso` aceita `SALA` ou `MATERIAL`. Recusa (409) se houver reservas ativas no período.

## Relatório

`GET /api/relatorios/utilizacao` (responsável ou admin) retorna: `totalReservas`, `reservasConcluidas`,
`cargaHorariaAlocada`, `porSala`, `porProfessor`, `porMaterial`, `cargaHorariaPorProfessor`, `porStatus`,
`conflitosEvitados` e `conflitosEvitadosPorRecurso`. "Alocada" considera só reservas efetivas
(exclui REJEITADA, CANCELADA e NAO_COMPARECEU).

## Notificação por webhook (opcional)

Defina `NOTIFICACAO_WEBHOOK_URL` (e, se quiser, `NOTIFICACAO_WEBHOOK_TIMEOUT_MS`, padrão 2000). A cada evento de
reserva, depois do commit, o sistema envia `POST` com `{"reservaId", "destinatario", "mensagem"}`.
Falhas do serviço externo nunca afetam a reserva.
