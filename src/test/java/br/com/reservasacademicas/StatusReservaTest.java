package br.com.reservasacademicas;

import br.com.reservasacademicas.enums.StatusReserva;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusReservaTest {

    @Test
    void estadosFinaisSaoOsQueLiberamORecurso() {
        assertEquals(
            List.of(
                StatusReserva.REJEITADA,
                StatusReserva.CANCELADA,
                StatusReserva.CONCLUIDA,
                StatusReserva.NAO_COMPARECEU
            ),
            StatusReserva.finais()
        );
    }

    @Test
    void estadosAtivosNaoSaoFinais() {
        assertFalse(StatusReserva.finais().contains(StatusReserva.SOLICITADA));
        assertFalse(StatusReserva.finais().contains(StatusReserva.APROVADA));
        assertFalse(StatusReserva.finais().contains(StatusReserva.EM_USO));
    }

    @Test
    void listaDeFinaisNaoPodeSerAlterada() {
        assertThrows(
            UnsupportedOperationException.class,
            () -> StatusReserva.finais().add(StatusReserva.APROVADA)
        );
    }
}
