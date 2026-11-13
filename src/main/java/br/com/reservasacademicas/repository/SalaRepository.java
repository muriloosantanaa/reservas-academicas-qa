package br.com.reservasacademicas.repository;

import br.com.reservasacademicas.entity.Sala;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalaRepository extends JpaRepository<Sala, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sala s where s.id = :id")
    Optional<Sala> findByIdForUpdate(@Param("id") Long id);

    List<Sala> findByCapacidadeGreaterThanEqualAndLocalizacaoContainingIgnoreCaseAndAtivaTrue(
        int capacidade,
        String localizacao
    );
}
