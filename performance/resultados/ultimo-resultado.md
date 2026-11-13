# Resultado do teste de carga (carga_reservas.py)

Data: 2026-11-13 03:53  |  Alvo: http://localhost:8080

| Cenário | Req. | Erros 5xx | Vazão (req/s) | Média (ms) | p50 | p95 | p99 | Máx |
|---|---|---|---|---|---|---|---|---|
| Leitura autenticada: 20 usuários x 25 GET /api/salas | 500 | 0 | 12.4 | 1582.2 | 1522.4 | 1848.6 | 3257.5 | 3380.6 |
| 50 reservas simultâneas, mesma sala e horário | 50 | 0 | 9.3 | 4295.2 | 4285.2 | 4683.9 | 4727.5 | 4727.5 |
| 50 reservas simultâneas de recursos diferentes | 50 | 0 | 10.0 | 3577.0 | 3704.6 | 3999.3 | 4038.5 | 4038.5 |

Invariantes de concorrência:
- Mesma sala/horário: 1 aceita(s) e 49 recusada(s) com 409; outros códigos: nenhum. Invariante 'exatamente uma aceita': **OK**.
- Recursos diferentes: 50/50 aceitas. Invariante 'todas aceitas': **OK**.
