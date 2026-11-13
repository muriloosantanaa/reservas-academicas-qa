# Segurança

## Checklist

- [x] Autenticação obrigatória em todas as rotas, exceto `/api/health` e arquivos estáticos.
- [x] Autorização por perfil por URL (`SecurityConfig`) **e** por objeto (titularidade da reserva).
- [x] Negação de permissão responde **403** (corrigido: antes era 409). Não autenticado: 401.
- [x] Senhas de demonstração com BCrypt.
- [x] Validação de entrada (Bean Validation nas entidades, `@Valid` nos controllers).
- [x] Campos de controle vindos do cliente ignorados (`id`, `retiradaEm`, `devolvidaEm`) — corrige DEF-01.
- [x] Erros sem stack trace, SQL ou nomes de classe; `server.error.include-*=never`; 500 só registra log.
- [x] Webhook de notificação: URL vem de configuração do operador (não de entrada do usuário); timeout; falha isolada.
- [ ] Usuários persistidos, troca e recuperação de senha.
- [ ] Segredos fora do código; HTTPS obrigatório em ambiente implantado.
- [ ] Autenticação por token e política de CSRF coerente; limite de tentativas (rate limiting).
- [ ] Revisão dos achados do SonarCloud e verificação de dependências (SCA).

## Mapeamento com o OWASP API Security Top 10 (2023)

| Risco | Situação no projeto |
|---|---|
| API1 — Autorização quebrada no nível do objeto (BOLA/IDOR) | Mitigado: `exigirDonoOuResponsavel`; testado em `AutorizacaoApiIT` |
| API2 — Autenticação quebrada | Parcial: Basic + BCrypt; sem bloqueio de tentativas |
| API3 — Autorização quebrada no nível da propriedade (mass assignment) | **Corrigido** (DEF-01); melhoria: DTOs |
| API4 — Consumo irrestrito de recursos | Parcial: relatório sem paginação; sem rate limiting |
| API5 — Autorização quebrada no nível da função | Mitigado: regras por URL + serviço; matriz em `AutorizacaoApiIT` |
| API7 — Falsificação de requisição no servidor (SSRF) | Baixo: o webhook usa URL de configuração, nunca de entrada do usuário |
| API8 — Configuração incorreta de segurança | Parcial: erros sem detalhes; CSRF desabilitado e senhas fixas (demonstração) |
| API10 — Consumo inseguro de APIs | Mitigado: timeout, falha isolada, nenhuma resposta externa é confiada |

## Achados esperados no SonarCloud (e justificativa)

| Achado | Justificativa |
|---|---|
| Desabilitar CSRF (`csrf.disable()`) | API com credenciais no cabeçalho `Authorization`; risco residual com Basic no navegador. Em produção: token ou CSRF ativo |
| Credenciais fixas no código | Usuários de demonstração acadêmica; em produção vão para banco/variáveis de ambiente |

Cada achado deve ser **corrigido** ou marcado com justificativa; nunca ignorado em silêncio.
