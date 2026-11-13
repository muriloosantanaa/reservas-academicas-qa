package br.com.reservasacademicas.exception;

import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Tradução centralizada de exceções para respostas JSON sem vazamento de
 * detalhes internos (stack trace, SQL, nomes de classes).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG =
        LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> notFound(
        RecursoNaoEncontradoException exception
    ) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<Map<String, Object>> forbidden(
        AcessoNegadoException exception
    ) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> accessDenied(
        AccessDeniedException exception
    ) {
        return response(
            HttpStatus.FORBIDDEN,
            "Você não tem permissão para executar esta operação"
        );
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, Object>> conflict(
        RegraNegocioException exception
    ) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(
        MethodArgumentNotValidException exception
    ) {
        String mensagem = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error ->
                error.getField() + ": " + error.getDefaultMessage()
            )
            .findFirst()
            .orElse("Dados inválidos");

        return response(HttpStatus.BAD_REQUEST, mensagem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(
        HttpMessageNotReadableException exception
    ) {
        return response(
            HttpStatus.BAD_REQUEST,
            "Corpo da requisição ausente ou malformado"
        );
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<Map<String, Object>> badParameter(Exception exception) {
        return response(
            HttpStatus.BAD_REQUEST,
            "Parâmetro de requisição ausente ou inválido"
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> noResource(
        NoResourceFoundException exception
    ) {
        return response(HttpStatus.NOT_FOUND, "Recurso não encontrado");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> methodNotAllowed(
        HttpRequestMethodNotSupportedException exception
    ) {
        return response(
            HttpStatus.METHOD_NOT_ALLOWED,
            "Método HTTP não permitido para este endereço"
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> unsupportedMediaType(
        HttpMediaTypeNotSupportedException exception
    ) {
        return response(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Tipo de conteúdo não suportado; utilize application/json"
        );
    }

    /**
     * Última linha de defesa contra dupla reserva: se o banco rejeitar a
     * gravação (restrição de exclusão ou unicidade), o cliente recebe 409.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(
        DataIntegrityViolationException exception
    ) {
        LOG.warn("Violação de integridade rejeitada pelo banco", exception);
        return response(
            HttpStatus.CONFLICT,
            "A operação conflita com outro registro (horário já ocupado ou dado duplicado)"
        );
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> lockFailure(
        PessimisticLockingFailureException exception
    ) {
        LOG.warn("Recurso bloqueado por outra solicitação", exception);
        return response(
            HttpStatus.CONFLICT,
            "Recurso em uso por outra solicitação simultânea; tente novamente"
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception exception) {
        LOG.error("Erro inesperado ao processar a requisição", exception);
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Erro interno. Verifique os dados e tente novamente."
        );
    }

    private ResponseEntity<Map<String, Object>> response(
        HttpStatus status,
        String mensagem
    ) {
        Map<String, Object> corpo = Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status", status.value(),
            "error", status.getReasonPhrase(),
            "message", mensagem
        );

        return ResponseEntity.status(status).body(corpo);
    }
}
