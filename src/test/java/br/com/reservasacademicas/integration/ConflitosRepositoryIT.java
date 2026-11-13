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
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * As consultas JPQL de sobreposição (o coração da detecção de conflito)
 * executadas contra PostgreSQL real — nenhum mock de repositório.
 * Reserva existente de referência: 10:00–12:00.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConflitosRepositoryIT extends PostgresContainerBase {

    private static final LocalDateTime DIA = LocalDateTime.now()
        .plusDays(120).withHour(0).withMinute(0).withSecond(0).withNano(0);

    @Autowired private ReservaRepository reservas;
    @Autowired private SalaRepository salas;
    @Autowired private ProfessorRepository professores;
    @Autowired private MaterialRepository materiais;

    @ParameterizedTest(name = "nova {0}–{1} (min do dia) sobre 10:00–12:00 → conflito={2}")
    @CsvSource({
        // início, fim (em minutos desde 00:00), conflito esperado
        "540, 600, false",   // 09:00–10:00  termina quando a existente começa
        "720, 780, false",   // 12:00–13:00  começa quando a existente termina
        "540, 601, true",    // 09:00–10:01  invade 1 minuto no começo
        "719, 780, true",    // 11:59–13:00  invade 1 minuto no fim
        "630, 690, true",    // 10:30–11:30  contida
        "480, 840, true",    // 08:00–14:00  contém a existente
        "600, 720, true",    // 10:00–12:00  idêntica
        "780, 840, false",   // 13:00–14:00  depois
        "420, 480, false"    // 07:00–08:00  antes
    })
    @DisplayName("Sala: regra de sobreposição de intervalos")
    void salaOcupada(int ini, int fim, boolean esperado) {
        Sala sala = novaSala();
        reservas.saveAndFlush(reserva(sala, novoProfessor(), null, 600, 720, StatusReserva.APROVADA));

        assertEquals(
            esperado,
            reservas.salaOcupada(
                sala.getId(), DIA.plusMinutes(ini), DIA.plusMinutes(fim),
                StatusReserva.finais(), null
            )
        );
    }

    @ParameterizedTest(name = "nova {0}–{1} (min do dia) sobre 10:00–12:00 → conflito={2}")
    @CsvSource({"540, 600, false", "720, 780, false", "630, 690, true", "599, 601, true"})
    @DisplayName("Professor: mesma regra de sobreposição na agenda do docente")
    void professorOcupado(int ini, int fim, boolean esperado) {
        Professor professor = novoProfessor();
        reservas.saveAndFlush(reserva(novaSala(), professor, null, 600, 720, StatusReserva.SOLICITADA));

        assertEquals(
            esperado,
            reservas.professorOcupado(
                professor.getId(), DIA.plusMinutes(ini), DIA.plusMinutes(fim),
                StatusReserva.finais(), null
            )
        );
    }

    @ParameterizedTest(name = "nova {0}–{1} (min do dia) sobre 10:00–12:00 → conflito={2}")
    @CsvSource({"540, 600, false", "720, 780, false", "630, 690, true", "599, 601, true"})
    @DisplayName("Material: mesma regra de sobreposição")
    void materialOcupado(int ini, int fim, boolean esperado) {
        Material material = novoMaterial();
        reservas.saveAndFlush(reserva(novaSala(), novoProfessor(), material, 600, 720, StatusReserva.EM_USO));

        assertEquals(
            esperado,
            reservas.materialOcupado(
                material.getId(), DIA.plusMinutes(ini), DIA.plusMinutes(fim),
                StatusReserva.finais(), null
            )
        );
    }

    @Test
    @DisplayName("Reservas em estado final não contam como ocupação")
    void estadosFinaisSaoIgnorados() {
        Sala sala = novaSala();

        for (StatusReserva final_ : StatusReserva.finais()) {
            reservas.saveAndFlush(reserva(sala, novoProfessor(), null, 600, 720, final_));
        }

        assertFalse(reservas.salaOcupada(
            sala.getId(), DIA.plusMinutes(630), DIA.plusMinutes(660),
            StatusReserva.finais(), null
        ));
    }

    @Test
    @DisplayName("Ao alterar, a própria reserva é ignorada (parâmetro ignorar)")
    void ignoraAPropriaReservaAoAlterar() {
        Sala sala = novaSala();
        Reserva existente = reservas.saveAndFlush(
            reserva(sala, novoProfessor(), null, 600, 720, StatusReserva.APROVADA)
        );

        assertTrue(reservas.salaOcupada(
            sala.getId(), DIA.plusMinutes(630), DIA.plusMinutes(660),
            StatusReserva.finais(), null
        ));
        assertFalse(reservas.salaOcupada(
            sala.getId(), DIA.plusMinutes(630), DIA.plusMinutes(660),
            StatusReserva.finais(), existente.getId()
        ));
    }

    @Test
    @DisplayName("Reservas de outro recurso não geram conflito")
    void outroRecursoNaoConflita() {
        Sala sala = novaSala();
        reservas.saveAndFlush(reserva(novaSala(), novoProfessor(), null, 600, 720, StatusReserva.APROVADA));

        assertFalse(reservas.salaOcupada(
            sala.getId(), DIA.plusMinutes(600), DIA.plusMinutes(720),
            StatusReserva.finais(), null
        ));
    }

    // --------------------------------------------------------------- apoio

    private Reserva reserva(
        Sala sala,
        Professor professor,
        Material material,
        int minInicio,
        int minFim,
        StatusReserva status
    ) {
        Reserva r = new Reserva();
        r.setSala(sala);
        r.setProfessor(professor);
        r.setMaterial(material);
        r.setInicio(DIA.plusMinutes(minInicio));
        r.setFim(DIA.plusMinutes(minFim));
        r.setFinalidade("Teste de consulta");
        r.setStatus(status);
        return r;
    }

    private Sala novaSala() {
        Sala s = new Sala();
        s.setNome("Sala-" + UUID.randomUUID());
        s.setLocalizacao("Bloco R");
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
        return materiais.save(m);
    }
}
