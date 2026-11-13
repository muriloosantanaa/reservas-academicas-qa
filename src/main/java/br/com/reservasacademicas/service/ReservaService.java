
package br.com.reservasacademicas.service;

import br.com.reservasacademicas.entity.*;
import br.com.reservasacademicas.enums.StatusReserva;
import br.com.reservasacademicas.enums.TipoRecurso;
import br.com.reservasacademicas.exception.*;
import br.com.reservasacademicas.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReservaService {

    private static final List<StatusReserva> FINAIS =
        StatusReserva.finais();

    private final ReservaRepository reservas;
    private final SalaRepository salas;
    private final ProfessorRepository professores;
    private final MaterialRepository materiais;
    private final AuditoriaRepository auditorias;
    private final NotificacaoService notificacoes;
    private final ManutencaoService manutencoes;
    private final ConflitoEvitadoRepository conflitos;

    public ReservaService(
        ReservaRepository r,
        SalaRepository s,
        ProfessorRepository p,
        MaterialRepository m,
        AuditoriaRepository a,
        NotificacaoService n,
        ManutencaoService manutencao,
        ConflitoEvitadoRepository conflitosEvitados
    ) {
        reservas = r;
        salas = s;
        professores = p;
        materiais = m;
        auditorias = a;
        notificacoes = n;
        manutencoes = manutencao;
        conflitos = conflitosEvitados;
    }

    public List<Reserva> listar(Authentication autenticacao) {
        if (ehResponsavel(autenticacao)) {
            return reservas.findAllByOrderByInicioDesc();
        }

        return reservas.findBySolicitanteUsuarioOrderByInicioDesc(
            autenticacao.getName()
        );
    }

    public Reserva buscarAutorizada(
        Long id,
        Authentication autenticacao
    ) {
        Reserva reserva = buscar(id);
        exigirDonoOuResponsavel(reserva, autenticacao);
        return reserva;
    }

    public List<Notificacao> notificacoes(Long id) {
        return notificacoes.listar(id);
    }

    public Reserva buscar(Long id) {
        return reservas.findById(id)
            .orElseThrow(() ->
                new RecursoNaoEncontradoException("Reserva não encontrada")
            );
    }

    private Reserva buscarParaAtualizacao(Long id) {
        return reservas.findByIdForUpdate(id)
            .orElseThrow(() ->
                new RecursoNaoEncontradoException("Reserva não encontrada")
            );
    }

    /**
     * Cria a reserva. {@code noRollbackFor}: a única escrita feita antes de um
     * {@link ConflitoHorarioException} é o registro do conflito evitado, que
     * deve ser confirmado junto com a resposta 409.
     */
    @Transactional(noRollbackFor = ConflitoHorarioException.class)
    public Reserva criar(Reserva r, String usuario) {
        validarPeriodo(r.getInicio(), r.getFim());

        if (
            r.getSala() == null || r.getSala().getId() == null ||
            r.getProfessor() == null || r.getProfessor().getId() == null
        ) {
            throw new RegraNegocioException(
                "Sala e professor devem ser informados"
            );
        }

        if (
            r.getMaterial() != null &&
            r.getMaterial().getId() == null
        ) {
            throw new RegraNegocioException(
                "O material informado é inválido"
            );
        }

        // Nunca confiar em campos de controle vindos do cliente: um "id"
        // preenchido faria o save() sobrescrever outra reserva.
        r.setId(null);
        r.setRetiradaEm(null);
        r.setDevolvidaEm(null);

        Sala sala = salas.findByIdForUpdate(r.getSala().getId())
            .orElseThrow(() ->
                new RecursoNaoEncontradoException("Sala não encontrada")
            );

        Professor prof = professores.findByIdForUpdate(r.getProfessor().getId())
            .orElseThrow(() ->
                new RecursoNaoEncontradoException("Professor não encontrado")
            );

        if (!sala.isAtiva() || sala.isEmManutencao() || !prof.isAtivo()) {
            throw new RegraNegocioException(
                "Sala em manutenção/inativa ou professor inativo"
            );
        }

        r.setSala(sala);
        r.setProfessor(prof);
        r.setSolicitanteUsuario(usuario);

        if (r.getMaterial() != null) {
            Material mat = materiais.findByIdForUpdate(r.getMaterial().getId())
                .orElseThrow(() ->
                    new RecursoNaoEncontradoException("Material não encontrado")
                );

            if (!mat.isDisponivel() || mat.isEmManutencao()) {
                throw new RegraNegocioException(
                    "Material indisponível ou em manutenção"
                );
            }

            r.setMaterial(mat);
        }

        exigirForaDeManutencaoProgramada(
            sala,
            r.getMaterial(),
            r.getInicio(),
            r.getFim()
        );

        checarConflitos(
            sala.getId(),
            prof.getId(),
            r.getMaterial() == null ? null : r.getMaterial().getId(),
            r.getInicio(),
            r.getFim(),
            null,
            usuario
        );

        r.setStatus(
            r.getMaterial() != null && r.getMaterial().isRestrito()
                ? StatusReserva.SOLICITADA
                : StatusReserva.APROVADA
        );

        Reserva salvo = reservas.save(r);

        auditar(salvo, "CRIADA - " + salvo.getStatus(), usuario);
        notificacoes.enviar(
            salvo,
            "solicitação criada com status " + salvo.getStatus()
        );

        return salvo;
    }

    /**
     * Altera período e finalidade. A entidade só é modificada depois de todas
     * as validações, pois um conflito não provoca rollback (ver criar).
     */
    @Transactional(noRollbackFor = ConflitoHorarioException.class)
    public Reserva alterar(
        Long id,
        Reserva dados,
        Authentication autenticacao
    ) {
        Reserva atual = buscarParaAtualizacao(id);

        exigirDonoOuResponsavel(atual, autenticacao);

        if (
            atual.getStatus() == StatusReserva.EM_USO ||
            atual.getStatus() == StatusReserva.CONCLUIDA
        ) {
            throw new RegraNegocioException(
                "Reserva iniciada ou concluída não pode ser alterada"
            );
        }

        if (FINAIS.contains(atual.getStatus())) {
            throw new RegraNegocioException(
                "Reserva em estado final não pode ser alterada"
            );
        }

        validarPeriodo(dados.getInicio(), dados.getFim());

        Sala sala = salas.findByIdForUpdate(atual.getSala().getId())
            .orElseThrow(() ->
                new RecursoNaoEncontradoException("Sala não encontrada")
            );
        Professor prof = professores
            .findByIdForUpdate(atual.getProfessor().getId())
            .orElseThrow(() ->
                new RecursoNaoEncontradoException("Professor não encontrado")
            );
        Material mat = null;

        if (atual.getMaterial() != null) {
            mat = materiais.findByIdForUpdate(atual.getMaterial().getId())
                .orElseThrow(() ->
                    new RecursoNaoEncontradoException("Material não encontrado")
                );
        }

        if (!sala.isAtiva() || sala.isEmManutencao() || !prof.isAtivo()) {
            throw new RegraNegocioException(
                "Sala em manutenção/inativa ou professor inativo"
            );
        }

        if (mat != null && (!mat.isDisponivel() || mat.isEmManutencao())) {
            throw new RegraNegocioException(
                "Material indisponível ou em manutenção"
            );
        }

        exigirForaDeManutencaoProgramada(
            sala,
            mat,
            dados.getInicio(),
            dados.getFim()
        );

        String usuario = autenticacao.getName();

        checarConflitos(
            sala.getId(),
            prof.getId(),
            mat == null ? null : mat.getId(),
            dados.getInicio(),
            dados.getFim(),
            id,
            usuario
        );

        boolean periodoMudou =
            !dados.getInicio().equals(atual.getInicio()) ||
            !dados.getFim().equals(atual.getFim());

        atual.setInicio(dados.getInicio());
        atual.setFim(dados.getFim());
        atual.setFinalidade(dados.getFinalidade());

        // Mover uma reserva já aprovada de recurso restrito exige nova aprovação.
        boolean exigeReaprovacao =
            periodoMudou &&
            atual.getStatus() == StatusReserva.APROVADA &&
            mat != null &&
            mat.isRestrito();

        if (exigeReaprovacao) {
            atual.setStatus(StatusReserva.SOLICITADA);
        }

        Reserva salvo = reservas.save(atual);

        auditar(
            salvo,
            exigeReaprovacao ? "ALTERADA - REAPROVAÇÃO EXIGIDA" : "ALTERADA",
            usuario
        );
        notificacoes.enviar(
            salvo,
            exigeReaprovacao
                ? "reserva alterada; nova aprovação necessária"
                : "reserva alterada"
        );

        return salvo;
    }

    @Transactional
    public Reserva mudarStatus(
        Long id,
        StatusReserva status,
        Authentication autenticacao
    ) {
        Reserva r = buscarParaAtualizacao(id);

        String usuario = autenticacao.getName();

        if (status == StatusReserva.SOLICITADA) {
            throw new RegraNegocioException("Transição de estado inválida");
        }

        if (FINAIS.contains(r.getStatus())) {
            throw new RegraNegocioException("Reserva em estado final");
        }

        boolean responsavel = ehResponsavel(autenticacao);

        if (
            status == StatusReserva.APROVADA ||
            status == StatusReserva.REJEITADA ||
            status == StatusReserva.EM_USO ||
            status == StatusReserva.CONCLUIDA ||
            status == StatusReserva.NAO_COMPARECEU
        ) {
            if (!responsavel) {
                throw new AcessoNegadoException(
                    "Apenas responsáveis podem executar esta operação"
                );
            }
        }

        if (status == StatusReserva.CANCELADA) {
            exigirDonoOuResponsavel(r, autenticacao);
        }

        boolean transicaoValida = switch (status) {
            case APROVADA, REJEITADA ->
                r.getStatus() == StatusReserva.SOLICITADA;

            case EM_USO ->
                r.getStatus() == StatusReserva.APROVADA;

            case CONCLUIDA ->
                r.getStatus() == StatusReserva.EM_USO;

            case CANCELADA ->
                r.getStatus() == StatusReserva.SOLICITADA
                || r.getStatus() == StatusReserva.APROVADA;

            case NAO_COMPARECEU ->
                r.getStatus() == StatusReserva.APROVADA;

            case SOLICITADA -> false;
        };

        if (!transicaoValida) {
            throw new RegraNegocioException(
                "Transição de status inválida: "
                + r.getStatus() + " → " + status
            );
        }

        r.setStatus(status);

        reservas.save(r);
        auditar(r, "STATUS: " + status, usuario);
        notificacoes.enviar(r, "status alterado para " + status);

        return r;
    }

    @Transactional
    public Reserva registrarRetirada(
        Long id,
        Authentication autenticacao
    ) {
        exigirPerfilResponsavel(autenticacao);
        String usuario = autenticacao.getName();
        Reserva r = buscarParaAtualizacao(id);

        if (r.getMaterial() == null) {
            throw new RegraNegocioException("Reserva não possui material");
        }

        if (r.getStatus() != StatusReserva.EM_USO) {
            throw new RegraNegocioException(
                "Retirada só pode ser registrada durante o uso"
            );
        }

        if (r.getRetiradaEm() != null) {
            throw new RegraNegocioException("Retirada já registrada");
        }

        r.setRetiradaEm(LocalDateTime.now());

        auditar(r, "RETIRADA DE MATERIAL", usuario);

        return reservas.save(r);
    }

    @Transactional
    public Reserva registrarDevolucao(
        Long id,
        Authentication autenticacao
    ) {
        exigirPerfilResponsavel(autenticacao);
        String usuario = autenticacao.getName();
        Reserva r = buscarParaAtualizacao(id);

        if (r.getMaterial() == null) {
            throw new RegraNegocioException("Reserva não possui material");
        }

        if (r.getRetiradaEm() == null) {
            throw new RegraNegocioException(
                "Retirada deve ser registrada antes da devolução"
            );
        }

        if (r.getDevolvidaEm() != null) {
            throw new RegraNegocioException("Devolução já registrada");
        }

        r.setDevolvidaEm(LocalDateTime.now());

        auditar(r, "DEVOLUÇÃO DE MATERIAL", usuario);
        notificacoes.enviar(r, "material devolvido");

        return reservas.save(r);
    }

    private void exigirDonoOuResponsavel(
        Reserva reserva,
        Authentication autenticacao
    ) {
        boolean responsavel = ehResponsavel(autenticacao);

        boolean dono = reserva.getSolicitanteUsuario() != null
            && reserva.getSolicitanteUsuario().equals(autenticacao.getName());

        if (!dono && !responsavel) {
            throw new AcessoNegadoException(
                "Você não tem permissão para acessar esta reserva"
            );
        }
    }

    private boolean ehResponsavel(Authentication autenticacao) {
        return possuiPerfil(autenticacao, "ROLE_RESPONSAVEL")
            || possuiPerfil(autenticacao, "ROLE_ADMIN");
    }

    private void exigirPerfilResponsavel(Authentication autenticacao) {
        if (!ehResponsavel(autenticacao)) {
            throw new AcessoNegadoException(
                "Apenas responsáveis podem executar esta operação"
            );
        }
    }

    private boolean possuiPerfil(
        Authentication autenticacao,
        String perfil
    ) {
        return autenticacao.getAuthorities()
            .stream()
            .anyMatch(a -> a.getAuthority().equals(perfil));
    }

    private void validarPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new RegraNegocioException(
                "O término deve ser posterior ao início"
            );
        }

        if (inicio.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new RegraNegocioException(
                "Não é permitido reservar no passado"
            );
        }
    }

    private void exigirForaDeManutencaoProgramada(
        Sala sala,
        Material material,
        LocalDateTime inicio,
        LocalDateTime fim
    ) {
        if (
            manutencoes.bloqueado(
                TipoRecurso.SALA,
                sala.getId(),
                inicio,
                fim
            )
        ) {
            throw new RegraNegocioException(
                "Sala em manutenção programada no período solicitado"
            );
        }

        if (
            material != null &&
            manutencoes.bloqueado(
                TipoRecurso.MATERIAL,
                material.getId(),
                inicio,
                fim
            )
        ) {
            throw new RegraNegocioException(
                "Material em manutenção programada no período solicitado"
            );
        }
    }

    private void checarConflitos(
        Long salaId,
        Long professorId,
        Long materialId,
        LocalDateTime inicio,
        LocalDateTime fim,
        Long ignorar,
        String usuario
    ) {
        if (reservas.salaOcupada(salaId, inicio, fim, FINAIS, ignorar)) {
            conflito(
                TipoRecurso.SALA, salaId, inicio, fim, usuario,
                "Sala já possui reserva nesse horário"
            );
        }

        if (
            reservas.professorOcupado(professorId, inicio, fim, FINAIS, ignorar)
        ) {
            conflito(
                TipoRecurso.PROFESSOR, professorId, inicio, fim, usuario,
                "Professor já possui reserva nesse horário"
            );
        }

        if (
            materialId != null &&
            reservas.materialOcupado(materialId, inicio, fim, FINAIS, ignorar)
        ) {
            conflito(
                TipoRecurso.MATERIAL, materialId, inicio, fim, usuario,
                "Material já possui reserva nesse horário"
            );
        }
    }

    private void conflito(
        TipoRecurso tipo,
        Long recursoId,
        LocalDateTime inicio,
        LocalDateTime fim,
        String usuario,
        String mensagem
    ) {
        conflitos.save(
            new ConflitoEvitado(tipo, recursoId, usuario, inicio, fim)
        );

        throw new ConflitoHorarioException(mensagem);
    }

    private void auditar(Reserva r, String acao, String usuario) {
        auditorias.save(new Auditoria(r.getId(), acao, usuario));
    }

    public List<Auditoria> historico(
        Long id,
        Authentication autenticacao
    ) {
        buscarAutorizada(id, autenticacao);
        return auditorias.findByReservaIdOrderByOcorridoEmDesc(id);
    }
}
