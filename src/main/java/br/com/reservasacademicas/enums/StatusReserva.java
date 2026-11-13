package br.com.reservasacademicas.enums;

import java.util.List;

public enum StatusReserva {
    SOLICITADA,
    APROVADA,
    REJEITADA,
    EM_USO,
    CONCLUIDA,
    CANCELADA,
    NAO_COMPARECEU;

    private static final List<StatusReserva> FINAIS = List.of(
        REJEITADA,
        CANCELADA,
        CONCLUIDA,
        NAO_COMPARECEU
    );

    /** Estados terminais: a reserva não ocupa mais o recurso. */
    public static List<StatusReserva> finais() {
        return FINAIS;
    }
}
