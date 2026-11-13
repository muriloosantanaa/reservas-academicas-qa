package br.com.reservasacademicas;

import br.com.reservasacademicas.entity.Notificacao;
import br.com.reservasacademicas.entity.Professor;
import br.com.reservasacademicas.entity.Reserva;
import br.com.reservasacademicas.repository.NotificacaoRepository;
import br.com.reservasacademicas.service.NotificacaoService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integração HTTP real (servidor do JDK) para o webhook de notificação.
 * O cenário equivalente com WireMock está em NotificacaoWebhookWireMockIT.
 */
@ExtendWith(MockitoExtension.class)
class NotificacaoWebhookTest {

    @Mock private NotificacaoRepository repository;
    @InjectMocks private NotificacaoService service;

    private HttpServer servidor;
    private final List<String> corpos = new CopyOnWriteArrayList<>();
    private final List<String> tiposDeConteudo = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private volatile long atrasoMs = 0;

    @BeforeEach
    void iniciarServidor() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/notificacoes", troca -> {
            corpos.add(new String(
                troca.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
            ));
            tiposDeConteudo.add(
                troca.getRequestHeaders().getFirst("Content-Type")
            );

            try {
                Thread.sleep(atrasoMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            troca.sendResponseHeaders(status.get(), -1);
            troca.close();
        });
        servidor.start();
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/notificacoes";
    }

    private Reserva reserva() {
        Professor professor = new Professor();
        professor.setEmail("docente@faculdade.edu.br");
        Reserva reserva = new Reserva();
        reserva.setId(42L);
        reserva.setProfessor(professor);
        return reserva;
    }

    @Test
    @DisplayName("Com webhook configurado, envia JSON com reserva, destinatário e mensagem")
    void enviaWebhookComCorpoJson() {
        service.configurarWebhook(url(), 2000);

        service.enviar(reserva(), "reserva aprovada");

        assertEquals(1, corpos.size());
        assertTrue(corpos.get(0).contains("\"reservaId\":42"));
        assertTrue(corpos.get(0).contains("\"destinatario\":\"docente@faculdade.edu.br\""));
        assertTrue(corpos.get(0).contains("reserva aprovada"));
        assertTrue(tiposDeConteudo.get(0).startsWith("application/json"));
        verify(repository).save(any(Notificacao.class));
    }

    @Test
    @DisplayName("Caracteres especiais da mensagem são escapados no JSON")
    void escapaCaracteresEspeciais() {
        service.configurarWebhook(url(), 2000);

        service.enviar(reserva(), "aspas \" barra \\ quebra\nfim");

        String corpo = corpos.get(0);
        assertTrue(corpo.contains("aspas \\\" barra \\\\ quebra\\nfim"), corpo);
    }

    @Test
    @DisplayName("Webhook respondendo 500 não impede a notificação nem lança erro")
    void falhaDoWebhookNaoQuebraANotificacao() {
        status.set(500);
        service.configurarWebhook(url(), 2000);

        assertDoesNotThrow(() -> service.enviar(reserva(), "evento"));

        assertEquals(1, corpos.size());
        verify(repository).save(any(Notificacao.class));
    }

    @Test
    @DisplayName("Webhook fora do ar não quebra a notificação")
    void webhookIndisponivelNaoQuebra() {
        service.configurarWebhook("http://127.0.0.1:1/notificacoes", 500);

        assertDoesNotThrow(() -> service.enviar(reserva(), "evento"));

        verify(repository).save(any(Notificacao.class));
    }

    @Test
    @DisplayName("Webhook lento respeita o timeout configurado")
    void webhookLentoRespeitaTimeout() {
        atrasoMs = 1500;
        service.configurarWebhook(url(), 200);

        long antes = System.nanoTime();
        assertDoesNotThrow(() -> service.enviar(reserva(), "evento"));
        long duracaoMs = (System.nanoTime() - antes) / 1_000_000;

        assertTrue(duracaoMs < 1200, "demorou " + duracaoMs + " ms");
    }

    @Test
    @DisplayName("URL inválida é tolerada")
    void urlInvalidaNaoQuebra() {
        service.configurarWebhook("::url inválida::", 200);

        assertDoesNotThrow(() -> service.enviar(reserva(), "evento"));

        verify(repository).save(any(Notificacao.class));
    }

    @Test
    @DisplayName("Sem URL configurada nenhuma requisição é feita")
    void semUrlNaoEnviaNada() {
        service.configurarWebhook("", 200);

        service.enviar(reserva(), "evento");

        assertTrue(corpos.isEmpty());
        verify(repository).save(any(Notificacao.class));
    }
}
