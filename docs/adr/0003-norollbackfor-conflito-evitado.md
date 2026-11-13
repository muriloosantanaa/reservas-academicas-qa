# ADR 0003 — `noRollbackFor` em vez de `REQUIRES_NEW` para registrar conflitos evitados

- **Status:** aceita
- **Contexto:** o relatório deve exibir "conflitos evitados". Lançar a exceção de conflito normalmente desfaz a transação e o registro se perderia.
- **Decisão:** `@Transactional(noRollbackFor = ConflitoHorarioException.class)` em `criar` e `alterar`; o conflito é gravado e a exceção segue para o handler (409). Em `alterar`, a entidade só é modificada depois de todas as validações.
- **Consequências:** uma única conexão por requisição. Exige disciplina: nada que não deva ser confirmado pode ser escrito antes do conflito.
- **Alternativa rejeitada:** `REQUIRES_NEW` — pede uma segunda conexão enquanto a primeira segura o lock; sob carga o pool (10 conexões por padrão) pode esgotar com todas as threads esperando umas pelas outras.
