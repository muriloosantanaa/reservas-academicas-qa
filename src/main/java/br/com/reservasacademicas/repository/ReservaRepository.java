package br.com.reservasacademicas.repository;

import br.com.reservasacademicas.entity.Reserva;
import br.com.reservasacademicas.enums.StatusReserva;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.id = :id")
    Optional<Reserva> findByIdForUpdate(
        @Param("id") Long id
    );

    @Query("""
        select count(r) > 0
        from Reserva r
        where r.status not in :finais
          and r.inicio < :fim
          and r.fim > :inicio
          and r.sala.id = :salaId
          and (:ignorar is null or r.id <> :ignorar)
        """)
    boolean salaOcupada(
        @Param("salaId") Long salaId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim,
        @Param("finais") Collection<StatusReserva> finais,
        @Param("ignorar") Long ignorar
    );

    @Query("""
        select count(r) > 0
        from Reserva r
        where r.status not in :finais
          and r.inicio < :fim
          and r.fim > :inicio
          and r.professor.id = :profId
          and (:ignorar is null or r.id <> :ignorar)
        """)
    boolean professorOcupado(
        @Param("profId") Long professorId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim,
        @Param("finais") Collection<StatusReserva> finais,
        @Param("ignorar") Long ignorar
    );

    @Query("""
        select count(r) > 0
        from Reserva r
        where r.status not in :finais
          and r.inicio < :fim
          and r.fim > :inicio
          and r.material.id = :materialId
          and (:ignorar is null or r.id <> :ignorar)
        """)
    boolean materialOcupado(
        @Param("materialId") Long materialId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim,
        @Param("finais") Collection<StatusReserva> finais,
        @Param("ignorar") Long ignorar
    );

    List<Reserva> findAllByOrderByInicioDesc();

    List<Reserva> findBySolicitanteUsuarioOrderByInicioDesc(
        String solicitanteUsuario
    );
}
