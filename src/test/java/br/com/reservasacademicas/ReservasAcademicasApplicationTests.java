package br.com.reservasacademicas;

import br.com.reservasacademicas.entity.Reserva;
import br.com.reservasacademicas.exception.RegraNegocioException;
import br.com.reservasacademicas.service.ReservaService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ReservasAcademicasApplicationTests {

    @Autowired
    private ReservaService reservaService;

    @Test
    void contextoDaAplicacaoDeveCarregar() {
        // O contexto é validado pela inicialização do Spring.
    }

    @Test
    void deveRejeitarReservaComTerminoAnteriorAoInicio() {
        Reserva reserva = new Reserva();
        reserva.setInicio(LocalDateTime.now().plusHours(2));
        reserva.setFim(LocalDateTime.now().plusHours(1));

        assertThrows(
            RegraNegocioException.class,
            () -> reservaService.criar(reserva, "professor")
        );
    }

    @Test
    void deveRejeitarReservaComMesmoHorarioDeInicioEFim() {
        LocalDateTime inicio = LocalDateTime.now().plusHours(2);

        Reserva reserva = new Reserva();
        reserva.setInicio(inicio);
        reserva.setFim(inicio);

        assertThrows(
            RegraNegocioException.class,
            () -> reservaService.criar(reserva, "professor")
        );
    }
}
