package br.com.reservasacademicas;

import br.com.reservasacademicas.entity.*;
import br.com.reservasacademicas.enums.StatusReserva;
import br.com.reservasacademicas.exception.RecursoNaoEncontradoException;
import br.com.reservasacademicas.exception.RegraNegocioException;
import br.com.reservasacademicas.repository.*;
import br.com.reservasacademicas.service.ManutencaoService;
import br.com.reservasacademicas.service.NotificacaoService;
import br.com.reservasacademicas.service.ReservaService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock private ReservaRepository reservas;
    @Mock private SalaRepository salas;
    @Mock private ProfessorRepository professores;
    @Mock private MaterialRepository materiais;
    @Mock private AuditoriaRepository auditorias;
    @Mock private NotificacaoService notificacoes;
    @Mock private ManutencaoService manutencoes;
    @Mock private ConflitoEvitadoRepository conflitosEvitados;

    @InjectMocks private ReservaService service;

    private Sala sala;
    private Professor professor;
    private Material material;
    private Authentication professorAuth;
    private Authentication responsavelAuth;
    private LocalDateTime inicio;

    @BeforeEach
    void configurar() {
        sala = new Sala();
        sala.setId(1L);
        sala.setNome("Sala 1");
        professor = new Professor();
        professor.setId(2L);
        professor.setNome("Docente");
        professor.setEmail("docente@faculdade.edu.br");
        material = new Material();
        material.setId(3L);
        material.setNome("Projetor");
        professorAuth = auth("docente", "ROLE_PROFESSOR");
        responsavelAuth = auth("responsavel", "ROLE_RESPONSAVEL");
        inicio = LocalDateTime.now().plusDays(2);
    }

    @Test
    void listarDeveRetornarTodasParaResponsavel() {
        when(reservas.findAllByOrderByInicioDesc()).thenReturn(List.of(new Reserva()));
        assertEquals(1, service.listar(responsavelAuth).size());
        verify(reservas).findAllByOrderByInicioDesc();
    }

    @Test
    void listarDeveFiltrarPorUsuarioComum() {
        when(reservas.findBySolicitanteUsuarioOrderByInicioDesc("docente"))
            .thenReturn(List.of(new Reserva()));
        assertEquals(1, service.listar(professorAuth).size());
    }

    @Test
    void buscarDeveRetornarReservaExistente() {
        Reserva reserva = reserva(StatusReserva.APROVADA);
        when(reservas.findById(10L)).thenReturn(Optional.of(reserva));
        assertSame(reserva, service.buscar(10L));
    }

    @Test
    void buscarDeveFalharQuandoNaoExiste() {
        when(reservas.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(99L));
    }

    @Test
    void buscarAutorizadaPermiteDono() {
        Reserva reserva = reserva(StatusReserva.APROVADA);
        reserva.setSolicitanteUsuario("docente");
        when(reservas.findById(10L)).thenReturn(Optional.of(reserva));
        assertSame(reserva, service.buscarAutorizada(10L, professorAuth));
    }

    @Test
    void buscarAutorizadaPermiteResponsavel() {
        Reserva reserva = reserva(StatusReserva.APROVADA);
        when(reservas.findById(10L)).thenReturn(Optional.of(reserva));
        assertSame(reserva, service.buscarAutorizada(10L, responsavelAuth));
    }

    @Test
    void buscarAutorizadaBloqueiaOutroUsuario() {
        Reserva reserva = reserva(StatusReserva.APROVADA);
        reserva.setSolicitanteUsuario("outra-pessoa");
        when(reservas.findById(10L)).thenReturn(Optional.of(reserva));
        assertThrows(RegraNegocioException.class,
            () -> service.buscarAutorizada(10L, professorAuth));
    }

    @Test
    void criarReservaSimplesAprovaAutomaticamente() {
        prepararRecursos();
        Reserva entrada = novaReserva(false);
        when(reservas.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
        Reserva criada = service.criar(entrada, "docente");
        assertEquals(StatusReserva.APROVADA, criada.getStatus());
        assertEquals("docente", criada.getSolicitanteUsuario());
        verify(auditorias).save(any(Auditoria.class));
        verify(notificacoes).enviar(any(Reserva.class), contains("solicitação criada"));
    }

    @Test
    void criarReservaComMaterialRestritoFicaSolicitada() {
        prepararRecursos();
        when(materiais.findByIdForUpdate(3L)).thenReturn(Optional.of(material));
        material.setRestrito(true);
        Reserva entrada = novaReserva(true);
        when(reservas.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(StatusReserva.SOLICITADA, service.criar(entrada, "docente").getStatus());
    }

    @Test
    void criarRejeitaPeriodoNuloOuInvertido() {
        Reserva entrada = new Reserva();
        assertThrows(RegraNegocioException.class, () -> service.criar(entrada, "docente"));
        entrada.setInicio(inicio);
        entrada.setFim(inicio);
        assertThrows(RegraNegocioException.class, () -> service.criar(entrada, "docente"));
    }

    @Test
    void criarRejeitaReservaNoPassado() {
        Reserva entrada = new Reserva();
        entrada.setInicio(LocalDateTime.now().minusDays(1));
        entrada.setFim(LocalDateTime.now().plusDays(1));
        assertThrows(RegraNegocioException.class, () -> service.criar(entrada, "docente"));
    }

    @Test
    void criarExigeSalaEProfessor() {
        Reserva entrada = novaReserva(false);
        entrada.setSala(null);
        assertThrows(RegraNegocioException.class, () -> service.criar(entrada, "docente"));
    }

    @Test
    void criarRejeitaMaterialSemIdentificador() {
        Reserva entrada = novaReserva(false);
        entrada.setMaterial(new Material());
        assertThrows(RegraNegocioException.class, () -> service.criar(entrada, "docente"));
    }

    @Test
    void criarFalhaQuandoSalaNaoExiste() {
        Reserva entrada = novaReserva(false);
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.criar(entrada, "docente"));
    }

    @Test
    void criarFalhaQuandoProfessorNaoExiste() {
        Reserva entrada = novaReserva(false);
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.criar(entrada, "docente"));
    }

    @Test
    void criarRejeitaSalaInativaOuEmManutencao() {
        sala.setAtiva(false);
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        assertThrows(RegraNegocioException.class,
            () -> service.criar(novaReserva(false), "docente"));
    }

    @Test
    void criarRejeitaProfessorInativo() {
        professor.setAtivo(false);
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        assertThrows(RegraNegocioException.class,
            () -> service.criar(novaReserva(false), "docente"));
    }

    @Test
    void criarFalhaQuandoMaterialNaoExisteOuIndisponivel() {
        Reserva entrada = novaReserva(true);
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        when(materiais.findByIdForUpdate(3L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class,
            () -> service.criar(entrada, "docente"));

        material.setDisponivel(false);
        when(materiais.findByIdForUpdate(3L)).thenReturn(Optional.of(material));
        assertThrows(RegraNegocioException.class,
            () -> service.criar(novaReserva(true), "docente"));
    }

    @Test
    void criarDetectaConflitoDeSalaProfessorEMaterial() {
        prepararRecursos();
        when(materiais.findByIdForUpdate(3L)).thenReturn(Optional.of(material));
        Reserva entrada = novaReserva(true);
        when(reservas.salaOcupada(anyLong(), any(), any(), anyList(), isNull())).thenReturn(true);
        assertThrows(RegraNegocioException.class, () -> service.criar(entrada, "docente"));
        reset(reservas);
        when(reservas.professorOcupado(anyLong(), any(), any(), anyList(), isNull())).thenReturn(true);
        assertThrows(RegraNegocioException.class, () -> service.criar(novaReserva(true), "docente"));
        reset(reservas);
        when(reservas.materialOcupado(anyLong(), any(), any(), anyList(), isNull())).thenReturn(true);
        assertThrows(RegraNegocioException.class, () -> service.criar(novaReserva(true), "docente"));
    }

    @Test
    void alterarAtualizaReservaDoDono() {
        Reserva atual = reserva(StatusReserva.APROVADA);
        atual.setSolicitanteUsuario("docente");
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        when(reservas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Reserva dados = new Reserva();
        dados.setInicio(inicio.plusHours(1));
        dados.setFim(inicio.plusHours(2));
        dados.setFinalidade("Aula prática");
        assertEquals("Aula prática", service.alterar(10L, dados, professorAuth).getFinalidade());
    }

    @Test
    void alterarBloqueiaReservaEmUsoOuConcluida() {
        Reserva atual = reserva(StatusReserva.EM_USO);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        Reserva dados = new Reserva();
        assertThrows(RegraNegocioException.class,
            () -> service.alterar(10L, dados, responsavelAuth));
    }

    @Test
    void alterarBloqueiaUsuarioQueNaoEDono() {
        Reserva atual = reserva(StatusReserva.APROVADA);
        atual.setSolicitanteUsuario("outro");
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        assertThrows(RegraNegocioException.class,
            () -> service.alterar(10L, new Reserva(), professorAuth));
    }

    @Test
    void mudarStatusAprovaSolicitacaoComoResponsavel() {
        Reserva atual = reserva(StatusReserva.SOLICITADA);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        when(reservas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(StatusReserva.APROVADA,
            service.mudarStatus(10L, StatusReserva.APROVADA, responsavelAuth).getStatus());
    }

    @Test
    void mudarStatusExecutaTransicoesValidasDeResponsavel() {
        Reserva solicitada = reserva(StatusReserva.SOLICITADA);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(solicitada));
        when(reservas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(StatusReserva.REJEITADA,
            service.mudarStatus(10L, StatusReserva.REJEITADA, responsavelAuth).getStatus());

        Reserva aprovada = reserva(StatusReserva.APROVADA);
        when(reservas.findByIdForUpdate(11L)).thenReturn(Optional.of(aprovada));
        assertEquals(StatusReserva.EM_USO,
            service.mudarStatus(11L, StatusReserva.EM_USO, responsavelAuth).getStatus());

        Reserva emUso = reserva(StatusReserva.EM_USO);
        when(reservas.findByIdForUpdate(12L)).thenReturn(Optional.of(emUso));
        assertEquals(StatusReserva.CONCLUIDA,
            service.mudarStatus(12L, StatusReserva.CONCLUIDA, responsavelAuth).getStatus());

        Reserva aprovadaAusente = reserva(StatusReserva.APROVADA);
        when(reservas.findByIdForUpdate(13L)).thenReturn(Optional.of(aprovadaAusente));
        assertEquals(StatusReserva.NAO_COMPARECEU,
            service.mudarStatus(13L, StatusReserva.NAO_COMPARECEU, responsavelAuth).getStatus());
    }

    @Test
    void mudarStatusPermiteCancelarSolicitacao() {
        Reserva solicitada = reserva(StatusReserva.SOLICITADA);
        solicitada.setSolicitanteUsuario("docente");
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(solicitada));
        when(reservas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(StatusReserva.CANCELADA,
            service.mudarStatus(10L, StatusReserva.CANCELADA, professorAuth).getStatus());
    }

    @Test
    void mudarStatusAdminTambemPodeExecutarOperacoesDeResponsavel() {
        Authentication admin = auth("admin", "ROLE_ADMIN");
        Reserva solicitada = reserva(StatusReserva.SOLICITADA);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(solicitada));
        when(reservas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(StatusReserva.APROVADA,
            service.mudarStatus(10L, StatusReserva.APROVADA, admin).getStatus());
    }

    @Test
    void alterarFalhaQuandoReservaNaoExiste() {
        when(reservas.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class,
            () -> service.alterar(404L, new Reserva(), responsavelAuth));
    }

    @Test
    void criarDetectaSalaEmManutencao() {
        sala.setEmManutencao(true);
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        assertThrows(RegraNegocioException.class,
            () -> service.criar(novaReserva(false), "docente"));
    }

    @Test
    void criarDetectaMaterialEmManutencao() {
        material.setEmManutencao(true);
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        when(materiais.findByIdForUpdate(3L)).thenReturn(Optional.of(material));
        assertThrows(RegraNegocioException.class,
            () -> service.criar(novaReserva(true), "docente"));
    }

    @Test
    void registrarRetiradaEDevolucaoRejeitamReservaSemMaterial() {
        Reserva semMaterial = reserva(StatusReserva.EM_USO);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(semMaterial));
        assertThrows(RegraNegocioException.class,
            () -> service.registrarRetirada(10L, responsavelAuth));
        assertThrows(RegraNegocioException.class,
            () -> service.registrarDevolucao(10L, responsavelAuth));
    }

    @Test
    void mudarStatusRejeitaTransicaoSolicitada() {
        Reserva atual = reserva(StatusReserva.APROVADA);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        assertThrows(RegraNegocioException.class,
            () -> service.mudarStatus(10L, StatusReserva.SOLICITADA, responsavelAuth));
    }

    @Test
    void mudarStatusBloqueiaUsuarioComumAoAprovar() {
        Reserva atual = reserva(StatusReserva.SOLICITADA);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        assertThrows(RegraNegocioException.class,
            () -> service.mudarStatus(10L, StatusReserva.APROVADA, professorAuth));
    }

    @Test
    void mudarStatusPermiteCancelarPeloDono() {
        Reserva atual = reserva(StatusReserva.APROVADA);
        atual.setSolicitanteUsuario("docente");
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        when(reservas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(StatusReserva.CANCELADA,
            service.mudarStatus(10L, StatusReserva.CANCELADA, professorAuth).getStatus());
    }

    @Test
    void mudarStatusBloqueiaCancelarPorTerceiro() {
        Reserva atual = reserva(StatusReserva.APROVADA);
        atual.setSolicitanteUsuario("outro");
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        assertThrows(RegraNegocioException.class,
            () -> service.mudarStatus(10L, StatusReserva.CANCELADA, professorAuth));
    }

    @Test
    void mudarStatusBloqueiaEstadoFinal() {
        Reserva atual = reserva(StatusReserva.CONCLUIDA);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        assertThrows(RegraNegocioException.class,
            () -> service.mudarStatus(10L, StatusReserva.CANCELADA, responsavelAuth));
    }

    @Test
    void mudarStatusDetectaTransicaoInvalida() {
        Reserva atual = reserva(StatusReserva.SOLICITADA);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        assertThrows(RegraNegocioException.class,
            () -> service.mudarStatus(10L, StatusReserva.CONCLUIDA, responsavelAuth));
    }

    @Test
    void registrarRetiradaExigeResponsavelMaterialEStatusEmUso() {
        assertThrows(RegraNegocioException.class,
            () -> service.registrarRetirada(10L, professorAuth));
        Reserva semMaterial = reserva(StatusReserva.EM_USO);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(semMaterial));
        assertThrows(RegraNegocioException.class,
            () -> service.registrarRetirada(10L, responsavelAuth));
    }

    @Test
    void registrarRetiradaRegistraDataEAuditoria() {
        Reserva atual = reserva(StatusReserva.EM_USO);
        atual.setMaterial(material);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        when(reservas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertNotNull(service.registrarRetirada(10L, responsavelAuth).getRetiradaEm());
        verify(auditorias).save(any(Auditoria.class));
    }

    @Test
    void registrarRetiradaNaoPermiteDuplicidade() {
        Reserva atual = reserva(StatusReserva.EM_USO);
        atual.setMaterial(material);
        atual.setRetiradaEm(LocalDateTime.now());
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        assertThrows(RegraNegocioException.class,
            () -> service.registrarRetirada(10L, responsavelAuth));
    }

    @Test
    void registrarDevolucaoExigeRetiradaPrevia() {
        Reserva atual = reserva(StatusReserva.EM_USO);
        atual.setMaterial(material);
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        assertThrows(RegraNegocioException.class,
            () -> service.registrarDevolucao(10L, responsavelAuth));
    }

    @Test
    void registrarDevolucaoRegistraDataAuditoriaENotificacao() {
        Reserva atual = reserva(StatusReserva.EM_USO);
        atual.setMaterial(material);
        atual.setRetiradaEm(LocalDateTime.now().minusHours(1));
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        when(reservas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertNotNull(service.registrarDevolucao(10L, responsavelAuth).getDevolvidaEm());
        verify(notificacoes).enviar(atual, "material devolvido");
    }

    @Test
    void registrarDevolucaoNaoPermiteDuplicidade() {
        Reserva atual = reserva(StatusReserva.EM_USO);
        atual.setMaterial(material);
        atual.setRetiradaEm(LocalDateTime.now().minusHours(1));
        atual.setDevolvidaEm(LocalDateTime.now());
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        assertThrows(RegraNegocioException.class,
            () -> service.registrarDevolucao(10L, responsavelAuth));
    }

    @Test
    void historicoExigeAcessoEConsultaAuditoria() {
        Reserva atual = reserva(StatusReserva.APROVADA);
        atual.setSolicitanteUsuario("docente");
        when(reservas.findById(10L)).thenReturn(Optional.of(atual));
        when(auditorias.findByReservaIdOrderByOcorridoEmDesc(10L)).thenReturn(List.of());
        assertTrue(service.historico(10L, professorAuth).isEmpty());
    }

    @Test
    void notificacoesConsultaPorReserva() {
        when(notificacoes.listar(10L)).thenReturn(List.of());
        assertTrue(service.notificacoes(10L).isEmpty());
    }

    private void prepararRecursos() {
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        when(reservas.salaOcupada(anyLong(), any(), any(), anyList(), any())).thenReturn(false);
        when(reservas.professorOcupado(anyLong(), any(), any(), anyList(), any())).thenReturn(false);
    }

    private Reserva novaReserva(boolean comMaterial) {
        Reserva r = new Reserva();
        r.setSala(sala);
        r.setProfessor(professor);
        r.setInicio(inicio);
        r.setFim(inicio.plusHours(1));
        r.setFinalidade("Aula");
        if (comMaterial) r.setMaterial(material);
        return r;
    }

    private Reserva reserva(StatusReserva status) {
        Reserva r = novaReserva(false);
        r.setId(10L);
        r.setStatus(status);
        return r;
    }

    private Authentication auth(String usuario, String perfil) {
        return new UsernamePasswordAuthenticationToken(
            usuario,
            "senha",
            List.of(new SimpleGrantedAuthority(perfil))
        );
    }
}
