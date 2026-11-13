# ADR 0004 — Autenticação HTTP Basic para o escopo acadêmico

- **Status:** aceita para a entrega acadêmica; **a revisar para produção**
- **Contexto:** o projeto precisa demonstrar autenticação e autorização por perfil com baixa complexidade.
- **Decisão:** HTTP Basic com usuários em memória e senhas com BCrypt; CSRF desabilitado (a API recebe credenciais no cabeçalho, sem formulário).
- **Consequências:** simples de testar. **Custo medido:** cada requisição reverifica a senha (BCrypt ≈ 69 ms por verificação em 1 vCPU), o que limita a vazão a ~14 req/s por núcleo (`docs/evidencias/bcrypt_medicao.txt`). Navegadores reenviam credenciais Basic, então o CSRF desabilitado é um risco residual. Usuários não são persistidos.
- **Evolução recomendada:** login que emite token (JWT/OAuth) ou sessão, usuários em banco, CSRF coerente com o mecanismo escolhido.
