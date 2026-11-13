package br.com.reservasacademicas.exception;

/**
 * Sobreposição de horário detectada para sala, professor ou material.
 * É uma {@link RegraNegocioException} (HTTP 409). Transações que a lançam
 * não sofrem rollback, para que o conflito evitado seja registrado.
 */
public class ConflitoHorarioException extends RegraNegocioException {

    public ConflitoHorarioException(String mensagem) {
        super(mensagem);
    }
}
