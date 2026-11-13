package br.com.reservasacademicas.integration;

import br.com.reservasacademicas.support.ApiClient;
import br.com.reservasacademicas.support.ApiClient.Resposta;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static br.com.reservasacademicas.support.ApiClient.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RF01 / limitador de nota "falha de autorização": matriz de caixa-preta
 * perfil × endpoint e verificação de titularidade das reservas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AutorizacaoApiIT {

    @Value("${local.server.port}")
    private int porta;

    private ApiClient api;

    @BeforeEach
    void iniciar() {
        api = new ApiClient(porta);
    }

    /** usuário "-" significa requisição anônima (sem Authorization). */
    @ParameterizedTest(name = "{0} {1} {2} → {3}")
    @CsvSource(delimiter = '|', textBlock = """
        # anônimo
        -           | GET    | /api/health                          | 200
        -           | GET    | /                                    | 200
        -           | GET    | /api/salas                           | 401
        -           | GET    | /api/professores                     | 401
        -           | GET    | /api/materiais                       | 401
        -           | GET    | /api/reservas                        | 401
        -           | GET    | /api/manutencoes                     | 401
        -           | POST   | /api/reservas                        | 401
        -           | POST   | /api/admin/salas                     | 401
        -           | GET    | /api/relatorios/utilizacao           | 401
        # solicitante (professor)
        professor   | GET    | /api/salas                           | 200
        professor   | GET    | /api/manutencoes                     | 200
        professor   | GET    | /api/reservas                        | 200
        professor   | POST   | /api/admin/salas                     | 403
        professor   | PUT    | /api/admin/salas/1                   | 403
        professor   | DELETE | /api/admin/salas/1                   | 403
        professor   | POST   | /api/admin/professores               | 403
        professor   | POST   | /api/admin/materiais                 | 403
        professor   | POST   | /api/admin/manutencoes               | 403
        professor   | DELETE | /api/admin/manutencoes/1             | 403
        professor   | GET    | /api/relatorios/utilizacao           | 403
        professor   | POST   | /api/reservas/1/aprovar              | 403
        professor   | POST   | /api/reservas/1/rejeitar             | 403
        professor   | POST   | /api/reservas/1/iniciar              | 403
        professor   | POST   | /api/reservas/1/concluir             | 403
        professor   | POST   | /api/reservas/1/nao-compareceu       | 403
        professor   | POST   | /api/reservas/1/retirada             | 403
        professor   | POST   | /api/reservas/1/devolucao            | 403
        # responsável
        responsavel | GET    | /api/relatorios/utilizacao           | 200
        responsavel | GET    | /api/reservas                        | 200
        responsavel | POST   | /api/admin/salas                     | 403
        responsavel | POST   | /api/admin/materiais                 | 403
        responsavel | POST   | /api/admin/manutencoes               | 403
        responsavel | DELETE | /api/admin/professores/1             | 403
        # administrador
        admin       | GET    | /api/relatorios/utilizacao           | 200
        admin       | POST   | /api/admin/salas                     | 400
        admin       | DELETE | /api/admin/salas/987654321           | 404
        admin       | DELETE | /api/admin/manutencoes/987654321     | 404
        admin       | GET    | /api/reservas/987654321              | 404
        """)
    void matrizPerfilVersusEndpoint(
        String usuario,
        String metodo,
        String caminho,
        int esperado
    ) {
        String perfil = "-".equals(usuario) ? null : usuario;
        String corpo = ("POST".equals(metodo) || "PUT".equals(metodo)) ? "{}" : null;

        Resposta resposta = switch (metodo) {
            case "GET" -> api.get(perfil, caminho);
            case "POST" -> api.post(perfil, caminho, corpo);
            case "PUT" -> api.put(perfil, caminho, corpo);
            case "DELETE" -> api.delete(perfil, caminho);
            default -> throw new IllegalArgumentException(metodo);
        };

        assertEquals(esperado, resposta.status(), resposta.corpo().toString());
    }

    @Test
    @DisplayName("Senha incorreta e usuário inexistente recebem 401")
    void credenciaisInvalidasRecebem401() {
        assertEquals(401, api.enviarComAutorizacao(
            "GET", "/api/salas", basic("professor", "senha-errada")).status());
        assertEquals(401, api.enviarComAutorizacao(
            "GET", "/api/salas", basic("inexistente", "qualquer")).status());
        assertEquals(401, api.enviarComAutorizacao(
            "GET", "/api/salas", "Basic isso-nao-e-base64").status());
    }

    @Test
    @DisplayName("Solicitante não acessa, altera nem cancela reserva de outra pessoa (403)")
    void titularidadeDaReserva() {
        long sala = api.criarSala();
        long professor = api.criarProfessor();
        LocalDateTime inicio = horario(70, 8);

        // reserva criada pelo "responsavel": o usuário "professor" é terceiro
        Resposta criada = exigirOk(api.reservar(
            RESPONSAVEL, sala, professor, null, inicio, inicio.plusHours(2)));
        long id = criada.numero("id");

        assertEquals(403, api.get(PROFESSOR, "/api/reservas/" + id).status());
        assertEquals(403, api.get(PROFESSOR, "/api/reservas/" + id + "/historico").status());
        assertEquals(403, api.get(PROFESSOR, "/api/reservas/" + id + "/notificacoes").status());
        assertEquals(403, api.post(PROFESSOR, "/api/reservas/" + id + "/cancelar", null).status());
        assertEquals(403, api.put(
            PROFESSOR,
            "/api/reservas/" + id,
            corpoReserva(sala, professor, null, inicio.plusHours(3), inicio.plusHours(4))
        ).status());

        // a reserva continua intacta e visível ao dono, ao responsável e ao admin
        assertEquals(200, api.get(RESPONSAVEL, "/api/reservas/" + id).status());
        assertEquals(200, api.get(ADMIN, "/api/reservas/" + id).status());
        assertEquals("APROVADA", api.get(ADMIN, "/api/reservas/" + id).texto("status"));
    }

    @Test
    @DisplayName("A listagem do solicitante mostra apenas as próprias reservas")
    void listagemFiltradaPorUsuario() {
        long sala = api.criarSala();
        long professor = api.criarProfessor();
        LocalDateTime inicio = horario(71, 8);
        long alheia = exigirOk(api.reservar(
            RESPONSAVEL, sala, professor, null, inicio, inicio.plusHours(1))).numero("id");

        for (var item : exigirOk(api.get(PROFESSOR, "/api/reservas")).corpo()) {
            assertNotEquals(alheia, item.path("id").asLong());
            assertEquals("professor", item.path("solicitanteUsuario").asString());
        }
    }
}
