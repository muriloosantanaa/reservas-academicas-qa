package br.com.reservasacademicas.controller;

import br.com.reservasacademicas.entity.PeriodoManutencao;
import br.com.reservasacademicas.service.ManutencaoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ManutencaoController {

    private final ManutencaoService manutencoes;

    public ManutencaoController(ManutencaoService manutencoes) {
        this.manutencoes = manutencoes;
    }

    /** Consulta aberta a qualquer usuário autenticado. */
    @GetMapping("/api/manutencoes")
    public List<PeriodoManutencao> listar() {
        return manutencoes.listar();
    }

    /** Operações de escrita restritas ao ADMIN (ver SecurityConfig). */
    @PostMapping("/api/admin/manutencoes")
    @ResponseStatus(HttpStatus.CREATED)
    public PeriodoManutencao agendar(
        @Valid @RequestBody PeriodoManutencao periodo,
        Authentication autenticacao
    ) {
        return manutencoes.agendar(periodo, autenticacao.getName());
    }

    @DeleteMapping("/api/admin/manutencoes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Long id) {
        manutencoes.cancelar(id);
    }
}
