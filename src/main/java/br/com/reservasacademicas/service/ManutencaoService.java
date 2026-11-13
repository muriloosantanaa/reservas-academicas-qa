package br.com.reservasacademicas.service;

import br.com.reservasacademicas.entity.PeriodoManutencao;
import br.com.reservasacademicas.enums.StatusReserva;
import br.com.reservasacademicas.enums.TipoRecurso;
import br.com.reservasacademicas.exception.RecursoNaoEncontradoException;
import br.com.reservasacademicas.exception.RegraNegocioException;
import br.com.reservasacademicas.repository.MaterialRepository;
import br.com.reservasacademicas.repository.PeriodoManutencaoRepository;
import br.com.reservasacademicas.repository.ReservaRepository;
import br.com.reservasacademicas.repository.SalaRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Períodos de manutenção programada de salas e materiais.
 *
 * <p>Regras: o término deve ser posterior ao início; só sala e material
 * podem entrar em manutenção; não se agenda manutenção sobre reservas ativas
 * (elas precisam ser canceladas antes); reservas novas não podem ocupar o
 * recurso durante o período.
 */
@Service
public class ManutencaoService {

    private final PeriodoManutencaoRepository periodos;
    private final ReservaRepository reservas;
    private final SalaRepository salas;
    private final MaterialRepository materiais;

    public ManutencaoService(
        PeriodoManutencaoRepository periodos,
        ReservaRepository reservas,
        SalaRepository salas,
        MaterialRepository materiais
    ) {
        this.periodos = periodos;
        this.reservas = reservas;
        this.salas = salas;
        this.materiais = materiais;
    }

    public List<PeriodoManutencao> listar() {
        return periodos.findAllByOrderByInicioAsc();
    }

    @Transactional
    public PeriodoManutencao agendar(PeriodoManutencao dados, String usuario) {
        if (
            dados.getTipoRecurso() == null ||
            dados.getTipoRecurso() == TipoRecurso.PROFESSOR
        ) {
            throw new RegraNegocioException(
                "A manutenção só pode ser agendada para sala ou material"
            );
        }

        if (
            dados.getInicio() == null ||
            dados.getFim() == null ||
            !dados.getFim().isAfter(dados.getInicio())
        ) {
            throw new RegraNegocioException(
                "O término deve ser posterior ao início"
            );
        }

        Long recursoId = dados.getRecursoId();
        boolean reservasAtivas;

        // O lock do recurso serializa o agendamento com novas reservas dele.
        if (dados.getTipoRecurso() == TipoRecurso.SALA) {
            salas.findByIdForUpdate(recursoId).orElseThrow(() ->
                new RecursoNaoEncontradoException("Sala não encontrada")
            );
            reservasAtivas = reservas.salaOcupada(
                recursoId,
                dados.getInicio(),
                dados.getFim(),
                StatusReserva.finais(),
                null
            );
        } else {
            materiais.findByIdForUpdate(recursoId).orElseThrow(() ->
                new RecursoNaoEncontradoException("Material não encontrado")
            );
            reservasAtivas = reservas.materialOcupado(
                recursoId,
                dados.getInicio(),
                dados.getFim(),
                StatusReserva.finais(),
                null
            );
        }

        if (reservasAtivas) {
            throw new RegraNegocioException(
                "Existem reservas ativas no período; cancele-as antes de agendar a manutenção"
            );
        }

        PeriodoManutencao novo = new PeriodoManutencao();
        novo.setTipoRecurso(dados.getTipoRecurso());
        novo.setRecursoId(recursoId);
        novo.setInicio(dados.getInicio());
        novo.setFim(dados.getFim());
        novo.setMotivo(dados.getMotivo());
        novo.setCriadoPor(usuario);

        return periodos.save(novo);
    }

    @Transactional
    public void cancelar(Long id) {
        PeriodoManutencao periodo = periodos.findById(id).orElseThrow(() ->
            new RecursoNaoEncontradoException(
                "Período de manutenção não encontrado"
            )
        );

        periodos.delete(periodo);
    }

    /** Indica se o recurso está em manutenção programada no intervalo. */
    public boolean bloqueado(
        TipoRecurso tipo,
        Long recursoId,
        LocalDateTime inicio,
        LocalDateTime fim
    ) {
        return periodos.existeSobreposicao(tipo, recursoId, inicio, fim);
    }
}
