package br.com.reservasacademicas.controller;

import br.com.reservasacademicas.entity.Material;
import br.com.reservasacademicas.entity.Professor;
import br.com.reservasacademicas.entity.Reserva;
import br.com.reservasacademicas.entity.Sala;
import br.com.reservasacademicas.enums.StatusReserva;
import br.com.reservasacademicas.repository.ConflitoEvitadoRepository;
import br.com.reservasacademicas.repository.ReservaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelatorioConflitosTest {

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final ConflitoEvitadoRepository conflitos =
        mock(ConflitoEvitadoRepository.class);
    private final RelatorioController controller =
        new RelatorioController(reservas, conflitos);

    private Reserva reserva(String sala, String prof, String mat, int horas, StatusReserva status) {
        Sala s = new Sala();
        s.setNome(sala);
        Professor p = new Professor();
        p.setNome(prof);
        Reserva r = new Reserva();
        r.setSala(s);
        r.setProfessor(p);
        if (mat != null) {
            Material m = new Material();
            m.setNome(mat);
            r.setMaterial(m);
        }
        LocalDateTime inicio = LocalDateTime.of(2026, 12, 1, 8, 0);
        r.setInicio(inicio);
        r.setFim(inicio.plusHours(horas));
        r.setStatus(status);
        return r;
    }

    @Test
    void reservasCanceladasRejeitadasENaoCompareceuNaoContamComoUtilizacao() {
        when(reservas.findAll()).thenReturn(List.of(
            reserva("Lab", "Ana", null, 2, StatusReserva.CONCLUIDA),
            reserva("Lab", "Ana", null, 3, StatusReserva.CANCELADA),
            reserva("Lab", "Ana", null, 4, StatusReserva.REJEITADA),
            reserva("Lab", "Ana", null, 5, StatusReserva.NAO_COMPARECEU),
            reserva("Lab", "Ana", null, 1, StatusReserva.APROVADA)
        ));

        var r = controller.utilizacao();

        assertEquals(5, r.get("totalReservas"));
        assertEquals(3.0, (Double) r.get("cargaHorariaAlocada"), 0.001);
        assertEquals(2L, ((Map<?, ?>) r.get("porSala")).get("Lab"));
        assertEquals(3.0, (Double) ((Map<?, ?>) r.get("cargaHorariaPorProfessor")).get("Ana"), 0.001);
    }

    @Test
    void agrupaPorMaterialIgnorandoReservasSemMaterial() {
        when(reservas.findAll()).thenReturn(List.of(
            reserva("Lab", "Ana", "Projetor", 1, StatusReserva.APROVADA),
            reserva("Lab", "Bia", "Projetor", 1, StatusReserva.APROVADA),
            reserva("Lab", "Bia", null, 1, StatusReserva.APROVADA)
        ));

        var porMaterial = (Map<?, ?>) controller.utilizacao().get("porMaterial");

        assertEquals(1, porMaterial.size());
        assertEquals(2L, porMaterial.get("Projetor"));
    }

    @Test
    void contagemPorStatusIncluiTodosOsEstados() {
        when(reservas.findAll()).thenReturn(List.of(
            reserva("Lab", "Ana", null, 1, StatusReserva.APROVADA),
            reserva("Lab", "Ana", null, 1, StatusReserva.APROVADA)
        ));

        var porStatus = (Map<?, ?>) controller.utilizacao().get("porStatus");

        assertEquals(StatusReserva.values().length, porStatus.size());
        assertEquals(2L, porStatus.get("APROVADA"));
        assertEquals(0L, porStatus.get("CANCELADA"));
    }

    @Test
    void informaConflitosEvitadosTotaisEPorTipoDeRecurso() {
        when(reservas.findAll()).thenReturn(List.of());
        when(conflitos.count()).thenReturn(7L);
        when(conflitos.contarPorTipo()).thenReturn(List.of(
            new Object[] {br.com.reservasacademicas.enums.TipoRecurso.SALA, 5L},
            new Object[] {br.com.reservasacademicas.enums.TipoRecurso.PROFESSOR, 2L}
        ));

        var r = controller.utilizacao();

        assertEquals(7L, r.get("conflitosEvitados"));
        var porTipo = (Map<?, ?>) r.get("conflitosEvitadosPorRecurso");
        assertEquals(5L, porTipo.get("SALA"));
        assertEquals(2L, porTipo.get("PROFESSOR"));
    }
}
