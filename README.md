# ReservaHub --- Reservas Acadêmicas

Aplicação web e API REST para gestão de reservas de **salas, professores
e materiais** sem conflito de horário.\
Projeto individual da disciplina de Qualidade de Software.

**Autor:** Murilo Henrique Santana Rodrigues

[![quality-checks](https://github.com/muriloosantanaa/reservas-academicas-qa/actions/workflows/ci.yml/badge.svg)](https://github.com/muriloosantanaa/reservas-academicas-qa/actions/workflows/ci.yml)

## Estado da qualidade

  -----------------------------------------------------------------------------------------------
  Meta do enunciado       Resultado               Como foi obtido
  ----------------------- ----------------------- -----------------------------------------------
  Cobertura de linhas ≥   **97,3%**               Percentual registrado nas evidências do projeto
  80%                                             em `docs/evidencias/`; conferir o relatório
                                                  JaCoCo da execução correspondente

  Cobertura de ramos ≥    **90,9%**               Percentual registrado nas evidências do projeto
  70%                                             em `docs/evidencias/`; conferir o relatório
                                                  JaCoCo da execução correspondente

  Testes automatizados    **302 aprovados**: 198  Execução local de `.\mvnw.cmd verify`; zero
                          no Surefire e 104 no    falhas, erros ou testes ignorados
                          Failsafe                

  Dupla reserva           **1 aceita em 50        PostgreSQL 16;
  impossível              simultâneas**           `docs/evidencias/concorrencia_postgres16.txt` e
                                                  `ConcorrenciaReservaIT`

  100% dos requisitos     **Sim, conforme a RTM** `RTM.md`
  críticos na RTM                                 

  0 bugs/vulnerabilidades **Pendente de análise   Configuração descrita em "CI e SonarCloud"
  críticas                no SonarCloud**         

  Pipeline de CI          **Sucesso**             GitHub Actions: workflow `ci.yml`, job `build`
                                                  concluído com sucesso após o push para `main`

  Teste de carga (JMeter) **Plano documentado;    `performance/`
                          execução pendente**     

  Evidência de TDD        **Pendente de           `docs/TDD_BDD.md`
                          comprovação dos ciclos  
                          RED/GREEN/REFACTOR**    
  -----------------------------------------------------------------------------------------------

A execução local de `.\mvnw.cmd verify` terminou com `BUILD SUCCESS`. O
Maven executou 198 testes no Surefire e 104 no Failsafe, totalizando 302
testes aprovados. O JaCoCo também informou que todos os critérios de
cobertura configurados no build foram atendidos.

Os percentuais de cobertura acima são os registrados nas evidências
existentes do projeto. Para consultar os resultados de uma nova
execução, gere o relatório localmente e confira
`target/site/jacoco/index.html`.

## Tecnologias

Java 21 · Spring Boot 4.1.2 · Spring MVC, Data JPA, Security e
Validation · PostgreSQL 16 ·\
JUnit 5, Mockito, Testcontainers, WireMock, JaCoCo · HTML/CSS/JS para o
painel.

## Executar localmente

**Pré-requisitos:** JDK 21 e Docker Desktop.

Inicie o PostgreSQL:

``` bash
docker compose up -d postgres
```

Execute a aplicação:

``` bash
./mvnw spring-boot:run
```

No Windows PowerShell:

``` powershell
.\mvnw.cmd spring-boot:run
```

Acesse: http://localhost:8080.

Banco padrão: `reservas_academicas`, usuário e senha `postgres`, porta
`5432`.\
As configurações podem ser sobrescritas pelas variáveis `DB_URL`,
`DB_USERNAME` e `DB_PASSWORD`.

### Usuários de demonstração

  Perfil          Usuário         Senha
  --------------- --------------- -------------
  Administrador   `admin`         `Admin123!`
  Responsável     `responsavel`   `Resp123!`
  Solicitante     `professor`     `Prof123!`

As credenciais são fixas e destinam-se **somente à demonstração
acadêmica**. Não devem ser utilizadas em produção.

## Testes e cobertura

Execute os testes unitários:

``` bash
./mvnw test
```

No Windows PowerShell:

``` powershell
.\mvnw.cmd test
```

Execute a validação completa, incluindo testes de integração:

``` bash
./mvnw verify
```

No Windows PowerShell:

``` powershell
.\mvnw.cmd verify
```

-   `test`: executa os testes unitários (Surefire, classes `*Test`).
-   `verify`: executa os testes unitários e de integração (Failsafe,
    classes `*IT`), além do JaCoCo e do gate de cobertura. Os testes de
    integração utilizam Docker/Testcontainers.

Relatório de cobertura: `target/site/jacoco/index.html`.

O build falha se a cobertura ficar abaixo de 80% de linhas ou 70% de
ramos.

  --------------------------------------------------------------------------------
  Nível                   Classes / exemplos               Banco real?
  ----------------------- -------------------------------- -----------------------
  Unitário                `ReservaServiceTest`,            Não
                          `ReservaServiceRegrasTest`,      
                          `ManutencaoServiceTest`,         
                          controllers, handler,            
                          `NotificacaoWebhookTest` e       
                          demais classes `*Test`           

  Integração              `ConflitosRepositoryIT`,         PostgreSQL
                          `RestricaoExclusaoBancoIT`       (Testcontainers)

  Concorrência            `ConcorrenciaReservaIT`          PostgreSQL
                                                           (Testcontainers)

  API caixa-preta / E2E   `AutorizacaoApiIT`,              Sim
                          `RegrasDeNegocioApiIT`,          
                          `FluxoReservaApiIT`,             
                          `ManutencaoApiIT`                

  Integração externa      `NotificacaoWebhookWireMockIT`   WireMock
  --------------------------------------------------------------------------------

Detalhes em [`docs/PLANO_DE_TESTES.md`](docs/PLANO_DE_TESTES.md).

## Funcionalidades

-   Login HTTP Basic e autorização por papéis (ADMIN, RESPONSAVEL,
    SOLICITANTE) e por titularidade da reserva.
-   CRUD administrativo de salas, professores e materiais; pesquisa por
    tipo, capacidade, localização e competência.
-   Reservas: solicitação, alteração, aprovação, rejeição, início,
    conclusão, cancelamento e não comparecimento.
-   Detecção de sobreposição de sala, professor e material; **proteção
    contra dupla reserva** (lock pessimista + restrição de exclusão no
    PostgreSQL).
-   **Manutenção programada** de salas e materiais por período.
-   Registro de retirada e devolução de material; histórico de
    auditoria; notificação simulada e por **webhook** opcional.
-   Relatório de utilização: por sala, professor e material, carga
    horária e **conflitos evitados**.
-   Painel web responsivo e mensagens de erro padronizadas (JSON).

API completa em [`docs/API.md`](docs/API.md).

## Regras principais

1.  Término posterior ao início; início não pode estar no passado.
2.  Sem sobreposição para a mesma sala, professor ou material em
    reservas não finais; intervalos adjacentes são permitidos.
3.  Sala/professor inativo, recurso em manutenção (imediata ou
    programada) ou material indisponível não podem ser reservados.
4.  Material restrito gera `SOLICITADA` e exige aprovação de
    responsável; os demais são aprovados automaticamente.
5.  `SOLICITADA → APROVADA → EM_USO → CONCLUIDA`; alternativos:
    `REJEITADA`, `CANCELADA`, `NAO_COMPARECEU`.
6.  Mover reserva aprovada de recurso restrito exige nova aprovação.
7.  Toda mudança de estado gera auditoria.

## Documentação

  --------------------------------------------------------------------------------------------------------------
  Documento                                                                  Conteúdo
  -------------------------------------------------------------------------- -----------------------------------
  [`RTM.md`](RTM.md)                                                         Rastreabilidade requisito → risco →
                                                                             teste → evidência

  [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md)                               Diagramas C4 (contexto,
                                                                             contêineres, componentes) e
                                                                             sequências críticas

  [`docs/adr/`](docs/adr)                                                    Decisões de arquitetura (ADRs)

  [`docs/PLANO_DE_TESTES.md`](docs/PLANO_DE_TESTES.md)                       Estratégia, níveis, critérios de
                                                                             saída

  [`docs/SEGURANCA.md`](docs/SEGURANCA.md)                                   Checklist e mapeamento OWASP

  [`docs/REGISTRO_DE_DEFEITOS.md`](docs/REGISTRO_DE_DEFEITOS.md)             Defeitos encontrados, priorizados e
                                                                             corrigidos

  [`docs/TDD_BDD.md`](docs/TDD_BDD.md)                                       Cenários BDD e protocolo de
                                                                             evidência de TDD

  [`docs/RELATORIO_FINAL_QUALIDADE.md`](docs/RELATORIO_FINAL_QUALIDADE.md)   Relatório final e decisão de
                                                                             qualidade

  [`performance/README.md`](performance/README.md)                           Plano e resultado do teste de carga

  [`docs/evidencias/`](docs/evidencias)                                      Saídas brutas das execuções
  --------------------------------------------------------------------------------------------------------------

## CI e SonarCloud

O pipeline definido em `.github/workflows/ci.yml` é acionado a cada push
e pull request. Ele executa `./mvnw clean verify`, realiza os testes
unitários e de integração, aplica o JaCoCo e o gate de cobertura e
publica os relatórios configurados como artefatos.

O primeiro push deste repositório foi validado pelo GitHub Actions: o
workflow `ci.yml` terminou com status **Success**, com o job `build`
concluído. A execução pode ser consultada na aba [Actions do
repositório](https://github.com/muriloosantanaa/reservas-academicas-qa/actions).

A análise do SonarCloud é executada somente quando o secret
`SONAR_TOKEN` está configurado no repositório. Portanto, o sucesso do
workflow não significa, por si só, que uma análise SonarCloud já foi
realizada.

### Configurar o SonarCloud

1.  Em [sonarcloud.io](https://sonarcloud.io/), entre com o GitHub e use
    **+ → Analyze new project** no repositório.
2.  Escolha análise via **GitHub Actions** e desligue a opção
    **Automatic Analysis** (ela não lê a cobertura configurada no
    pipeline).
3.  Anote a *organization key* e a *project key*; gere um token em **My
    Account → Security**.
4.  No GitHub, acesse **Settings → Secrets and variables → Actions → New
    repository secret** e crie o secret `SONAR_TOKEN`.
5.  No `pom.xml`, configure `sonar.organization` e `sonar.projectKey`
    com os valores do projeto no SonarCloud.
6.  Faça um novo push ou abra um pull request e guarde as evidências do
    painel em `docs/evidencias/sonarcloud/`.

## Clonar o projeto

Para obter uma cópia do repositório:

``` bash
git clone https://github.com/muriloosantanaa/reservas-academicas-qa.git
cd reservas-academicas-qa
```

Depois, siga as instruções da seção **Executar localmente**.

No Windows, se necessário, garanta a permissão de execução do wrapper no
repositório:

``` bash
git update-index --chmod=+x mvnw
```

O workflow também ajusta a permissão do wrapper antes de executar o
Maven. Para evidências de TDD e revisão por pares, consulte
`docs/TDD_BDD.md` e utilize branches e pull requests.

## Uso de IA generativa

Este projeto contou com apoio do ChatGPT na revisão de código, na
investigação e correção de defeitos, na elaboração e análise de testes e
na documentação. A autoria e a responsabilidade pelas decisões, pela
validação e pela entrega do projeto são de Murilo Henrique Santana
Rodrigues. O uso da ferramenta deve ser descrito em conjunto com as
atividades efetivamente realizadas e revisadas pelo autor.

## Limitações conhecidas

Usuários e senhas fixos em memória; entidades JPA usadas como corpo das
requisições (mitigado, ver `docs/REGISTRO_DE_DEFEITOS.md`); CSRF
desabilitado com HTTP Basic; relatório sem paginação; webhook síncrono
sem retentativa; sem teste de UI; **HTTP Basic com BCrypt a cada
requisição limita a vazão** (ver `performance/README.md`); painel web
sem tela para manutenção programada.

A lista completa está em `docs/RELATORIO_FINAL_QUALIDADE.md`.
