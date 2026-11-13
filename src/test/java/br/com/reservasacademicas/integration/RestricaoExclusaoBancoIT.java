package br.com.reservasacademicas.integration;

import br.com.reservasacademicas.entity.Material;
import br.com.reservasacademicas.entity.Professor;
import br.com.reservasacademicas.entity.Reserva;
import br.com.reservasacademicas.entity.Sala;
import br.com.reservasacademicas.enums.StatusReserva;
import br.com.reservasacademicas.enums.TipoMaterial;
import br.com.reservasacademicas.repository.MaterialRepository;
import br.com.reservasacademicas.repository.ProfessorRepository;
import br.com.reservasacademicas.repository.ReservaRepository;
import br.com.reservasacademicas.repository.SalaRepository;
import br.com.reservasacademicas.support.PostgresContainerBase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Defesa em profundidade: mesmo que o código ignore o lock pessimista, o
 * PostgreSQL recusa reservas ativas sobrepostas (restrições de exclusão
 * criadas por RestricoesPostgres). Estes testes gravam direto pelos
 * repositórios, <b>sem passar por ReservaService</b>.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestricaoExclusaoBancoIT extends PostgresContainerBase {

    private static final LocalDateTime DIA = LocalDateTime.now()
        .plusDays(90).withHour(0).withMinute(0).withSecond(0).withNano(0);

    @Autowired private ReservaRepository reservas;
    @Autowired private SalaRepository salas;
    @Autowired private ProfessorRepository professores;
    @Autowired private MaterialRepository materiais;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("As três restrições de exclusão existem no banco")
    void restricoesExistem() throws SQLException {
        try (
            Connection conexao = dataSource.getConnection();
            Statement sql = conexao.createStatement();
            ResultSet rs = sql.executeQuery(
                "select count(*) from pg_constraint where conname like 'ex_reservas_%'"
            )
        ) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("Banco recusa duas reservas ativas sobrepostas na mesma sala")
    void recusaSobreposicaoDeSala() {
        Sala sala = novaSala();
        reservas.saveAndFlush(reserva(sala, novoProfessor(), null, 10, 12, StatusReserva.APROVADA));

        assertThrows(
            DataIntegrityViolationException.class,
            () -> reservas.saveAndFlush(
                reserva(sala, novoProfessor(), null, 11, 13, StatusReserva.APROVADA)
            )
        );
    }

    @Test
    @DisplayName("Banco recusa duas reservas ativas sobrepostas do mesmo professor")
    void recusaSobreposicaoDeProfessor() {
        Professor professor = novoProfessor();
        reservas.saveAndFlush(reserva(novaSala(), professor, null, 10, 12, StatusReserva.SOLICITADA));

        assertThrows(
            DataIntegrityViolationException.class,
            () -> reservas.saveAndFlush(
                reserva(novaSala(), professor, null, 9, 11, StatusReserva.APROVADA)
            )
        );
    }

    @Test
    @DisplayName("Banco recusa duas reservas ativas sobrepostas do mesmo material")
    void recusaSobreposicaoDeMaterial() {
        Material material = materiais.save(novoMaterial());
        reservas.saveAndFlush(reserva(novaSala(), novoProfessor(), material, 10, 12, StatusReserva.EM_USO));

        assertThrows(
            DataIntegrityViolationException.class,
            () -> reservas.saveAndFlush(
                reserva(novaSala(), novoProfessor(), material, 10, 12, StatusReserva.APROVADA)
            )
        );
    }

    @Test
    @DisplayName("Intervalos adjacentes (fim = início) são permitidos")
    void permiteIntervalosAdjacentes() {
        Sala sala = novaSala();
        Professor professor = novoProfessor();

        reservas.saveAndFlush(reserva(sala, professor, null, 8, 10, StatusReserva.APROVADA));
        assertDoesNotThrow(() -> reservas.saveAndFlush(
            reserva(sala, professor, null, 10, 12, StatusReserva.APROVADA)
        ));
    }

    @Test
    @DisplayName("Reservas em estado final (cancelada, rejeitada...) não bloqueiam o horário")
    void estadosFinaisNaoBloqueiam() {
        Sala sala = novaSala();

        reservas.saveAndFlush(reserva(sala, novoProfessor(), null, 10, 12, StatusReserva.CANCELADA));
        reservas.saveAndFlush(reserva(sala, novoProfessor(), null, 10, 12, StatusReserva.REJEITADA));
        reservas.saveAndFlush(reserva(sala, novoProfessor(), null, 10, 12, StatusReserva.NAO_COMPARECEU));
        assertDoesNotThrow(() -> reservas.saveAndFlush(
            reserva(sala, novoProfessor(), null, 10, 12, StatusReserva.APROVADA)
        ));
    }

    @Test
    @DisplayName("Reservas sem material não conflitam entre si pelo material")
    void materialNuloNaoConflita() {
        reservas.saveAndFlush(reserva(novaSala(), novoProfessor(), null, 10, 12, StatusReserva.APROVADA));

        assertDoesNotThrow(() -> reservas.saveAndFlush(
            reserva(novaSala(), novoProfessor(), null, 10, 12, StatusReserva.APROVADA)
        ));
    }

    // --------------------------------------------------------------- apoio

    private Reserva reserva(
        Sala sala,
        Professor professor,
        Material material,
        int horaInicio,
        int horaFim,
        StatusReserva status
    ) {
        Reserva r = new Reserva();
        r.setSala(sala);
        r.setProfessor(professor);
        r.setMaterial(material);
        r.setInicio(DIA.plusHours(horaInicio));
        r.setFim(DIA.plusHours(horaFim));
        r.setFinalidade("Teste de restrição");
        r.setStatus(status);
        r.setSolicitanteUsuario("professor");
        return r;
    }

    private Sala novaSala() {
        Sala s = new Sala();
        s.setNome("Sala-" + UUID.randomUUID());
        s.setLocalizacao("Bloco T");
        s.setCapacidade(20);
        return salas.save(s);
    }

    private Professor novoProfessor() {
        Professor p = new Professor();
        p.setNome("Prof " + UUID.randomUUID());
        p.setEmail(UUID.randomUUID() + "@faculdade.edu.br");
        return professores.save(p);
    }

    private Material novoMaterial() {
        Material m = new Material();
        m.setNome("Projetor " + UUID.randomUUID());
        m.setTipo(TipoMaterial.PROJETOR);
        m.setQuantidade(1);
        return m;
    }
}
