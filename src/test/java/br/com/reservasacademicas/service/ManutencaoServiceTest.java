package br.com.reservasacademicas.service;

import br.com.reservasacademicas.entity.PeriodoManutencao;
import br.com.reservasacademicas.entity.Sala;
import br.com.reservasacademicas.entity.Material;
import br.com.reservasacademicas.enums.TipoRecurso;
import br.com.reservasacademicas.exception.RecursoNaoEncontradoException;
import br.com.reservasacademicas.exception.RegraNegocioException;
import br.com.reservasacademicas.repository.MaterialRepository;
import br.com.reservasacademicas.repository.PeriodoManutencaoRepository;
import br.com.reservasacademicas.repository.ReservaRepository;
import br.com.reservasacademicas.repository.SalaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Funcionalidade desenvolvida com BDD/TDD: períodos de manutenção.
 * Cenários em docs/TDD_BDD.md (Dado / Quando / Então).
 */
@ExtendWith(MockitoExtension.class)
class ManutencaoServiceTest {

    @Mock private PeriodoManutencaoRepository periodos;
    @Mock private ReservaRepository reservas;
    @Mock private SalaRepository salas;
    @Mock private MaterialRepository materiais;

    @InjectMocks private ManutencaoService service;

    private LocalDateTime inicio;
    private LocalDateTime fim;

    @BeforeEach
    void configurar() {
        inicio = LocalDateTime.now().plusDays(10);
        fim = inicio.plusDays(2);
    }

