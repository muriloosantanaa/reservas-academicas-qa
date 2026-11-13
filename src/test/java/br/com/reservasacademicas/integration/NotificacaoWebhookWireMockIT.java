package br.com.reservasacademicas.integration;

import br.com.reservasacademicas.support.ApiClient;
import br.com.reservasacademicas.support.ApiClient.Resposta;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static br.com.reservasacademicas.support.ApiClient.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RF11 com integração externa isolada: o serviço de notificação (webhook) é
 * simulado com WireMock; nenhuma chamada sai da máquina de teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificacaoWebhookWireMockIT {

    private static final WireMockServer WIREMOCK =
        new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("notificacao.webhook.url",
            () -> "http://localhost:" + WIREMOCK.port() + "/notificacoes");
        registro.add("notificacao.webhook.timeout-ms", () -> "800");
        // banco H2 próprio: este contexto não compartilha dados com os demais
        registro.add("spring.datasource.url",
            () -> "jdbc:h2:mem:reservas_wiremock;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    }

    @AfterAll
    static void encerrar() {
        WIREMOCK.stop();
    }

    @Value("${local.server.port}")
    private int porta;

    private ApiClient api;

    @BeforeEach
    void iniciar() {
        api = new ApiClient(porta);
        WIREMOCK.resetAll();
    }

    @Test
    @DisplayName("Criar reserva dispara POST JSON para o serviço externo de notificação")
    void enviaNotificacaoParaOServicoExterno() {
        WIREMOCK.stubFor(post(urlEqualTo("/notificacoes"))
            .willReturn(aResponse().withStatus(200)));
        LocalDateTime inicio = horario(170, 8);

        Resposta criada = exigirOk(api.reservar(
            PROFESSOR, api.criarSala(), api.criarProfessor(), null,
            inicio, inicio.plusHours(1)));

        WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/notificacoes"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(containing("\"reservaId\":" + criada.numero("id")))
            .withRequestBody(containing("@faculdade.edu.br"))
            .withRequestBody(containing("solicitação criada")));
    }

    @Test
    @DisplayName("Cada mudança de estado gera uma nova chamada ao serviço externo")
    void mudancaDeEstadoNotificaNovamente() {
        WIREMOCK.stubFor(post(urlEqualTo("/notificacoes"))
            .willReturn(aResponse().withStatus(200)));
        LocalDateTime inicio = horario(171, 8);
        long id = exigirOk(api.reservar(
            PROFESSOR, api.criarSala(), api.criarProfessor(), null,
            inicio, inicio.plusHours(1))).numero("id");

        exigirOk(api.post(RESPONSAVEL, "/api/reservas/" + id + "/iniciar", null));

        WIREMOCK.verify(2, postRequestedFor(urlEqualTo("/notificacoes")));
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/notificacoes"))
            .withRequestBody(containing("EM_USO")));
    }

    @Test
    @DisplayName("Serviço externo respondendo 500 não impede a reserva")
    void falhaExternaNaoQuebraAReserva() {
        WIREMOCK.stubFor(post(urlEqualTo("/notificacoes"))
            .willReturn(aResponse().withStatus(500)));
        LocalDateTime inicio = horario(172, 8);

        Resposta resposta = api.reservar(
            PROFESSOR, api.criarSala(), api.criarProfessor(), null,
            inicio, inicio.plusHours(1));

        assertEquals(200, resposta.status());
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/notificacoes")));
    }

    @Test
    @DisplayName("Serviço externo lento é cortado pelo timeout e a reserva é criada")
    void servicoLentoRespeitaTimeout() {
        WIREMOCK.stubFor(post(urlEqualTo("/notificacoes"))
            .willReturn(aResponse().withStatus(200).withFixedDelay(5000)));
        LocalDateTime inicio = horario(173, 8);

        long antes = System.nanoTime();
        Resposta resposta = api.reservar(
            PROFESSOR, api.criarSala(), api.criarProfessor(), null,
            inicio, inicio.plusHours(1));
        long duracaoMs = (System.nanoTime() - antes) / 1_000_000;

        assertEquals(200, resposta.status());
        assertTrue(duracaoMs < 4000, "a reserva esperou " + duracaoMs + " ms pelo webhook");
    }

    @Test
    @DisplayName("Reserva recusada por conflito não dispara notificação externa")
    void conflitoNaoNotifica() {
        WIREMOCK.stubFor(post(urlEqualTo("/notificacoes"))
            .willReturn(aResponse().withStatus(200)));
        long sala = api.criarSala();
        LocalDateTime inicio = horario(174, 8);
        exigirOk(api.reservar(PROFESSOR, sala, api.criarProfessor(), null,
            inicio, inicio.plusHours(1)));
        WIREMOCK.resetRequests();

        Resposta conflito = api.reservar(PROFESSOR, sala, api.criarProfessor(), null,
            inicio, inicio.plusHours(1));

        assertEquals(409, conflito.status());
        WIREMOCK.verify(0, postRequestedFor(urlEqualTo("/notificacoes")));
    }
}
