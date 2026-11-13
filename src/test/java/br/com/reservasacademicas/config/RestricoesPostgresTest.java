package br.com.reservasacademicas.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestricoesPostgresTest {

    @Mock private DataSource dataSource;
    @Mock private Connection conexao;
    @Mock private DatabaseMetaData metadados;
    @Mock private Statement sql;

    private void banco(String nome) throws SQLException {
        when(dataSource.getConnection()).thenReturn(conexao);
        when(conexao.getMetaData()).thenReturn(metadados);
        when(metadados.getDatabaseProductName()).thenReturn(nome);
    }

    @Test
    void naoFazNadaEmBancosQueNaoSaoPostgres() throws Exception {
        banco("H2");

        new RestricoesPostgres(dataSource).run(null);

        verify(conexao, never()).createStatement();
    }

    @Test
    void naoFazNadaQuandoDesabilitada() throws Exception {
        RestricoesPostgres restricoes = new RestricoesPostgres(dataSource);
        restricoes.setHabilitado(false);

        restricoes.run(null);

        verifyNoMoreInteractions(dataSource);
    }

    @Test
    void criaExtensaoETresRestricoesNoPostgres() throws Exception {
        banco("PostgreSQL");
        when(conexao.createStatement()).thenReturn(sql);

        new RestricoesPostgres(dataSource).run(null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sql, times(4)).execute(captor.capture());
        assertEquals("CREATE EXTENSION IF NOT EXISTS btree_gist", captor.getAllValues().get(0));
        assertTrue(captor.getAllValues().get(1).contains("sala_id WITH ="));
        assertTrue(captor.getAllValues().get(2).contains("professor_id WITH ="));
        assertTrue(captor.getAllValues().get(3).contains("material_id WITH ="));
    }

    @Test
    void falhaNoBancoNaoDerrubaAAplicacao() throws Exception {
        banco("PostgreSQL");
        when(conexao.createStatement()).thenReturn(sql);
        when(sql.execute(anyString())).thenThrow(new SQLException("sem permissão"));

        assertDoesNotThrow(() -> new RestricoesPostgres(dataSource).run(null));
    }

    @Test
    void semConexaoNaoDerrubaAAplicacao() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("indisponível"));

        assertDoesNotThrow(() -> new RestricoesPostgres(dataSource).run(null));
    }

    @Test
    void sqlDaRestricaoExcluiSomenteEstadosAtivos() {
        String ddl = RestricoesPostgres.restricao("ex_teste", "sala_id");

        assertTrue(ddl.contains("EXCLUDE USING gist"));
        assertTrue(ddl.contains("tsrange(inicio, fim) WITH &&"));
        assertTrue(ddl.contains("'REJEITADA'"));
        assertTrue(ddl.contains("'CANCELADA'"));
        assertTrue(ddl.contains("'CONCLUIDA'"));
        assertTrue(ddl.contains("'NAO_COMPARECEU'"));
        assertFalse(ddl.contains("'APROVADA'"), "estados ativos não podem ser excluídos");
        assertTrue(ddl.contains("IF NOT EXISTS"), "deve ser idempotente");
    }
}
