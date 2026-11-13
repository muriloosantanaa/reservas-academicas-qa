package br.com.reservasacademicas.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integração que exigem PostgreSQL real.
 *
 * <p>Um único container (padrão singleton) é compartilhado por todas as
 * classes de teste da JVM e encerrado pelo Ryuk do Testcontainers. A imagem
 * é a mesma do docker-compose.yml, com a extensão btree_gist disponível.
 */
public abstract class PostgresContainerBase {

    protected static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void propriedadesDoBanco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registro.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
