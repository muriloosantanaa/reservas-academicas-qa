package br.com.reservasacademicas.repository;

import br.com.reservasacademicas.entity.PeriodoManutencao;
import br.com.reservasacademicas.enums.TipoRecurso;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeriodoManutencaoRepository
    extends JpaRepository<PeriodoManutencao, Long> {

    @Query("""
        select count(p) > 0
        from PeriodoManutencao p
        where p.tipoRecurso = :tipo
          and p.recursoId = :recursoId
          and p.inicio < :fim
          and p.fim > :inicio
        """)
    boolean existeSobreposicao(
        @Param("tipo") TipoRecurso tipo,
        @Param("recursoId") Long recursoId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    List<PeriodoManutencao> findAllByOrderByInicioAsc();
}