    @Test
    @DisplayName("Dado uma sala sem reservas, quando o admin agenda manutenção, então o período é salvo")
    void agendaManutencaoDeSalaSemReservas() {
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(new Sala()));
        when(reservas.salaOcupada(eq(1L), eq(inicio), eq(fim), anyList(), isNull()))
            .thenReturn(false);
        when(periodos.save(any(PeriodoManutencao.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        PeriodoManutencao salvo =
            service.agendar(dados(TipoRecurso.SALA, 1L), "admin");

        assertEquals(TipoRecurso.SALA, salvo.getTipoRecurso());
        assertEquals(1L, salvo.getRecursoId());
        assertEquals("admin", salvo.getCriadoPor());
        assertEquals("Troca do ar-condicionado", salvo.getMotivo());
    }

    @Test
    @DisplayName("Dado um material sem reservas, quando o admin agenda manutenção, então o período é salvo")
    void agendaManutencaoDeMaterialSemReservas() {
        when(materiais.findByIdForUpdate(5L)).thenReturn(Optional.of(new Material()));
        when(reservas.materialOcupado(eq(5L), eq(inicio), eq(fim), anyList(), isNull()))
            .thenReturn(false);
        when(periodos.save(any(PeriodoManutencao.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        assertEquals(
            TipoRecurso.MATERIAL,
            service.agendar(dados(TipoRecurso.MATERIAL, 5L), "admin")
                .getTipoRecurso()
        );
    }

    @Test
    @DisplayName("Dado reservas ativas no período, quando agenda manutenção de sala, então é recusado")
    void recusaManutencaoDeSalaComReservasAtivas() {
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(new Sala()));
        when(reservas.salaOcupada(eq(1L), any(), any(), anyList(), isNull()))
            .thenReturn(true);

        RegraNegocioException erro = assertThrows(
            RegraNegocioException.class,
            () -> service.agendar(dados(TipoRecurso.SALA, 1L), "admin")
        );

        assertTrue(erro.getMessage().contains("reservas ativas"));
        verify(periodos, never()).save(any());
    }

    @Test
    @DisplayName("Dado reservas ativas no período, quando agenda manutenção de material, então é recusado")
    void recusaManutencaoDeMaterialComReservasAtivas() {
        when(materiais.findByIdForUpdate(5L)).thenReturn(Optional.of(new Material()));
        when(reservas.materialOcupado(eq(5L), any(), any(), anyList(), isNull()))
            .thenReturn(true);

        assertThrows(
            RegraNegocioException.class,
            () -> service.agendar(dados(TipoRecurso.MATERIAL, 5L), "admin")
        );
        verify(periodos, never()).save(any());
    }

    @Test
    @DisplayName("Professor não é um recurso que entra em manutenção")
    void recusaManutencaoDeProfessor() {
        assertThrows(
            RegraNegocioException.class,
            () -> service.agendar(dados(TipoRecurso.PROFESSOR, 2L), "admin")
        );
        verifyNoMoreInteractions(periodos, reservas, salas, materiais);
    }

    @Test
    @DisplayName("Tipo de recurso ausente é recusado")
    void recusaTipoAusente() {
        assertThrows(
            RegraNegocioException.class,
            () -> service.agendar(dados(null, 1L), "admin")
        );
    }

    @ParameterizedTest(name = "início +{0}d, fim +{1}d é recusado")
    @CsvSource({"10,10", "10,9", "5,1"})
    void recusaPeriodoComTerminoNaoPosteriorAoInicio(long i, long f) {
        LocalDateTime base = LocalDateTime.now();
        PeriodoManutencao dados = dados(TipoRecurso.SALA, 1L);
        dados.setInicio(base.plusDays(i));
        dados.setFim(base.plusDays(f));

        assertThrows(
            RegraNegocioException.class,
            () -> service.agendar(dados, "admin")
        );
        verify(periodos, never()).save(any());
    }

    @Test
    @DisplayName("Período sem datas é recusado")
    void recusaPeriodoSemDatas() {
        PeriodoManutencao dados = dados(TipoRecurso.SALA, 1L);
        dados.setInicio(null);

        assertThrows(
            RegraNegocioException.class,
            () -> service.agendar(dados, "admin")
        );
    }

    @Test
    @DisplayName("Sala inexistente gera 404")
    void salaInexistente() {
        when(salas.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(
            RecursoNaoEncontradoException.class,
            () -> service.agendar(dados(TipoRecurso.SALA, 99L), "admin")
        );
    }

    @Test
    @DisplayName("Material inexistente gera 404")
    void materialInexistente() {
        when(materiais.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(
            RecursoNaoEncontradoException.class,
            () -> service.agendar(dados(TipoRecurso.MATERIAL, 99L), "admin")
        );
    }

    @Test
    @DisplayName("Cancelar uma manutenção remove o período")
    void cancelaManutencao() {
        PeriodoManutencao existente = dados(TipoRecurso.SALA, 1L);
        when(periodos.findById(7L)).thenReturn(Optional.of(existente));

        service.cancelar(7L);

        verify(periodos).delete(existente);
    }

    @Test
    @DisplayName("Cancelar manutenção inexistente gera 404")
    void cancelaManutencaoInexistente() {
        when(periodos.findById(7L)).thenReturn(Optional.empty());

        assertThrows(
            RecursoNaoEncontradoException.class,
            () -> service.cancelar(7L)
        );
    }

    @Test
    @DisplayName("bloqueado() delega a consulta de sobreposição ao repositório")
    void bloqueadoConsultaRepositorio() {
        when(periodos.existeSobreposicao(TipoRecurso.SALA, 1L, inicio, fim))
            .thenReturn(true);

        assertTrue(service.bloqueado(TipoRecurso.SALA, 1L, inicio, fim));
        assertFalse(service.bloqueado(TipoRecurso.SALA, 2L, inicio, fim));
    }

    @Test
    @DisplayName("listar devolve os períodos em ordem de início")
    void listaPeriodos() {
        when(periodos.findAllByOrderByInicioAsc())
            .thenReturn(List.of(dados(TipoRecurso.SALA, 1L)));

        assertEquals(1, service.listar().size());
    }

    private PeriodoManutencao dados(TipoRecurso tipo, Long recursoId) {
        PeriodoManutencao p = new PeriodoManutencao();
        p.setTipoRecurso(tipo);
        p.setRecursoId(recursoId);
        p.setInicio(inicio);
        p.setFim(fim);
        p.setMotivo("Troca do ar-condicionado");
        return p;
    }
}
