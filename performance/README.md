# Teste de carga e concorrência

| Arquivo | Para quê |
|---|---|
| `reservas-carga.jmx` | **Plano JMeter**: leitura autenticada + N reservas simultâneas pelo mesmo recurso, com verificação da invariante "exatamente 1 aceita" |
| `carga_reservas.py` | Gerador equivalente em Python (só biblioteca padrão): produz latências, vazão e a mesma invariante |
| `reservas-smoke.jmx` | Smoke test do endpoint público |
| `resultados/ultimo-resultado.md` | Saída do último `carga_reservas.py` |

## JMeter

Pré-requisito: JMeter 5.6.x. A aplicação deve estar no ar (`docker compose up -d postgres` e `./mvnw spring-boot:run`).

```bash
jmeter -n -t performance/reservas-carga.jmx \
  -Jhost=localhost -Jport=8080 -Jleitores=20 -Jiteracoes=25 -Jsimultaneas=50 \
  -l performance/resultados/carga.jtl -e -o performance/resultados/dashboard-jmeter
```

O `-e -o` gera o painel HTML. Os grupos rodam em sequência: setUp (cria sala e professor), cenário 1 (leitura),
cenário 2 (reservas simultâneas, com *Synchronizing Timer*) e tearDown (verifica que exatamente 1 reserva foi aceita; falha o teste se não).

> O plano foi validado quanto à **sintaxe XML**, mas **não foi executado no JMeter** pelo autor do pacote.
> Abra-o no JMeter (File → Open) para conferir antes de rodar e, depois, guarde o `.jtl` e o dashboard.

## Script Python

```bash
python3 performance/carga_reservas.py --base http://localhost:8080 --leitores 20 --leituras 25 --simultaneas 50
```

Retorna código de saída 0 somente se as invariantes forem cumpridas e não houver erro 5xx.

## Resultado medido (script Python, ambiente com 1 vCPU)

| Cenário | Req. | Erros 5xx | Vazão (req/s) | p50 (ms) | p95 (ms) | p99 (ms) |
|---|---|---|---|---|---|---|
| Leitura autenticada: 20 usuários × 25 GET | 500 | 0 | 12,4 | 1522 | 1849 | 3258 |
| 50 reservas simultâneas, mesma sala e horário | 50 | 0 | 9,3 | 4285 | 4684 | 4728 |
| 50 reservas simultâneas, recursos diferentes | 50 | 0 | 10,0 | 3705 | 3999 | 4039 |

Invariantes: mesma sala → **1 aceita e 49 × 409**; recursos diferentes → **50 de 50 aceitas**.

## Interpretação

A vazão baixa **não** vem das reservas: uma leitura simples tem a mesma vazão. A causa medida é a autenticação —
HTTP Basic reverifica o BCrypt (≈ 69 ms em 1 vCPU, `docs/evidencias/bcrypt_medicao.txt`) a cada requisição, o que dá um
teto de ~14 req/s por núcleo. Os números absolutos dependem da máquina; **rode no seu computador** e registre os seus.
Para aumentar a vazão: autenticação por token/sessão (ver `docs/adr/0004-autenticacao-http-basic.md`).
