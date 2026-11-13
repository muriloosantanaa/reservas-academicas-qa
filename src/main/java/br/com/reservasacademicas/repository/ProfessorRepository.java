package br.com.reservasacademicas.repository;

import br.com.reservasacademicas.entity.Professor;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Professor p where p.id = :id")
    Optional<Professor> findByIdForUpdate(@Param("id") Long id);

    List<Professor> findByCompetenciaContainingIgnoreCaseAndAtivoTrue(
        String competencia
    );
}
