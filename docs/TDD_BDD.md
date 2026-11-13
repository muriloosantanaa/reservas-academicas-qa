# TDD e BDD

O enunciado exige "ao menos uma funcionalidade nova desenvolvida com evidências da técnica".
Este documento separa, com honestidade, **o que é BDD já entregue** e **o que é evidência de TDD que o autor precisa produzir**.

## 1. BDD — manutenção programada (entregue)

A funcionalidade foi especificada por cenários (Dado / Quando / Então) e automatizada em
`ManutencaoApiIT` (caixa-preta) e `ManutencaoServiceTest` (unitário). Os `@DisplayName` seguem os cenários.

```gherkin
Funcionalidade: Manutenção programada de salas e materiais

  Cenário 1: Reserva dentro do período de manutenção é bloqueada
    Dado uma sala com manutenção agendada de D+100 08h a D+102 18h
    Quando um solicitante reserva a sala em D+101
    Então o sistema responde 409 com "manutenção programada"
    E reservar em D+103 é aceito (200)

  Cenário 2: Material em manutenção não pode ser reservado
  Cenário 3: Não se agenda manutenção sobre reservas ativas (409)
  Cenário 4: Cancelar a manutenção libera o período
  Cenário 5: Manutenção adjacente à reserva (fim = início) não conflita
  Cenário 6: Validações — professor não entra em manutenção; período invertido (409); recurso inexistente (404); corpo vazio (400)
  Cenário 7: Qualquer usuário consulta; somente o administrador agenda e cancela (403 para os demais)
```

> **Atenção:** esta funcionalidade foi escrita junto com os testes, **sem ciclo RED/GREEN registrado**.
> Ela é exemplo de **BDD (especificação por exemplos)**, e **não** deve ser apresentada como evidência de TDD.

## 2. TDD — duração máxima de reserva (a fazer por você)

**Regra:** uma reserva não pode durar mais de 8 horas.

```gherkin
Funcionalidade: Duração máxima de uma reserva

  Cenário: Reserva exatamente no limite é aceita
    Dado uma sala e um professor disponíveis
    Quando o solicitante reserva por exatamente 8 horas
    Então a reserva é criada

  Esquema do Cenário: Reserva acima do limite é recusada
    Dado uma sala e um professor disponíveis
    Quando o solicitante reserva por <minutos> minutos
    Então o sistema responde 409 com "A reserva não pode durar mais de 8 horas"

    Exemplos:
      | minutos |
      | 481     |
      | 600     |
      | 1440    |
```

O ciclo foi **simulado em uma cópia do projeto** para validar o roteiro: no RED, 3 testes falham (os recusados) e o do
limite exato já passa; no GREEN a suíte inteira fica verde. Isso valida o roteiro, **não** substitui os seus commits.

### Protocolo (PowerShell; no Linux/macOS use `./mvnw` e `tee`)

**0. Preparação**
```
git checkout -b feature/duracao-maxima
mkdir docs\evidencias\tdd
```

**1. RED** — em `ReservaServiceRegrasTest` acrescente:

```java
@ParameterizedTest(name = "reserva de {0} minutos é recusada")
@CsvSource({"481", "600", "1440"})
void duracaoAcimaDoLimiteERecusada(long minutos) {
    Reserva entrada = novaReserva(false);
    entrada.setFim(entrada.getInicio().plusMinutes(minutos));

    RegraNegocioException erro = assertThrows(
        RegraNegocioException.class,
        () -> service.criar(entrada, "docente")
    );
    assertEquals("A reserva não pode durar mais de 8 horas", erro.getMessage());
}

@Test
void duracaoExatamenteNoLimiteEAceita() {
    prepararRecursosSemConflito();
    Reserva entrada = novaReserva(false);
    entrada.setFim(entrada.getInicio().plusHours(8));
    when(reservas.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

    assertEquals(StatusReserva.APROVADA, service.criar(entrada, "docente").getStatus());
}
```

Rode e **veja falhar**, guarde a saída e faça o commit:
```
.\mvnw.cmd test "-Dtest=ReservaServiceRegrasTest" | Tee-Object -FilePath docs\evidencias\tdd\01-red.txt
git add -A
git commit -m "test(RED): reserva acima de 8h deve ser recusada"
git push -u origin feature/duracao-maxima
```
O `push` faz o GitHub Actions **falhar** neste commit: guarde a captura da execução vermelha.

**2. GREEN** — em `ReservaService.validarPeriodo`, logo após a checagem de término:
```java
if (Duration.between(inicio, fim).compareTo(Duration.ofHours(8)) > 0) {
    throw new RegraNegocioException("A reserva não pode durar mais de 8 horas");
}
```
(importe `java.time.Duration`). Rode **a suíte inteira**:
```
.\mvnw.cmd test | Tee-Object -FilePath docs\evidencias\tdd\02-green.txt
git add -A
git commit -m "feat(GREEN): recusa reserva com duração acima de 8h"
git push
```

**3. REFACTOR** — extraia a constante e o método, sem mudar o comportamento:
```java
private static final Duration DURACAO_MAXIMA = Duration.ofHours(8);

private void exigirDuracaoPermitida(LocalDateTime inicio, LocalDateTime fim) {
    if (Duration.between(inicio, fim).compareTo(DURACAO_MAXIMA) > 0) {
        throw new RegraNegocioException(
            "A reserva não pode durar mais de " + DURACAO_MAXIMA.toHours() + " horas"
        );
    }
}
```
```
.\mvnw.cmd test | Tee-Object -FilePath docs\evidencias\tdd\03-refactor.txt
git add -A
git commit -m "refactor: extrai constante e metodo da regra de duracao"
git log --oneline --graph | Out-File docs\evidencias\tdd\git-log.txt
```

**4. Pull request** — abra o PR e faça o merge como **"Create a merge commit"** ou **"Rebase and merge"**.
**Nunca "Squash and merge"**: o squash funde os commits e apaga a prova do ciclo.

### Registro do ciclo (preencha com os seus dados reais)

| Etapa | Commit (hash) | Data/hora | Resultado do `mvnw test` | Arquivo de evidência |
|---|---|---|---|---|
| RED | _preencher_ | _preencher_ | _3 falhas esperadas_ | `docs/evidencias/tdd/01-red.txt` |
| GREEN | _preencher_ | _preencher_ | _0 falhas_ | `docs/evidencias/tdd/02-green.txt` |
| REFACTOR | _preencher_ | _preencher_ | _0 falhas_ | `docs/evidencias/tdd/03-refactor.txt` |

Anexe também a captura do CI vermelho (RED) e verde (GREEN/REFACTOR) e o link do pull request.
