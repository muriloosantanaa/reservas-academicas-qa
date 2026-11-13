# Evidências

Saídas brutas das execuções que sustentam o `RTM.md` e o `RELATORIO_FINAL_QUALIDADE.md`.
Foram geradas em ambiente de desenvolvimento (JDK 21, PostgreSQL 16.15, 1 vCPU), **não** pelo Maven/CI.

| Arquivo | O que mostra |
|---|---|
| `concorrencia_postgres16.txt` | 20 requisições simultâneas → 1 aceita (sala, professor, material); 10 adjacentes → 10 aceitas |
| `defeitos_versao_original.txt` | Comportamento da versão **original** (DEF-01 a DEF-05 reproduzidos) |
| `validacao_funcional_nova_versao.txt` | A versão corrigida: 25 verificações de segurança, erros e regras |
| `testes_execucao_local.txt` | Resumo: 195 unitários e 104 de integração aprovados |
| `cobertura_jacoco_unit_mais_integracao.txt` | Cobertura por classe e total (97,3% linhas, 90,9% ramos) |
| `carga_python_resultado.md` | Latência, vazão e invariantes do script de carga |
| `bcrypt_medicao.txt` | Custo da verificação BCrypt (69 ms) — causa da vazão limitada |

**Adicione aqui** as suas evidências reais: saída de `./mvnw verify`, capturas do GitHub Actions e do SonarCloud
(`sonarcloud/`), resultado do JMeter e os arquivos de TDD (`tdd/`).
