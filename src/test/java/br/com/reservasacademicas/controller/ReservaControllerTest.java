package br.com.reservasacademicas.controller;

import br.com.reservasacademicas.entity.Reserva;
import br.com.reservasacademicas.enums.StatusReserva;
import br.com.reservasacademicas.service.ReservaService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservaControllerTest {
    private ReservaService service;
    private ReservaController controller;
    private Authentication auth;

    @BeforeEach
    void configurar() {
        service = mock(ReservaService.class);
        controller = new ReservaController(service);
        auth = new UsernamePasswordAuthenticationToken("docente", "senha");
    }

    @Test
    void consultaECriacaoDelegamAoServico() {
        Reserva reserva = new Reserva();
        when(service.listar(auth)).thenReturn(List.of(reserva));
        when(service.buscarAutorizada(1L, auth)).thenReturn(reserva);
        when(service.criar(reserva, "docente")).thenReturn(reserva);
        assertEquals(1, controller.listar(auth).size());
        assertSame(reserva, controller.buscar(1L, auth));
        assertSame(reserva, controller.criar(reserva, auth));
    }

    @Test
    void alteracaoEDemaisOperacoesDelegamAoServico() {
        Reserva reserva = new Reserva();
        when(service.alterar(1L, reserva, auth)).thenReturn(reserva);
        when(service.mudarStatus(eq(1L), any(StatusReserva.class), eq(auth))).thenReturn(reserva);
        when(service.registrarRetirada(1L, auth)).thenReturn(reserva);
        when(service.registrarDevolucao(1L, auth)).thenReturn(reserva);
        when(service.historico(1L, auth)).thenReturn(List.of());
        when(service.notificacoes(1L)).thenReturn(List.of());
        when(service.buscarAutorizada(1L, auth)).thenReturn(reserva);

        assertSame(reserva, controller.alterar(1L, reserva, auth));
        assertSame(reserva, controller.aprovar(1L, auth));
        assertSame(reserva, controller.rejeitar(1L, auth));
        assertSame(reserva, controller.iniciar(1L, auth));
        assertSame(reserva, controller.concluir(1L, auth));
        assertSame(reserva, controller.naoCompareceu(1L, auth));
        assertSame(reserva, controller.cancelar(1L, auth));
        assertSame(reserva, controller.retirada(1L, auth));
        assertSame(reserva, controller.devolucao(1L, auth));
        assertTrue(controller.historico(1L, auth).isEmpty());
        assertTrue(controller.notificacoes(1L, auth).isEmpty());
        verify(service, times(6)).mudarStatus(eq(1L), any(StatusReserva.class), eq(auth));
    }
}
