package br.com.reservasacademicas.config;

import br.com.reservasacademicas.enums.StatusReserva;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Última linha de defesa contra dupla reserva no PostgreSQL.
 *
 * <p>A proteção primária é o lock pessimista dos recursos em
 * {@code ReservaService}. Estas restrições de exclusão fazem o <b>próprio
 * banco</b> recusar duas reservas ativas que se sobreponham para a mesma
 * sala, professor ou material, mesmo que algum caminho de código (ou outra
 * aplicação) esqueça o lock. Em outros bancos (ex.: H2 nos testes
 * unitários) nada é criado.
 */
@Component
public class RestricoesPostgres implements ApplicationRunner {

    private static final Logger LOG =
        LoggerFactory.getLogger(RestricoesPostgres.class);

    private final DataSource dataSource;

    @Value("${reservas.restricoes-banco.habilitado:true}")
    private boolean habilitado = true;

    public RestricoesPostgres(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!habilitado) {
            return;
        }

        try (Connection conexao = dataSource.getConnection()) {
            String banco = conexao.getMetaData().getDatabaseProductName();

            if (!"PostgreSQL".equalsIgnoreCase(banco)) {
                LOG.info("Restrições de exclusão ignoradas para o banco {}", banco);
                return;
            }

            try (Statement sql = conexao.createStatement()) {
                sql.execute("CREATE EXTENSION IF NOT EXISTS btree_gist");
                sql.execute(restricao("ex_reservas_sala", "sala_id"));
                sql.execute(restricao("ex_reservas_professor", "professor_id"));
                sql.execute(restricao("ex_reservas_material", "material_id"));
            }

            LOG.info("Restrições de exclusão de reservas verificadas/criadas");
        } catch (SQLException e) {
            // Não derruba a aplicação: o lock pessimista continua ativo.
            LOG.warn(
                "Não foi possível criar as restrições de exclusão: {}",
                e.getMessage()
            );
        }
    }

    static String restricao(String nome, String coluna) {
        String finais = StatusReserva.finais().stream()
            .map(status -> "'" + status.name() + "'")
            .collect(Collectors.joining(","));

        return "DO $$ BEGIN "
            + "IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '"
            + nome + "') THEN "
            + "ALTER TABLE reservas ADD CONSTRAINT " + nome
            + " EXCLUDE USING gist (" + coluna
            + " WITH =, tsrange(inicio, fim) WITH &&)"
            + " WHERE (status NOT IN (" + finais + ")); "
            + "END IF; END $$";
    }
}
