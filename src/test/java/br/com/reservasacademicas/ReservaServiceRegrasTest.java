package br.com.reservasacademicas;

import br.com.reservasacademicas.entity.*;
import br.com.reservasacademicas.enums.StatusReserva;
import br.com.reservasacademicas.enums.TipoRecurso;
import br.com.reservasacademicas.exception.*;
import br.com.reservasacademicas.repository.*;
import br.com.reservasacademicas.service.ManutencaoService;
import br.com.reservasacademicas.service.NotificacaoService;
import br.com.reservasacademicas.service.ReservaService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regras acrescentadas na revisão de qualidade: autorização com HTTP 403,
 * sanitização de campos de controle, manutenção programada, registro de
 * conflitos evitados e proteção das reservas já aprovadas.
 */
@ExtendWith(MockitoExtension.class)
class ReservaServiceRegrasTest {

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
        professor = new Professor();
        professor.setId(2L);
        professor.setEmail("docente@faculdade.edu.br");
        material = new Material();
        material.setId(3L);
        professorAuth = auth("docente", "ROLE_SOLICITANTE");
        responsavelAuth = auth("responsavel", "ROLE_RESPONSAVEL");
        inicio = LocalDateTime.now().plusDays(2);
    }

    // ---------------------------------------------------------- autorização

    @Test
    @DisplayName("Quem não é dono nem responsável recebe AcessoNegado ao consultar")
    void naoDonoRecebeAcessoNegadoAoBuscar() {
        Reserva alheia = reserva(StatusReserva.APROVADA);
        alheia.setSolicitanteUsuario("outra-pessoa");
        when(reservas.findById(10L)).thenReturn(Optional.of(alheia));

        assertThrows(
            AcessoNegadoException.class,
            () -> service.buscarAutorizada(10L, professorAuth)
        );
    }

    @Test
    @DisplayName("Usuário comum recebe AcessoNegado ao tentar aprovar")
    void usuarioComumRecebeAcessoNegadoAoAprovar() {
        when(reservas.findByIdForUpdate(10L))
            .thenReturn(Optional.of(reserva(StatusReserva.SOLICITADA)));

        assertThrows(
            AcessoNegadoException.class,
            () -> service.mudarStatus(10L, StatusReserva.APROVADA, professorAuth)
        );
    }

    @Test
    @DisplayName("Terceiro recebe AcessoNegado ao cancelar reserva alheia")
    void terceiroRecebeAcessoNegadoAoCancelar() {
        Reserva alheia = reserva(StatusReserva.APROVADA);
        alheia.setSolicitanteUsuario("outra-pessoa");
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(alheia));

        assertThrows(
            AcessoNegadoException.class,
            () -> service.mudarStatus(10L, StatusReserva.CANCELADA, professorAuth)
        );
    }

    @Test
    @DisplayName("Retirada e devolução por usuário comum recebem AcessoNegado")
    void retiradaEDevolucaoPorUsuarioComumRecebemAcessoNegado() {
        assertThrows(
            AcessoNegadoException.class,
            () -> service.registrarRetirada(10L, professorAuth)
        );
        assertThrows(
            AcessoNegadoException.class,
            () -> service.registrarDevolucao(10L, professorAuth)
        );
    }

    // ----------------------------------------------------- campos de controle

    @Test
    @DisplayName("Criar ignora id, retirada e devolução enviados pelo cliente")
    void criarIgnoraCamposDeControleDoCliente() {
        prepararRecursosSemConflito();
        Reserva entrada = novaReserva(false);
        entrada.setId(999L);
        entrada.setRetiradaEm(LocalDateTime.now());
        entrada.setDevolvidaEm(LocalDateTime.now());
        when(reservas.save(any(Reserva.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        Reserva criada = service.criar(entrada, "docente");

        assertNull(criada.getId(), "id do cliente não pode sobrescrever outra reserva");
        assertNull(criada.getRetiradaEm());
        assertNull(criada.getDevolvidaEm());
    }

    // ------------------------------------------------------------ manutenção

    @Test
    @DisplayName("Sala em manutenção programada não pode ser reservada")
    void criarBloqueiaSalaEmManutencaoProgramada() {
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        when(manutencoes.bloqueado(eq(TipoRecurso.SALA), eq(1L), any(), any()))
            .thenReturn(true);

        RegraNegocioException erro = assertThrows(
            RegraNegocioException.class,
            () -> service.criar(novaReserva(false), "docente")
        );

        assertTrue(erro.getMessage().contains("manutenção programada"));
        verify(reservas, never()).save(any());
    }

    @Test
    @DisplayName("Material em manutenção programada não pode ser reservado")
    void criarBloqueiaMaterialEmManutencaoProgramada() {
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        when(materiais.findByIdForUpdate(3L)).thenReturn(Optional.of(material));
        doReturn(false).when(manutencoes)
            .bloqueado(eq(TipoRecurso.SALA), eq(1L), any(), any());
        doReturn(true).when(manutencoes)
            .bloqueado(eq(TipoRecurso.MATERIAL), eq(3L), any(), any());

        assertThrows(
            RegraNegocioException.class,
            () -> service.criar(novaReserva(true), "docente")
        );
        verify(reservas, never()).save(any());
    }

    // ------------------------------------------------------ conflito evitado

    @Test
    @DisplayName("Conflito de sala registra o conflito evitado e responde 409")
    void conflitoDeSalaRegistraConflitoEvitado() {
        prepararRecursosSemConflito();
        when(reservas.salaOcupada(eq(1L), any(), any(), anyList(), isNull()))
            .thenReturn(true);

        assertThrows(
            ConflitoHorarioException.class,
            () -> service.criar(novaReserva(false), "docente")
        );

        ArgumentCaptor<ConflitoEvitado> captor =
            ArgumentCaptor.forClass(ConflitoEvitado.class);
        verify(conflitosEvitados).save(captor.capture());
        assertEquals(TipoRecurso.SALA, captor.getValue().getTipoRecurso());
        assertEquals(1L, captor.getValue().getRecursoId());
        assertEquals("docente", captor.getValue().getUsuario());
        verify(reservas, never()).save(any());
    }

    @Test
    @DisplayName("Conflito de professor é registrado com o tipo PROFESSOR")
    void conflitoDeProfessorRegistraConflitoEvitado() {
        prepararRecursosSemConflito();
        when(reservas.professorOcupado(eq(2L), any(), any(), anyList(), isNull()))
            .thenReturn(true);

        assertThrows(
            ConflitoHorarioException.class,
            () -> service.criar(novaReserva(false), "docente")
        );

        ArgumentCaptor<ConflitoEvitado> captor =
            ArgumentCaptor.forClass(ConflitoEvitado.class);
        verify(conflitosEvitados).save(captor.capture());
        assertEquals(TipoRecurso.PROFESSOR, captor.getValue().getTipoRecurso());
    }

    @Test
    @DisplayName("Conflito de material é registrado com o tipo MATERIAL")
    void conflitoDeMaterialRegistraConflitoEvitado() {
        prepararRecursosSemConflito();
        when(materiais.findByIdForUpdate(3L)).thenReturn(Optional.of(material));
        when(reservas.materialOcupado(eq(3L), any(), any(), anyList(), isNull()))
            .thenReturn(true);

        assertThrows(
            ConflitoHorarioException.class,
            () -> service.criar(novaReserva(true), "docente")
        );

        ArgumentCaptor<ConflitoEvitado> captor =
            ArgumentCaptor.forClass(ConflitoEvitado.class);
        verify(conflitosEvitados).save(captor.capture());
        assertEquals(TipoRecurso.MATERIAL, captor.getValue().getTipoRecurso());
    }

    // ------------------------------------------------------------- alteração

    @Test
    @DisplayName("Reserva em estado final não pode ser alterada")
    void alterarNaoPermiteReservaEmEstadoFinal() {
        Reserva cancelada = reserva(StatusReserva.CANCELADA);
        cancelada.setSolicitanteUsuario("docente");
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(cancelada));

        assertThrows(
            RegraNegocioException.class,
            () -> service.alterar(10L, dados(inicio.plusHours(3)), professorAuth)
        );
        verify(reservas, never()).save(any());
    }

    @Test
    @DisplayName("Mover reserva aprovada de recurso restrito exige nova aprovação")
    void alterarPeriodoDeReservaAprovadaDeRecursoRestritoExigeReaprovacao() {
        material.setRestrito(true);
        Reserva atual = reserva(StatusReserva.APROVADA);
        atual.setMaterial(material);
        atual.setSolicitanteUsuario("docente");
        prepararAlteracao(atual);
        when(materiais.findByIdForUpdate(3L)).thenReturn(Optional.of(material));

        Reserva salva =
            service.alterar(10L, dados(inicio.plusHours(5)), professorAuth);

        assertEquals(StatusReserva.SOLICITADA, salva.getStatus());
    }

    @Test
    @DisplayName("Alterar só a finalidade não tira a aprovação")
    void alterarSemMudarPeriodoMantemAprovacao() {
        material.setRestrito(true);
        Reserva atual = reserva(StatusReserva.APROVADA);
        atual.setMaterial(material);
        atual.setSolicitanteUsuario("docente");
        prepararAlteracao(atual);
        when(materiais.findByIdForUpdate(3L)).thenReturn(Optional.of(material));

        Reserva dados = new Reserva();
        dados.setInicio(atual.getInicio());
        dados.setFim(atual.getFim());
        dados.setFinalidade("Novo texto");

        assertEquals(
            StatusReserva.APROVADA,
            service.alterar(10L, dados, professorAuth).getStatus()
        );
    }

    @Test
    @DisplayName("Conflito ao alterar não modifica a entidade gerenciada")
    void alterarComConflitoNaoModificaAEntidade() {
        Reserva atual = reserva(StatusReserva.APROVADA);
        atual.setSolicitanteUsuario("docente");
        LocalDateTime inicioOriginal = atual.getInicio();
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        when(reservas.salaOcupada(eq(1L), any(), any(), anyList(), eq(10L)))
            .thenReturn(true);

        assertThrows(
            ConflitoHorarioException.class,
            () -> service.alterar(10L, dados(inicio.plusHours(6)), professorAuth)
        );

        assertEquals(inicioOriginal, atual.getInicio());
        verify(reservas, never()).save(any());
        verify(conflitosEvitados).save(any(ConflitoEvitado.class));
    }

    @Test
    @DisplayName("Alterar para período em manutenção programada é bloqueado")
    void alterarBloqueiaManutencaoProgramada() {
        Reserva atual = reserva(StatusReserva.APROVADA);
        atual.setSolicitanteUsuario("docente");
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        when(manutencoes.bloqueado(eq(TipoRecurso.SALA), eq(1L), any(), any()))
            .thenReturn(true);

        assertThrows(
            RegraNegocioException.class,
            () -> service.alterar(10L, dados(inicio.plusHours(6)), professorAuth)
        );
    }

    @Test
    @DisplayName("Alterar é bloqueado se a sala foi desativada depois da reserva")
    void alterarBloqueiaSalaQueFicouInativa() {
        sala.setAtiva(false);
        Reserva atual = reserva(StatusReserva.APROVADA);
        atual.setSolicitanteUsuario("docente");
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));

        assertThrows(
            RegraNegocioException.class,
            () -> service.alterar(10L, dados(inicio.plusHours(6)), professorAuth)
        );
    }

    @Test
    @DisplayName("Alterar é bloqueado se o material ficou indisponível")
    void alterarBloqueiaMaterialIndisponivel() {
        material.setDisponivel(false);
        Reserva atual = reserva(StatusReserva.APROVADA);
        atual.setMaterial(material);
        atual.setSolicitanteUsuario("docente");
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        when(materiais.findByIdForUpdate(3L)).thenReturn(Optional.of(material));

        assertThrows(
            RegraNegocioException.class,
            () -> service.alterar(10L, dados(inicio.plusHours(6)), professorAuth)
        );
    }

    // ------------------------------------------------- testes parametrizados

    @ParameterizedTest(name = "início em +{0}h e fim em +{1}h é rejeitado")
    @CsvSource({"48,48", "48,47", "49,24", "100,1"})
    void periodoComTerminoNaoPosteriorAoInicioERejeitado(long inicioH, long fimH) {
        LocalDateTime base = LocalDateTime.now();
        Reserva entrada = novaReserva(false);
        entrada.setInicio(base.plusHours(inicioH));
        entrada.setFim(base.plusHours(fimH));

        assertThrows(
            RegraNegocioException.class,
            () -> service.criar(entrada, "docente")
        );
        verifyNoMoreInteractions(reservas, salas, professores, materiais);
    }

    @ParameterizedTest(name = "início há {0} horas (passado) é rejeitado")
    @CsvSource({"1", "24", "720"})
    void inicioNoPassadoERejeitado(long horasAtras) {
        Reserva entrada = novaReserva(false);
        entrada.setInicio(LocalDateTime.now().minusHours(horasAtras));
        entrada.setFim(LocalDateTime.now().plusHours(2));

        assertThrows(
            RegraNegocioException.class,
            () -> service.criar(entrada, "docente")
        );
    }

    private static final Set<String> PERMITIDAS = Set.of(
        "SOLICITADA>APROVADA",
        "SOLICITADA>REJEITADA",
        "SOLICITADA>CANCELADA",
        "APROVADA>EM_USO",
        "APROVADA>CANCELADA",
        "APROVADA>NAO_COMPARECEU",
        "EM_USO>CONCLUIDA"
    );

    static Stream<Arguments> matrizDeTransicoes() {
        Stream.Builder<Arguments> todas = Stream.builder();

        for (StatusReserva de : StatusReserva.values()) {
            for (StatusReserva para : StatusReserva.values()) {
                todas.add(Arguments.of(
                    de,
                    para,
                    PERMITIDAS.contains(de + ">" + para)
                ));
            }
        }

        return todas.build();
    }

    @ParameterizedTest(name = "{0} → {1}: permitida={2}")
    @MethodSource("matrizDeTransicoes")
    @DisplayName("Máquina de estados: todas as 49 combinações de transição")
    void matrizCompletaDeTransicoes(
        StatusReserva de,
        StatusReserva para,
        boolean permitida
    ) {
        Reserva atual = reserva(de);
        atual.setSolicitanteUsuario("responsavel");
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));

        if (permitida) {
            when(reservas.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertEquals(
                para,
                service.mudarStatus(10L, para, responsavelAuth).getStatus()
            );
            verify(auditorias).save(any(Auditoria.class));
        } else {
            assertThrows(
                RegraNegocioException.class,
                () -> service.mudarStatus(10L, para, responsavelAuth)
            );
            assertEquals(de, atual.getStatus(), "estado não pode mudar");
            verify(auditorias, never()).save(any());
        }
    }

    // --------------------------------------------------------------- apoio

    private void prepararRecursosSemConflito() {
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
    }

    private void prepararAlteracao(Reserva atual) {
        when(reservas.findByIdForUpdate(10L)).thenReturn(Optional.of(atual));
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        when(reservas.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Reserva dados(LocalDateTime novoInicio) {
        Reserva dados = new Reserva();
        dados.setInicio(novoInicio);
        dados.setFim(novoInicio.plusHours(1));
        dados.setFinalidade("Aula remarcada");
        return dados;
    }

    private Reserva novaReserva(boolean comMaterial) {
        Reserva r = new Reserva();
        r.setSala(sala);
        r.setProfessor(professor);
        r.setInicio(inicio);
        r.setFim(inicio.plusHours(1));
        r.setFinalidade("Aula");
        if (comMaterial) {
            r.setMaterial(material);
        }
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
