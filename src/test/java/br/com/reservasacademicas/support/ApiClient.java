package br.com.reservasacademicas.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cliente HTTP de caixa-preta para os testes de API e E2E.
 * Usa apenas o JDK (HttpClient) e Jackson, sem dependências extras.
 */
public final class ApiClient {

    public static final String ADMIN = "admin";
    public static final String RESPONSAVEL = "responsavel";
    public static final String PROFESSOR = "professor";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Resposta HTTP já com o corpo JSON interpretado (se houver). */
    public record Resposta(int status, JsonNode corpo) {
        public String texto(String campo) {
            return corpo.path(campo).asString();
        }

        public long numero(String campo) {
            return corpo.path(campo).asLong();
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private final String base;

    public ApiClient(int porta) {
        this.base = "http://localhost:" + porta;
    }

    // ---------------------------------------------------------------- HTTP

    public Resposta get(String usuario, String caminho) {
        return enviar("GET", usuario, caminho, null);
    }

    public Resposta post(String usuario, String caminho, String corpo) {
        return enviar("POST", usuario, caminho, corpo);
    }

    public Resposta put(String usuario, String caminho, String corpo) {
        return enviar("PUT", usuario, caminho, corpo);
    }

    public Resposta delete(String usuario, String caminho) {
        return enviar("DELETE", usuario, caminho, null);
    }

    /** Envia com cabeçalho Authorization arbitrário (ex.: senha errada). */
    public Resposta enviarComAutorizacao(
        String metodo,
        String caminho,
        String cabecalhoAuthorization
    ) {
        HttpRequest.Builder req = HttpRequest
            .newBuilder(URI.create(base + caminho))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json");

        if (cabecalhoAuthorization != null) {
            req.header("Authorization", cabecalhoAuthorization);
        }

        return executar(req.method(metodo, BodyPublishers.noBody()).build());
    }

    public static String basic(String usuario, String senha) {
        return "Basic " + Base64.getEncoder().encodeToString(
            (usuario + ":" + senha).getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String senhaDe(String usuario) {
        return switch (usuario) {
            case ADMIN -> "Admin123!";
            case RESPONSAVEL -> "Resp123!";
            case PROFESSOR -> "Prof123!";
            default -> throw new IllegalArgumentException(usuario);
        };
    }

    private Resposta enviar(
        String metodo,
        String usuario,
        String caminho,
        String corpo
    ) {
        HttpRequest.Builder req = HttpRequest
            .newBuilder(URI.create(base + caminho))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json");

        if (usuario != null) {
            req.header("Authorization", basic(usuario, senhaDe(usuario)));
        }

        BodyPublisher publisher = corpo == null
            ? BodyPublishers.noBody()
            : BodyPublishers.ofString(corpo, StandardCharsets.UTF_8);

        return executar(req.method(metodo, publisher).build());
    }

    private Resposta executar(HttpRequest requisicao) {
        try {
            HttpResponse<String> resposta =
                http.send(requisicao, BodyHandlers.ofString(StandardCharsets.UTF_8));
            String texto = resposta.body();
            JsonNode corpo = null;

            if (texto != null && !texto.isBlank()) {
                try {
                    corpo = JSON.readTree(texto);
                } catch (RuntimeException naoEhJson) {
                    corpo = JSON.createObjectNode().put("_texto", texto);
                }
            }

            if (corpo == null) {
                corpo = JSON.createObjectNode();
            }

            return new Resposta(resposta.statusCode(), corpo);
        } catch (IOException e) {
            throw new IllegalStateException("Falha de I/O na chamada HTTP", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chamada HTTP interrompida", e);
        }
    }

    // ---------------------------------------------------- dados de teste

    public long criarSala() {
        ObjectNode n = JSON.createObjectNode();
        n.put("nome", "Sala-" + sufixo());
        n.put("localizacao", "Bloco A");
        n.put("capacidade", 30);

        return exigirOk(post(ADMIN, "/api/admin/salas", n.toString())).numero("id");
    }

    public long criarProfessor() {
        ObjectNode n = JSON.createObjectNode();
        n.put("nome", "Prof-" + sufixo());
        n.put("email", sufixo() + "@faculdade.edu.br");
        n.put("competencia", "Java");

        return exigirOk(post(ADMIN, "/api/admin/professores", n.toString()))
            .numero("id");
    }

    public long criarMaterial(boolean restrito) {
        ObjectNode n = JSON.createObjectNode();
        n.put("nome", "Mat-" + sufixo());
        n.put("tipo", "PROJETOR");
        n.put("quantidade", 1);
        n.put("restrito", restrito);

        return exigirOk(post(ADMIN, "/api/admin/materiais", n.toString()))
            .numero("id");
    }

    /**
     * Altera um campo booleano de uma sala ou material via API administrativa
     * (ex.: {@code alterarFlag("salas", id, "emManutencao", true)}).
     */
    public void alterarFlag(String recurso, long id, String campo, boolean valor) {
        JsonNode atual = null;

        for (JsonNode item : exigirOk(get(ADMIN, "/api/" + recurso)).corpo()) {
            if (item.path("id").asLong() == id) {
                atual = item;
            }
        }

        if (atual == null) {
            throw new AssertionError(recurso + " " + id + " não encontrado");
        }

        ObjectNode copia = (ObjectNode) atual.deepCopy();
        copia.put(campo, valor);
        exigirOk(put(ADMIN, "/api/admin/" + recurso + "/" + id, copia.toString()));
    }

    public static String corpoReserva(
        long salaId,
        long professorId,
        Long materialId,
        LocalDateTime inicio,
        LocalDateTime fim
    ) {
        ObjectNode n = JSON.createObjectNode();
        n.putObject("sala").put("id", salaId);
        n.putObject("professor").put("id", professorId);

        if (materialId != null) {
            n.putObject("material").put("id", materialId);
        }

        n.put("inicio", inicio.withNano(0).toString());
        n.put("fim", fim.withNano(0).toString());
        n.put("finalidade", "Aula de teste");

        return n.toString();
    }

    public Resposta reservar(
        String usuario,
        long salaId,
        long professorId,
        Long materialId,
        LocalDateTime inicio,
        LocalDateTime fim
    ) {
        return post(
            usuario,
            "/api/reservas",
            corpoReserva(salaId, professorId, materialId, inicio, fim)
        );
    }

    /** Horário futuro determinístico: dia +{@code dias}, às {@code hora}h. */
    public static LocalDateTime horario(int dias, int hora) {
        return LocalDateTime.now()
            .plusDays(dias)
            .withHour(hora)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
    }

    public static Resposta exigirOk(Resposta resposta) {
        if (resposta.status() != 200 && resposta.status() != 201) {
            throw new AssertionError(
                "Esperado 2xx, recebido " + resposta.status() + ": " + resposta.corpo()
            );
        }

        return resposta;
    }

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
