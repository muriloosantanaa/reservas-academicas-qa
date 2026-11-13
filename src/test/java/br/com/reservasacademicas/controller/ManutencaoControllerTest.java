package br.com.reservasacademicas.controller;

import br.com.reservasacademicas.entity.PeriodoManutencao;
import br.com.reservasacademicas.service.ManutencaoService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManutencaoControllerTest {

    @Mock private ManutencaoService service;
    @InjectMocks private ManutencaoController controller;

    @Test
    void listarDelegaAoServico() {
        when(service.listar()).thenReturn(List.of(new PeriodoManutencao()));

        assertEquals(1, controller.listar().size());
    }

    @Test
    void agendarRepassaUsuarioAutenticado() {
        PeriodoManutencao periodo = new PeriodoManutencao();
        when(service.agendar(periodo, "admin")).thenReturn(periodo);

        var auth = new UsernamePasswordAuthenticationToken("admin", "x", List.of());

        assertSame(periodo, controller.agendar(periodo, auth));
    }

    @Test
    void cancelarDelegaAoServico() {
        controller.cancelar(4L);

        verify(service).cancelar(4L);
    }
}
