package br.com.reservasacademicas.repository;

import br.com.reservasacademicas.entity.Auditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository
    extends JpaRepository<Auditoria, Long> {

    List<Auditoria> findByReservaIdOrderByOcorridoEmDesc(Long reservaId);
}
