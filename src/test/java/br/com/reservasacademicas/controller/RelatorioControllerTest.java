package br.com.reservasacademicas.controller;

import br.com.reservasacademicas.entity.Professor;
import br.com.reservasacademicas.entity.Reserva;
import br.com.reservasacademicas.entity.Sala;
import br.com.reservasacademicas.enums.StatusReserva;
import br.com.reservasacademicas.repository.ConflitoEvitadoRepository;
import br.com.reservasacademicas.repository.ReservaRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelatorioControllerTest {
    @Test
    void calculaUtilizacaoEAgrupaPorSalaEProfessor() {
        ReservaRepository repository = mock(ReservaRepository.class);
        Sala sala = new Sala();
        sala.setNome("Laboratório");
        Professor professor = new Professor();
        professor.setNome("Docente A");
        Reserva reserva = new Reserva();
        reserva.setSala(sala);
        reserva.setProfessor(professor);
        reserva.setInicio(LocalDateTime.of(2026, 11, 10, 10, 0));
        reserva.setFim(LocalDateTime.of(2026, 11, 10, 12, 30));
        reserva.setStatus(StatusReserva.CONCLUIDA);
        when(repository.findAll()).thenReturn(List.of(reserva));

        var resultado = new RelatorioController(repository, mock(ConflitoEvitadoRepository.class)).utilizacao();
        assertEquals(1, resultado.get("totalReservas"));
        assertEquals(1L, resultado.get("reservasConcluidas"));
        assertEquals(2.5, (Double) resultado.get("cargaHorariaAlocada"), 0.001);
        assertEquals(1L, ((java.util.Map<?, ?>) resultado.get("porSala")).get("Laboratório"));
        assertEquals(1L, ((java.util.Map<?, ?>) resultado.get("porProfessor")).get("Docente A"));
    }

    @Test
    void relatorioVazioRetornaZeros() {
        ReservaRepository repository = mock(ReservaRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        var resultado = new RelatorioController(repository, mock(ConflitoEvitadoRepository.class)).utilizacao();
        assertEquals(0, resultado.get("totalReservas"));
        assertEquals(0L, resultado.get("reservasConcluidas"));
        assertEquals(0.0, (Double) resultado.get("cargaHorariaAlocada"), 0.001);
    }
}
