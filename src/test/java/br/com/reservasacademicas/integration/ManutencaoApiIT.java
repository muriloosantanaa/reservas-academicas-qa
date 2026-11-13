package br.com.reservasacademicas.integration;

import br.com.reservasacademicas.support.ApiClient;
import br.com.reservasacademicas.support.ApiClient.Resposta;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static br.com.reservasacademicas.support.ApiClient.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Funcionalidade desenvolvida com BDD: manutenção programada de recursos.
 * Cenários (Dado/Quando/Então) descritos em docs/TDD_BDD.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ManutencaoApiIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    private int porta;

    private ApiClient api;

    @BeforeEach
    void iniciar() {
        api = new ApiClient(porta);
    }

    @Test
    @DisplayName("Cenário 1 — Dado uma sala com manutenção agendada, quando se reserva no período, então 409; fora do período, 200")
    void reservaDentroDoPeriodoDeManutencaoEBloqueada() {
        long sala = api.criarSala();
        long professor = api.criarProfessor();

        exigirOk(api.post(ADMIN, "/api/admin/manutencoes",
            corpo("SALA", sala, horario(100, 8), horario(102, 18), "Pintura")));

        Resposta dentro = api.reservar(PROFESSOR, sala, professor, null,
            horario(101, 9), horario(101, 11));
        Resposta fora = api.reservar(PROFESSOR, sala, professor, null,
            horario(103, 9), horario(103, 11));

        assertEquals(409, dentro.status());
        assertTrue(dentro.texto("message").contains("manutenção programada"));
        assertEquals(200, fora.status());
    }

    @Test
    @DisplayName("Cenário 2 — Dado um material em manutenção, quando se reserva no período, então 409")
    void materialEmManutencaoProgramada() {
        long material = api.criarMaterial(false);
        exigirOk(api.post(ADMIN, "/api/admin/manutencoes",
            corpo("MATERIAL", material, horario(110, 8), horario(110, 18), "Troca da lâmpada")));

        assertEquals(409, api.reservar(PROFESSOR, api.criarSala(), api.criarProfessor(), material,
            horario(110, 9), horario(110, 10)).status());
    }

    @Test
    @DisplayName("Cenário 3 — Dado reservas ativas no período, quando o admin agenda manutenção, então 409")
    void naoAgendaManutencaoSobreReservasAtivas() {
        long sala = api.criarSala();
        exigirOk(api.reservar(PROFESSOR, sala, api.criarProfessor(), null,
            horario(120, 9), horario(120, 11)));

        Resposta resposta = api.post(ADMIN, "/api/admin/manutencoes",
            corpo("SALA", sala, horario(120, 8), horario(120, 18), "Reforma"));

        assertEquals(409, resposta.status());
        assertTrue(resposta.texto("message").contains("reservas ativas"));
    }

    @Test
    @DisplayName("Cenário 4 — Dado uma manutenção cancelada, quando se reserva no período, então 200")
    void cancelarManutencaoLiberaOPeriodo() {
        long sala = api.criarSala();
        long id = exigirOk(api.post(ADMIN, "/api/admin/manutencoes",
            corpo("SALA", sala, horario(130, 8), horario(130, 18), "Pintura"))).numero("id");
        LocalDateTime inicio = horario(130, 9);
        assertEquals(409, api.reservar(PROFESSOR, sala, api.criarProfessor(), null,
            inicio, inicio.plusHours(1)).status());

        assertEquals(204, api.delete(ADMIN, "/api/admin/manutencoes/" + id).status());

        assertEquals(200, api.reservar(PROFESSOR, sala, api.criarProfessor(), null,
            inicio, inicio.plusHours(1)).status());
    }

    @Test
    @DisplayName("Cenário 5 — Dado manutenção adjacente à reserva, então não há conflito (fim = início)")
    void periodosAdjacentesNaoConflitam() {
        long sala = api.criarSala();
        exigirOk(api.post(ADMIN, "/api/admin/manutencoes",
            corpo("SALA", sala, horario(140, 12), horario(140, 18), "Limpeza")));

        assertEquals(200, api.reservar(PROFESSOR, sala, api.criarProfessor(), null,
            horario(140, 10), horario(140, 12)).status());
    }

    @Test
    @DisplayName("Cenário 6 — Validações do agendamento: professor, período invertido e recurso inexistente")
    void validacoesDoAgendamento() {
        long sala = api.criarSala();

        assertEquals(409, api.post(ADMIN, "/api/admin/manutencoes",
            corpo("PROFESSOR", sala, horario(150, 8), horario(150, 9), "x")).status());
        assertEquals(409, api.post(ADMIN, "/api/admin/manutencoes",
            corpo("SALA", sala, horario(150, 9), horario(150, 8), "x")).status());
        assertEquals(404, api.post(ADMIN, "/api/admin/manutencoes",
            corpo("SALA", 987654321L, horario(150, 8), horario(150, 9), "x")).status());
        assertEquals(400, api.post(ADMIN, "/api/admin/manutencoes", "{}").status());
    }

    @Test
    @DisplayName("Cenário 7 — Qualquer usuário consulta as manutenções; só o admin altera")
    void consultaAbertaEEscritaRestrita() {
        long sala = api.criarSala();
        String corpo = corpo("SALA", sala, horario(160, 8), horario(160, 9), "Teste");

        assertEquals(403, api.post(PROFESSOR, "/api/admin/manutencoes", corpo).status());
        assertEquals(403, api.post(RESPONSAVEL, "/api/admin/manutencoes", corpo).status());
        exigirOk(api.post(ADMIN, "/api/admin/manutencoes", corpo));
        assertEquals(200, api.get(PROFESSOR, "/api/manutencoes").status());
        assertTrue(api.get(PROFESSOR, "/api/manutencoes").corpo().isArray());
    }

    private static String corpo(
        String tipo,
        long recursoId,
        LocalDateTime inicio,
        LocalDateTime fim,
        String motivo
    ) {
        ObjectNode n = JSON.createObjectNode();
        n.put("tipoRecurso", tipo);
        n.put("recursoId", recursoId);
        n.put("inicio", inicio.toString());
        n.put("fim", fim.toString());
        n.put("motivo", motivo);
        return n.toString();
    }
}
