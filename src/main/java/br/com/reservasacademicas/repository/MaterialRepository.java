package br.com.reservasacademicas.repository;

import br.com.reservasacademicas.entity.Material;
import br.com.reservasacademicas.enums.TipoMaterial;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Material m where m.id = :id")
    Optional<Material> findByIdForUpdate(@Param("id") Long id);

    List<Material> findByTipoAndDisponivelTrueAndEmManutencaoFalse(
        TipoMaterial tipo
    );
}
