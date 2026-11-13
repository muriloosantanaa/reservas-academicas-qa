# ADR 0002 — Dupla reserva: lock pessimista + restrição de exclusão

- **Status:** aceita
- **Contexto:** verificar conflito e depois gravar tem uma janela de corrida (TOCTOU). O conflito é entre **inserções** que se sobrepõem, não entre duas atualizações da mesma linha.
- **Decisão:** (1) `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`) nas linhas de sala, professor e material, sempre nessa ordem, dentro da transação; (2) restrições `EXCLUDE USING gist (recurso WITH =, tsrange(inicio, fim) WITH &&) WHERE status NOT IN (finais)` criadas por `RestricoesPostgres`.
- **Consequências:** o lock devolve 409 claro e cedo; a restrição garante a integridade mesmo se algum caminho esquecer o lock. Reservas do mesmo recurso são serializadas. A ordem fixa evita *deadlock*.
- **Evidência:** `ConcorrenciaReservaIT`, `RestricaoExclusaoBancoIT`, `docs/evidencias/concorrencia_postgres16.txt` (20 simultâneas → 1 aceita).
- **Alternativas rejeitadas:** `@Version` (otimista) — não protege inserção; isolamento SERIALIZABLE — exige retentativas e é mais difícil de explicar e testar; apenas a restrição — erro tardio e sem mensagem amigável.
