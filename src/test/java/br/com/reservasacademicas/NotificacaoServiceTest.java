package br.com.reservasacademicas;

import br.com.reservasacademicas.entity.Notificacao;
import br.com.reservasacademicas.entity.Professor;
import br.com.reservasacademicas.entity.Reserva;
import br.com.reservasacademicas.repository.NotificacaoRepository;
import br.com.reservasacademicas.service.NotificacaoService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    @Mock private NotificacaoRepository repository;
    @InjectMocks private NotificacaoService service;

    @Test
    void enviarCriaNotificacaoComDestinatarioEMensagem() {
        Professor professor = new Professor();
        professor.setEmail("professor@faculdade.edu.br");
        Reserva reserva = new Reserva();
        reserva.setId(42L);
        reserva.setProfessor(professor);
        service.enviar(reserva, "reserva aprovada");
        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(repository).save(captor.capture());
        assertEquals(42L, captor.getValue().getReservaId());
        assertEquals("professor@faculdade.edu.br", captor.getValue().getDestinatario());
        assertTrue(captor.getValue().getMensagem().contains("reserva aprovada"));
    }

    @Test
    void listarConsultaNotificacoesDaReserva() {
        when(repository.findByReservaIdOrderByCriadaEmDesc(42L)).thenReturn(List.of());
        assertTrue(service.listar(42L).isEmpty());
    }
}
