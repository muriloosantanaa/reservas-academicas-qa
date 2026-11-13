package br.com.reservasacademicas.integration;

import br.com.reservasacademicas.support.ApiClient;
import br.com.reservasacademicas.support.ApiClient.Resposta;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static br.com.reservasacademicas.support.ApiClient.*;
import static org.junit.jupiter.api.Assertions.*;

/** Regras críticas de tempo, recursos indisponíveis e tratamento seguro de erros. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegrasDeNegocioApiIT {

    @Value("${local.server.port}")
    private int porta;

    private ApiClient api;

    @BeforeEach
    void iniciar() {
        api = new ApiClient(porta);
    }

    // ------------------------------------------------------- tempo e conflito

    @Test
    @DisplayName("Término anterior ou igual ao início é recusado")
    void terminoNaoPosteriorAoInicio() {
        long sala = api.criarSala();
        long professor = api.criarProfessor();
        LocalDateTime inicio = horario(80, 10);

        Resposta antes = api.reservar(PROFESSOR, sala, professor, null, inicio, inicio.minusHours(1));
        Resposta igual = api.reservar(PROFESSOR, sala, professor, null, inicio, inicio);

        assertEquals(409, antes.status());
        assertEquals("O término deve ser posterior ao início", antes.texto("message"));
        assertEquals(409, igual.status());
    }

    @Test
    @DisplayName("Não é permitido reservar no passado")
    void reservaNoPassado() {
        LocalDateTime ontem = LocalDateTime.now().minusDays(1);

        Resposta resposta = api.reservar(
            PROFESSOR, api.criarSala(), api.criarProfessor(), null, ontem, ontem.plusHours(1));

        assertEquals(409, resposta.status());
    }

    @Test
    @DisplayName("Sobreposição de sala, professor e material retorna 409 com mensagem clara")
    void sobreposicaoDeRecursos() {
        long sala = api.criarSala();
        long professor = api.criarProfessor();
        long material = api.criarMaterial(false);
        LocalDateTime inicio = horario(81, 10);
        exigirOk(api.reservar(PROFESSOR, sala, professor, material, inicio, inicio.plusHours(2)));

        Resposta mesmaSala = api.reservar(PROFESSOR, sala, api.criarProfessor(), null,
            inicio.plusHours(1), inicio.plusHours(3));
        Resposta mesmoProfessor = api.reservar(PROFESSOR, api.criarSala(), professor, null,
            inicio.plusHours(1), inicio.plusHours(3));
        Resposta mesmoMaterial = api.reservar(PROFESSOR, api.criarSala(), api.criarProfessor(), material,
            inicio.minusHours(1), inicio.plusHours(1));

        assertEquals(409, mesmaSala.status());
        assertEquals("Sala já possui reserva nesse horário", mesmaSala.texto("message"));
        assertEquals(409, mesmoProfessor.status());
        assertEquals("Professor já possui reserva nesse horário", mesmoProfessor.texto("message"));
        assertEquals(409, mesmoMaterial.status());
        assertEquals("Material já possui reserva nesse horário", mesmoMaterial.texto("message"));
    }

    @Test
    @DisplayName("Reservas adjacentes (fim = início) são aceitas, inclusive na agenda do professor")
    void reservasAdjacentes() {
        long sala = api.criarSala();
        long professor = api.criarProfessor();
        LocalDateTime inicio = horario(82, 8);

        assertEquals(200, api.reservar(PROFESSOR, sala, professor, null,
            inicio, inicio.plusHours(2)).status());
        assertEquals(200, api.reservar(PROFESSOR, sala, professor, null,
            inicio.plusHours(2), inicio.plusHours(4)).status());
    }

    // ---------------------------------------------------- recursos indisponíveis

    @Test
    @DisplayName("Sala em manutenção (flag) não pode ser reservada")
    void salaEmManutencao() {
        long sala = api.criarSala();
        api.alterarFlag("salas", sala, "emManutencao", true);
        LocalDateTime inicio = horario(83, 8);

        assertEquals(409, api.reservar(PROFESSOR, sala, api.criarProfessor(), null,
            inicio, inicio.plusHours(1)).status());
    }

    @Test
    @DisplayName("Sala inativa não pode ser reservada")
    void salaInativa() {
        long sala = api.criarSala();
        api.alterarFlag("salas", sala, "ativa", false);
        LocalDateTime inicio = horario(83, 10);

        assertEquals(409, api.reservar(PROFESSOR, sala, api.criarProfessor(), null,
            inicio, inicio.plusHours(1)).status());
    }

    @Test
    @DisplayName("Material em manutenção ou indisponível não pode ser reservado")
    void materialIndisponivel() {
        long emManutencao = api.criarMaterial(false);
        long indisponivel = api.criarMaterial(false);
        api.alterarFlag("materiais", emManutencao, "emManutencao", true);
        api.alterarFlag("materiais", indisponivel, "disponivel", false);
        LocalDateTime inicio = horario(83, 12);

        assertEquals(409, api.reservar(PROFESSOR, api.criarSala(), api.criarProfessor(),
            emManutencao, inicio, inicio.plusHours(1)).status());
        assertEquals(409, api.reservar(PROFESSOR, api.criarSala(), api.criarProfessor(),
            indisponivel, inicio, inicio.plusHours(1)).status());
    }

    // ------------------------------------------------- erros seguros e consistentes

    @Test
    @DisplayName("Erros de cliente retornam 4xx, nunca 500, e sem detalhes internos")
    void errosDeClienteSaoQuatroXX() {
        assertEquals(400, api.post(PROFESSOR, "/api/reservas", "{quebrado").status());
        assertEquals(400, api.post(PROFESSOR, "/api/reservas", "{}").status());
        assertEquals(400, api.get(PROFESSOR, "/api/reservas/abc").status());
        assertEquals(404, api.get(PROFESSOR, "/api/rota-que-nao-existe").status());
        assertEquals(405, api.delete(PROFESSOR, "/api/reservas").status());
        assertEquals(404, api.get(ADMIN, "/api/reservas/987654321").status());

        Resposta erro = api.post(PROFESSOR, "/api/reservas", "{quebrado");
        String texto = erro.corpo().toString();
        assertFalse(texto.contains("Exception"), texto);
        assertFalse(texto.contains("at br.com"), texto);
        assertNotNull(erro.corpo().get("timestamp"));
        assertEquals(400, erro.numero("status"));
    }

    @Test
    @DisplayName("Reserva com recursos inexistentes retorna 404")
    void recursosInexistentes() {
        LocalDateTime inicio = horario(84, 8);

        assertEquals(404, api.reservar(PROFESSOR, 987654321L, api.criarProfessor(), null,
            inicio, inicio.plusHours(1)).status());
        assertEquals(404, api.reservar(PROFESSOR, api.criarSala(), 987654321L, null,
            inicio, inicio.plusHours(1)).status());
    }

    @Test
    @DisplayName("Id enviado pelo cliente não sobrescreve reserva de outra pessoa")
    void idDoClienteNaoSobrescreveOutraReserva() {
        long sala = api.criarSala();
        long professor = api.criarProfessor();
        LocalDateTime inicio = horario(85, 8);
        long vitima = exigirOk(api.reservar(
            RESPONSAVEL, sala, professor, null, inicio, inicio.plusHours(1))).numero("id");

        String ataque = corpoReserva(api.criarSala(), api.criarProfessor(), null,
            horario(85, 14), horario(85, 15))
            .replaceFirst("\\{", "{\"id\":" + vitima + ",\"retiradaEm\":\"2026-01-01T00:00:00\",");
        Resposta resposta = api.post(PROFESSOR, "/api/reservas", ataque);

        assertEquals(200, resposta.status());
        assertNotEquals(vitima, resposta.numero("id"), "deve criar uma NOVA reserva");
        assertTrue(resposta.corpo().path("retiradaEm").isNull());

        Resposta original = exigirOk(api.get(ADMIN, "/api/reservas/" + vitima));
        assertEquals("responsavel", original.texto("solicitanteUsuario"));
        assertEquals("Aula de teste", original.texto("finalidade"));
        assertEquals(sala, original.corpo().path("sala").path("id").asLong());
    }

    @Test
    @DisplayName("Excluir sala que possui reservas retorna 409 (e não 500)")
    void excluirSalaComReservas() {
        long sala = api.criarSala();
        LocalDateTime inicio = horario(86, 8);
        exigirOk(api.reservar(PROFESSOR, sala, api.criarProfessor(), null,
            inicio, inicio.plusHours(1)));

        assertEquals(409, api.delete(ADMIN, "/api/admin/salas/" + sala).status());
    }
}
