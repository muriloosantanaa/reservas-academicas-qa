package br.com.reservasacademicas.integration;

import br.com.reservasacademicas.support.ApiClient;
import br.com.reservasacademicas.support.ApiClient.Resposta;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

import static br.com.reservasacademicas.support.ApiClient.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Fluxo ponta a ponta (E2E de API): perfis, estados, material restrito,
 * retirada/devolução, auditoria, notificação e relatório — tudo pela porta
 * HTTP, com o banco real da aplicação, como um cliente externo faria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FluxoReservaApiIT {

    @Value("${local.server.port}")
    private int porta;

    private ApiClient api;

    @BeforeEach
    void iniciar() {
        api = new ApiClient(porta);
    }

    @Test
    @DisplayName("Ciclo completo: SOLICITADA → APROVADA → EM_USO → CONCLUÍDA com material restrito")
    void cicloCompletoComMaterialRestrito() {
        long sala = api.criarSala();
        long professor = api.criarProfessor();
        long material = api.criarMaterial(true);
        LocalDateTime inicio = horario(60, 9);

        // 1. solicitante pede reserva com recurso restrito → fica SOLICITADA
        Resposta criada = exigirOk(api.reservar(
            PROFESSOR, sala, professor, material, inicio, inicio.plusHours(2)));
        long id = criada.numero("id");
        assertEquals("SOLICITADA", criada.texto("status"));
        assertEquals("professor", criada.texto("solicitanteUsuario"));

        // 2. enquanto não aprovada, não pode ser iniciada
        assertEquals(409, api.post(RESPONSAVEL, url(id, "iniciar"), null).status());

        // 3. só o responsável aprova
        assertEquals(403, api.post(PROFESSOR, url(id, "aprovar"), null).status());
        assertEquals("APROVADA",
            exigirOk(api.post(RESPONSAVEL, url(id, "aprovar"), null)).texto("status"));

        // 4. retirada só é possível durante o uso
        assertEquals(409, api.post(RESPONSAVEL, url(id, "retirada"), null).status());
        assertEquals("EM_USO",
            exigirOk(api.post(RESPONSAVEL, url(id, "iniciar"), null)).texto("status"));

        // 5. retirada e devolução, sem duplicidade
        assertEquals(409, api.post(RESPONSAVEL, url(id, "devolucao"), null).status(),
            "devolução exige retirada prévia");
        assertFalse(exigirOk(api.post(RESPONSAVEL, url(id, "retirada"), null))
            .corpo().path("retiradaEm").isNull());
        assertEquals(409, api.post(RESPONSAVEL, url(id, "retirada"), null).status());
        assertFalse(exigirOk(api.post(RESPONSAVEL, url(id, "devolucao"), null))
            .corpo().path("devolvidaEm").isNull());
        assertEquals(409, api.post(RESPONSAVEL, url(id, "devolucao"), null).status());

        // 6. reserva em uso não pode ser apagada nem cancelada
        assertEquals(405, api.delete(ADMIN, "/api/reservas/" + id).status(),
            "não existe operação de exclusão de reservas");
        assertEquals(409, api.post(PROFESSOR, url(id, "cancelar"), null).status());

        // 7. conclusão e estado final
        assertEquals("CONCLUIDA",
            exigirOk(api.post(RESPONSAVEL, url(id, "concluir"), null)).texto("status"));
        assertEquals(409, api.post(RESPONSAVEL, url(id, "concluir"), null).status());
        assertEquals(409, api.post(PROFESSOR, url(id, "cancelar"), null).status());

        // 8. toda mudança de estado gerou auditoria (visível ao dono)
        List<String> acoes = new ArrayList<>();
        for (JsonNode a : exigirOk(api.get(PROFESSOR, "/api/reservas/" + id + "/historico")).corpo()) {
            acoes.add(a.path("acao").asString());
        }
        for (String esperada : List.of(
            "CRIADA - SOLICITADA", "STATUS: APROVADA", "STATUS: EM_USO",
            "RETIRADA DE MATERIAL", "DEVOLUÇÃO DE MATERIAL", "STATUS: CONCLUIDA")) {
            assertTrue(acoes.contains(esperada), "auditoria sem " + esperada + " em " + acoes);
        }

        // 9. notificações simuladas persistidas
        JsonNode notificacoes =
            exigirOk(api.get(PROFESSOR, "/api/reservas/" + id + "/notificacoes")).corpo();
        assertTrue(notificacoes.size() >= 4);

        // 10. relatório enxerga a reserva concluída
        Resposta relatorio = exigirOk(api.get(RESPONSAVEL, "/api/relatorios/utilizacao"));
        assertTrue(relatorio.numero("reservasConcluidas") >= 1);
        assertTrue(relatorio.corpo().path("cargaHorariaAlocada").asDouble() >= 2.0);
    }

    @Test
    @DisplayName("Recurso comum é aprovado automaticamente")
    void recursoComumNasceAprovado() {
        LocalDateTime inicio = horario(61, 9);

        Resposta criada = exigirOk(api.reservar(
            PROFESSOR, api.criarSala(), api.criarProfessor(), api.criarMaterial(false),
            inicio, inicio.plusHours(1)));

        assertEquals("APROVADA", criada.texto("status"));
    }

    @Test
    @DisplayName("Responsável rejeita solicitação: estado final e horário liberado")
    void rejeicaoLiberaOHorario() {
        long sala = api.criarSala();
        long professor = api.criarProfessor();
        long material = api.criarMaterial(true);
        LocalDateTime inicio = horario(62, 9);

        long id = exigirOk(api.reservar(
            PROFESSOR, sala, professor, material, inicio, inicio.plusHours(2))).numero("id");
        assertEquals("REJEITADA",
            exigirOk(api.post(RESPONSAVEL, url(id, "rejeitar"), null)).texto("status"));

        // o mesmo horário volta a ficar disponível
        assertEquals(200, api.reservar(
            PROFESSOR, sala, api.criarProfessor(), null, inicio, inicio.plusHours(2)).status());
    }

    @Test
    @DisplayName("Não comparecimento: estado final alcançado a partir de APROVADA")
    void naoCompareceu() {
        LocalDateTime inicio = horario(63, 9);
        long id = exigirOk(api.reservar(
            PROFESSOR, api.criarSala(), api.criarProfessor(), null,
            inicio, inicio.plusHours(1))).numero("id");

        assertEquals("NAO_COMPARECEU",
            exigirOk(api.post(RESPONSAVEL, url(id, "nao-compareceu"), null)).texto("status"));
        assertEquals(409, api.post(RESPONSAVEL, url(id, "iniciar"), null).status());
    }

    @Test
    @DisplayName("Cancelar pelo dono libera o horário; alterar respeita conflitos")
    void cancelarELiberarEAlterar() {
        long sala = api.criarSala();
        LocalDateTime inicio = horario(64, 9);

        long id = exigirOk(api.reservar(
            PROFESSOR, sala, api.criarProfessor(), null, inicio, inicio.plusHours(2))).numero("id");

        // outra reserva ocupa 12h–13h; mover a primeira para lá conflita (409)
        long outraProf = api.criarProfessor();
        exigirOk(api.reservar(PROFESSOR, sala, outraProf, null,
            horario(64, 12), horario(64, 13)));
        long profDaPrimeira = exigirOk(api.get(PROFESSOR, "/api/reservas/" + id))
            .corpo().path("professor").path("id").asLong();
        Resposta conflito = api.put(PROFESSOR, "/api/reservas/" + id,
            corpoReserva(sala, profDaPrimeira, null, horario(64, 12), horario(64, 13)));
        assertEquals(409, conflito.status());
        assertEquals(inicio,
            LocalDateTime.parse(
                exigirOk(api.get(PROFESSOR, "/api/reservas/" + id)).texto("inicio")),
            "conflito não pode alterar a reserva original");

        // mover para um horário livre funciona
        assertEquals(200, api.put(PROFESSOR, "/api/reservas/" + id,
            corpoReserva(sala, profDaPrimeira, null, horario(64, 15), horario(64, 16))).status());

        // cancelar libera o horário 15h–16h
        assertEquals("CANCELADA",
            exigirOk(api.post(PROFESSOR, url(id, "cancelar"), null)).texto("status"));
        assertEquals(200, api.reservar(PROFESSOR, sala, api.criarProfessor(), null,
            horario(64, 15), horario(64, 16)).status());
    }

    private static String url(long id, String acao) {
        return "/api/reservas/" + id + "/" + acao;
    }
}
