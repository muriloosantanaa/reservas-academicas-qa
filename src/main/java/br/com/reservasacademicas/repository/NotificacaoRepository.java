package br.com.reservasacademicas.repository;

import br.com.reservasacademicas.entity.Notificacao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository
    extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByReservaIdOrderByCriadaEmDesc(Long reservaId);
}
