package br.com.reservasacademicas.controller;

import br.com.reservasacademicas.entity.Auditoria;
import br.com.reservasacademicas.entity.Notificacao;
import br.com.reservasacademicas.entity.Reserva;
import br.com.reservasacademicas.enums.StatusReserva;
import br.com.reservasacademicas.service.ReservaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService reservas;

    public ReservaController(ReservaService reservas) {
        this.reservas = reservas;
    }

    @GetMapping
    public List<Reserva> listar(Authentication autenticacao) {
        return reservas.listar(autenticacao);
    }

    @GetMapping("/{id}")
    public Reserva buscar(
        @PathVariable Long id,
        Authentication autenticacao
    ) {
        return reservas.buscarAutorizada(id, autenticacao);
    }

    @PostMapping
    public Reserva criar(
        @Valid @RequestBody Reserva reserva,
        Authentication autenticacao
    ) {
        return reservas.criar(reserva, autenticacao.getName());
    }

    @PutMapping("/{id}")
    public Reserva alterar(
        @PathVariable Long id,
        @Valid @RequestBody Reserva reserva,
        Authentication autenticacao
    ) {
        return reservas.alterar(id, reserva, autenticacao);
    }

    @PostMapping("/{id}/aprovar")
    public Reserva aprovar(
        @PathVariable Long id,
        Authentication autenticacao
    ) {
        return reservas.mudarStatus(id, StatusReserva.APROVADA, autenticacao);
    }

    @PostMapping("/{id}/rejeitar")
    public Reserva rejeitar(
        @PathVariable Long id,
        Authentication autenticacao
    ) {
        return reservas.mudarStatus(id, StatusReserva.REJEITADA, autenticacao);
    }

    @PostMapping("/{id}/iniciar")
    public Reserva iniciar(
        @PathVariable Long id,
        Authentication autenticacao
    ) {
        return reservas.mudarStatus(id, StatusReserva.EM_USO, autenticacao);
    }

    @PostMapping("/{id}/concluir")
    public Reserva concluir(
        @PathVariable Long id,
        Authentication autenticacao
    ) {
        return reservas.mudarStatus(id, StatusReserva.CONCLUIDA, autenticacao);
    }

    @PostMapping("/{id}/nao-compareceu")
    public Reserva naoCompareceu(
        @PathVariable Long id,
        Authentication autenticacao
    ) {
        return reservas.mudarStatus(
            id,
            StatusReserva.NAO_COMPARECEU,
            autenticacao
        );
    }

    @PostMapping("/{id}/cancelar")
    public Reserva cancelar(
        @PathVariable Long id,
        Authentication autenticacao
    ) {
        return reservas.mudarStatus(id, StatusReserva.CANCELADA, autenticacao);
    }

    @PostMapping("/{id}/retirada")
    public Reserva retirada(
        @PathVariable Long id,
        Authentication autenticacao
    ) {
        return reservas.registrarRetirada(id, autenticacao);
    }

    @PostMapping("/{id}/devolucao")
    public Reserva devolucao(
        @PathVariable Long id,
        Authentication autenticacao
    ) {
        return reservas.registrarDevolucao(id, autenticacao);
    }

    @GetMapping("/{id}/historico")
    public List<Auditoria> historico(
        @PathVariable Long id,
        Authentication autenticacao
    ) {
        return reservas.historico(id, autenticacao);
    }

    @GetMapping("/{id}/notificacoes")
    public List<Notificacao> notificacoes(
        @PathVariable Long id,
        Authentication autenticacao
    ) {
        reservas.buscarAutorizada(id, autenticacao);
        return reservas.notificacoes(id);
    }
}
