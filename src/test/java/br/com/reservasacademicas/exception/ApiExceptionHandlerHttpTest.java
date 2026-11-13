package br.com.reservasacademicas.exception;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;

/** Erros de cliente devem ser 4xx; só falhas reais do servidor são 500. */
class ApiExceptionHandlerHttpTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private static void esperar(
        ResponseEntity<Map<String, Object>> resposta,
        HttpStatus status
    ) {
        assertEquals(status, resposta.getStatusCode());
        assertEquals(status.value(), resposta.getBody().get("status"));
        assertNotNull(resposta.getBody().get("message"));
        assertNotNull(resposta.getBody().get("timestamp"));
    }

    @Test
    void acessoNegadoRetorna403() {
        esperar(
            handler.forbidden(new AcessoNegadoException("sem permissão")),
            HttpStatus.FORBIDDEN
        );
    }

    @Test
    void accessDeniedDoSpringRetorna403() {
        esperar(
            handler.accessDenied(new AccessDeniedException("negado")),
            HttpStatus.FORBIDDEN
        );
    }

    @Test
    void acessoNegadoEhRegraDeNegocioMasOHandlerEspecificoVence() {
        assertInstanceOf(RegraNegocioException.class, new AcessoNegadoException("x"));
        assertInstanceOf(RegraNegocioException.class, new ConflitoHorarioException("x"));
    }

    @Test
    void jsonMalformadoRetorna400() {
        esperar(
            handler.unreadable(new HttpMessageNotReadableException("x", null, null)),
            HttpStatus.BAD_REQUEST
        );
    }

    @Test
    void parametroAusenteRetorna400() {
        esperar(
            handler.badParameter(new MissingServletRequestParameterException("capacidade", "int")),
            HttpStatus.BAD_REQUEST
        );
    }

    @Test
    void rotaInexistenteRetorna404() {
        esperar(
            handler.noResource(new NoResourceFoundException(HttpMethod.GET, "/api/x", "/api/x")),
            HttpStatus.NOT_FOUND
        );
    }

    @Test
    void metodoNaoPermitidoRetorna405() {
        esperar(
            handler.methodNotAllowed(new HttpRequestMethodNotSupportedException("DELETE")),
            HttpStatus.METHOD_NOT_ALLOWED
        );
    }

    @Test
    void tipoDeConteudoNaoSuportadoRetorna415() {
        esperar(
            handler.unsupportedMediaType(new HttpMediaTypeNotSupportedException("text/plain")),
            HttpStatus.UNSUPPORTED_MEDIA_TYPE
        );
    }

    @Test
    void violacaoDeIntegridadeRetorna409SemVazarSql() {
        var resposta = handler.integrity(
            new DataIntegrityViolationException("ERROR: conflicting key value violates exclusion constraint ex_reservas_sala")
        );

        esperar(resposta, HttpStatus.CONFLICT);
        assertFalse(resposta.getBody().get("message").toString().contains("ex_reservas_sala"));
    }

    @Test
    void falhaDeLockRetorna409() {
        esperar(
            handler.lockFailure(new CannotAcquireLockException("timeout")),
            HttpStatus.CONFLICT
        );
    }
}
