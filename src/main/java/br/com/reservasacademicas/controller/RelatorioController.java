package br.com.reservasacademicas.controller;

import br.com.reservasacademicas.entity.Reserva;
import br.com.reservasacademicas.enums.StatusReserva;
import br.com.reservasacademicas.repository.ConflitoEvitadoRepository;
import br.com.reservasacademicas.repository.ReservaRepository;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    /** Reservas que não ocupam mais o recurso e não contam como utilização. */
    private static final List<StatusReserva> NAO_EFETIVAS = List.of(
        StatusReserva.REJEITADA,
        StatusReserva.CANCELADA,
        StatusReserva.NAO_COMPARECEU
    );

    private final ReservaRepository reservas;
    private final ConflitoEvitadoRepository conflitos;

    public RelatorioController(
        ReservaRepository reservas,
        ConflitoEvitadoRepository conflitos
    ) {
        this.reservas = reservas;
        this.conflitos = conflitos;
    }

    @GetMapping("/utilizacao")
    public Map<String, Object> utilizacao() {
        List<Reserva> registros = reservas.findAll();

        List<Reserva> efetivas = registros.stream()
            .filter(r -> !NAO_EFETIVAS.contains(r.getStatus()))
            .toList();

        double horasAlocadas = efetivas.stream()
            .mapToDouble(RelatorioController::horas)
            .sum();

        long concluidas = registros.stream()
            .filter(r -> r.getStatus() == StatusReserva.CONCLUIDA)
            .count();

        Map<String, Long> porSala = contar(
            efetivas, r -> r.getSala().getNome()
        );

        Map<String, Long> porProfessor = contar(
            efetivas, r -> r.getProfessor().getNome()
        );

        Map<String, Long> porMaterial = efetivas.stream()
            .filter(r -> r.getMaterial() != null)
            .collect(Collectors.groupingBy(
                r -> r.getMaterial().getNome(),
                Collectors.counting()
            ));

        Map<String, Double> horasPorProfessor = efetivas.stream()
            .collect(Collectors.groupingBy(
                r -> r.getProfessor().getNome(),
                Collectors.summingDouble(RelatorioController::horas)
            ));

        Map<String, Long> porStatus = new LinkedHashMap<>();
        for (StatusReserva status : StatusReserva.values()) {
            porStatus.put(status.name(), 0L);
        }
        registros.forEach(r ->
            porStatus.merge(r.getStatus().name(), 1L, Long::sum)
        );

        Map<String, Long> conflitosPorRecurso = new LinkedHashMap<>();
        for (Object[] linha : conflitos.contarPorTipo()) {
            conflitosPorRecurso.put(
                String.valueOf(linha[0]),
                ((Number) linha[1]).longValue()
            );
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("totalReservas", registros.size());
        resposta.put("reservasConcluidas", concluidas);
        resposta.put("cargaHorariaAlocada", horasAlocadas);
        resposta.put("porSala", porSala);
        resposta.put("porProfessor", porProfessor);
        resposta.put("porMaterial", porMaterial);
        resposta.put("cargaHorariaPorProfessor", horasPorProfessor);
        resposta.put("porStatus", porStatus);
        resposta.put("conflitosEvitados", conflitos.count());
        resposta.put("conflitosEvitadosPorRecurso", conflitosPorRecurso);

        return resposta;
    }

    private static double horas(Reserva reserva) {
        return Duration
            .between(reserva.getInicio(), reserva.getFim())
            .toMinutes() / 60.0;
    }

    private static Map<String, Long> contar(
        List<Reserva> lista,
        Function<Reserva, String> chave
    ) {
        return lista.stream().collect(
            Collectors.groupingBy(chave, Collectors.counting())
        );
    }
}
