package br.com.reservasacademicas.repository;

import br.com.reservasacademicas.entity.ConflitoEvitado;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConflitoEvitadoRepository
    extends JpaRepository<ConflitoEvitado, Long> {

    @Query("""
        select c.tipoRecurso, count(c)
        from ConflitoEvitado c
        group by c.tipoRecurso
        """)
    List<Object[]> contarPorTipo();
}
