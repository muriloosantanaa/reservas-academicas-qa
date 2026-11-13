package br.com.reservasacademicas.service;

import br.com.reservasacademicas.entity.Notificacao;
import br.com.reservasacademicas.entity.Reserva;
import br.com.reservasacademicas.repository.NotificacaoRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Notificações de reserva.
 *
 * <p>Toda notificação é persistida (modo simulado). Se {@code
 * notificacao.webhook.url} estiver configurada, também é enviada por HTTP
 * POST (JSON) a esse endereço <b>depois do commit</b> da transação, com
 * timeout, e qualquer falha do webhook é registrada em log sem afetar a
 * reserva.
 */
@Service
public class NotificacaoService {

    private static final Logger LOG =
        LoggerFactory.getLogger(NotificacaoService.class);

    private final NotificacaoRepository notificacoes;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    @Value("${notificacao.webhook.url:}")
    private String webhookUrl;

    @Value("${notificacao.webhook.timeout-ms:2000}")
    private int timeoutMs = 2000;

    public NotificacaoService(NotificacaoRepository notificacoes) {
        this.notificacoes = notificacoes;
    }

    /** Permite configurar o webhook fora do Spring (testes). */
    public void configurarWebhook(String url, int timeoutEmMs) {
        this.webhookUrl = url;
        this.timeoutMs = timeoutEmMs;
    }

    public void enviar(Reserva reserva, String evento) {
        String destinatario = reserva.getProfessor().getEmail();
        String mensagem = "Reserva #" + reserva.getId() + ": " + evento;

        notificacoes.save(
            new Notificacao(reserva.getId(), destinatario, mensagem)
        );

        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        String corpo = montarJson(reserva.getId(), destinatario, mensagem);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        postar(corpo);
                    }
                }
            );
        } else {
            postar(corpo);
        }
    }

    public List<Notificacao> listar(Long reservaId) {
        return notificacoes.findByReservaIdOrderByCriadaEmDesc(reservaId);
    }

    private void postar(String corpo) {
        try {
            HttpRequest requisicao = HttpRequest
                .newBuilder(URI.create(webhookUrl))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build();

            HttpResponse<Void> resposta =
                http.send(requisicao, BodyHandlers.discarding());

            if (resposta.statusCode() >= 400) {
                LOG.warn(
                    "Webhook de notificação respondeu HTTP {}",
                    resposta.statusCode()
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Envio do webhook de notificação interrompido");
        } catch (IOException | RuntimeException e) {
            LOG.warn(
                "Falha ao enviar webhook de notificação: {}",
                e.getClass().getSimpleName()
            );
        }
    }

    static String montarJson(Long reservaId, String destinatario, String msg) {
        return "{\"reservaId\":" + reservaId
            + ",\"destinatario\":\"" + escapar(destinatario)
            + "\",\"mensagem\":\"" + escapar(msg) + "\"}";
    }

    static String escapar(String valor) {
        if (valor == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(valor.length() + 8);

        for (char c : valor.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }

        return sb.toString();
    }
}
