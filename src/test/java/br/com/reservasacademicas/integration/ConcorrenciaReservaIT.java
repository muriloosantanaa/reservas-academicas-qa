package br.com.reservasacademicas.integration;

import br.com.reservasacademicas.support.ApiClient;
import br.com.reservasacademicas.support.ApiClient.Resposta;
import br.com.reservasacademicas.support.PostgresContainerBase;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

import static br.com.reservasacademicas.support.ApiClient.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RF06 / regra crítica "duas solicitações simultâneas devem produzir uma
 * única reserva aceita". Dispara requisições HTTP realmente simultâneas
 * (largada sincronizada por barreira) contra a aplicação com PostgreSQL real
 * em container. Cobre sala, professor, material e a propriedade geral de que
 * as reservas aceitas nunca se sobrepõem.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcorrenciaReservaIT extends PostgresContainerBase {

    private static final int SIMULTANEAS = 20;

    @Value("${local.server.port}")
    private int porta;

    private ApiClient api;

    @BeforeEach
    void iniciar() {
        api = new ApiClient(porta);
    }

    @RepeatedTest(value = 3, name = "mesma sala e mesmo horário, rodada {currentRepetition}/{totalRepetitions}")
    @DisplayName("20 solicitações simultâneas para a mesma sala geram uma única reserva")
    void mesmaSalaMesmoHorarioAceitaApenasUma() throws Exception {
        long sala = api.criarSala();
        List<Long> professores = criarProfessores(SIMULTANEAS);
        LocalDateTime inicio = horario(30, 8);
        long conflitosAntes = conflitosEvitados();

        List<Resposta> respostas = dispararSimultaneamente(SIMULTANEAS, i ->
            api.reservar(PROFESSOR, sala, professores.get(i), null, inicio, inicio.plusHours(2))
        );

        assertEquals(1, contar(respostas, 200), "exatamente uma reserva aceita");
        assertEquals(SIMULTANEAS - 1, contar(respostas, 409), "as demais recebem 409");
        assertEquals(1, reservasDaSala(sala).size(), "uma única linha no banco");
        assertEquals(
            conflitosAntes + SIMULTANEAS - 1,
            conflitosEvitados(),
            "cada conflito barrado é contabilizado"
        );
    }

    @Test
    @DisplayName("20 solicitações simultâneas do mesmo professor em salas diferentes geram uma reserva")
    void mesmoProfessorAceitaApenasUma() throws Exception {
        long professor = api.criarProfessor();
        List<Long> salas = criarSalas(SIMULTANEAS);
        LocalDateTime inicio = horario(31, 8);

        List<Resposta> respostas = dispararSimultaneamente(SIMULTANEAS, i ->
            api.reservar(PROFESSOR, salas.get(i), professor, null, inicio, inicio.plusHours(2))
        );

        assertEquals(1, contar(respostas, 200));
        assertEquals(SIMULTANEAS - 1, contar(respostas, 409));
    }

    @Test
    @DisplayName("20 solicitações simultâneas do mesmo material geram uma reserva")
    void mesmoMaterialAceitaApenasUma() throws Exception {
        long material = api.criarMaterial(false);
        List<Long> salas = criarSalas(SIMULTANEAS);
        List<Long> professores = criarProfessores(SIMULTANEAS);
        LocalDateTime inicio = horario(32, 8);

        List<Resposta> respostas = dispararSimultaneamente(SIMULTANEAS, i ->
            api.reservar(PROFESSOR, salas.get(i), professores.get(i), material, inicio, inicio.plusHours(2))
        );

        assertEquals(1, contar(respostas, 200));
        assertEquals(SIMULTANEAS - 1, contar(respostas, 409));
    }

    @Test
    @DisplayName("Períodos parcialmente sobrepostos: as reservas aceitas nunca se sobrepõem")
    void reservasAceitasNuncaSeSobrepoem() throws Exception {
        long sala = api.criarSala();
        int tentativas = 12;
        List<Long> professores = criarProfessores(tentativas);
        LocalDateTime base = horario(33, 6);

        // tentativa i: [base + 30·i min, +60 min] — cada uma sobrepõe as vizinhas
        List<Resposta> respostas = dispararSimultaneamente(tentativas, i -> {
            LocalDateTime inicio = base.plusMinutes(30L * i);
            return api.reservar(PROFESSOR, sala, professores.get(i), null, inicio, inicio.plusHours(1));
        });

        assertTrue(contar(respostas, 200) >= 1);
        assertEquals(tentativas, contar(respostas, 200) + contar(respostas, 409));

        List<LocalDateTime[]> aceitas = new ArrayList<>();
        for (JsonNode r : reservasDaSala(sala)) {
            aceitas.add(new LocalDateTime[] {
                LocalDateTime.parse(r.path("inicio").asString()),
                LocalDateTime.parse(r.path("fim").asString())
            });
        }

        for (int a = 0; a < aceitas.size(); a++) {
            for (int b = a + 1; b < aceitas.size(); b++) {
                boolean sobrepoe =
                    aceitas.get(a)[0].isBefore(aceitas.get(b)[1]) &&
                    aceitas.get(a)[1].isAfter(aceitas.get(b)[0]);
                assertFalse(sobrepoe, "reservas aceitas se sobrepõem");
            }
        }
    }

    @Test
    @DisplayName("Horários adjacentes simultâneos não geram falso conflito")
    void horariosAdjacentesSaoTodosAceitos() throws Exception {
        long sala = api.criarSala();
        int tentativas = 10;
        List<Long> professores = criarProfessores(tentativas);

        List<Resposta> respostas = dispararSimultaneamente(tentativas, i -> {
            LocalDateTime inicio = horario(34, 6 + i);
            return api.reservar(PROFESSOR, sala, professores.get(i), null, inicio, inicio.plusHours(1));
        });

        assertEquals(tentativas, contar(respostas, 200));
        assertEquals(tentativas, reservasDaSala(sala).size());
    }

    // --------------------------------------------------------------- apoio

    private interface Chamada {
        Resposta executar(int indice);
    }

    private List<Resposta> dispararSimultaneamente(int n, Chamada chamada)
        throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CyclicBarrier largada = new CyclicBarrier(n);

        try {
            List<Future<Resposta>> futuros = new ArrayList<>();

            for (int i = 0; i < n; i++) {
                final int indice = i;
                Callable<Resposta> tarefa = () -> {
                    largada.await(30, TimeUnit.SECONDS);
                    return chamada.executar(indice);
                };
                futuros.add(pool.submit(tarefa));
            }

            List<Resposta> respostas = new ArrayList<>();
            for (Future<Resposta> futuro : futuros) {
                respostas.add(futuro.get(90, TimeUnit.SECONDS));
            }
            return respostas;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long contar(List<Resposta> respostas, int status) {
        return respostas.stream().filter(r -> r.status() == status).count();
    }

    private List<Long> criarProfessores(int n) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(api.criarProfessor());
        }
        return ids;
    }

    private List<Long> criarSalas(int n) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(api.criarSala());
        }
        return ids;
    }

    private List<JsonNode> reservasDaSala(long salaId) {
        List<JsonNode> encontradas = new ArrayList<>();
        for (JsonNode r : exigirOk(api.get(ADMIN, "/api/reservas")).corpo()) {
            if (r.path("sala").path("id").asLong() == salaId) {
                encontradas.add(r);
            }
        }
        return encontradas;
    }

    private long conflitosEvitados() {
        return exigirOk(api.get(ADMIN, "/api/relatorios/utilizacao"))
            .numero("conflitosEvitados");
    }
}
