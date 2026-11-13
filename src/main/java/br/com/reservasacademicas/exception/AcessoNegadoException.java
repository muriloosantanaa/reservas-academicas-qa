package br.com.reservasacademicas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Operação negada por falta de permissão (perfil ou titularidade).
 * Responde HTTP 403. Estende {@link RegraNegocioException} para manter a
 * compatibilidade com código que já tratava a negação como regra de negócio.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AcessoNegadoException extends RegraNegocioException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
