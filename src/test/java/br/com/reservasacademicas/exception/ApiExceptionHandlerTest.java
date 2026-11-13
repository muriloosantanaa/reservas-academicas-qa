package br.com.reservasacademicas.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void recursoNaoEncontradoRetorna404() {
        var resposta = handler.notFound(new RecursoNaoEncontradoException("Não encontrado"));
        assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode());
        assertEquals("Não encontrado", resposta.getBody().get("message"));
    }

    @Test
    void regraDeNegocioRetorna409() {
        var resposta = handler.conflict(new RegraNegocioException("Conflito"));
        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("Conflito", resposta.getBody().get("message"));
    }

    @Test
    void excecaoGenericaRetorna500SemExporDetalhes() {
        var resposta = handler.generic(new IllegalStateException("segredo interno"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resposta.getStatusCode());
        assertFalse(resposta.getBody().get("message").toString().contains("segredo interno"));
    }
}
