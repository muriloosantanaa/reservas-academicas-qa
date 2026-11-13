package br.com.reservasacademicas.entity;

import br.com.reservasacademicas.enums.TipoRecurso;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Registro de uma tentativa de reserva barrada por sobreposição de horário. */
@Entity
@Table(name = "conflitos_evitados")
public class ConflitoEvitado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoRecurso tipoRecurso;

    @Column(nullable = false)
    private Long recursoId;

    @Column(length = 100)
    private String usuario;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fim;

    @Column(nullable = false)
    private LocalDateTime ocorridoEm = LocalDateTime.now();

    protected ConflitoEvitado() {
    }

    public ConflitoEvitado(
        TipoRecurso tipoRecurso,
        Long recursoId,
        String usuario,
        LocalDateTime inicio,
        LocalDateTime fim
    ) {
        this.tipoRecurso = tipoRecurso;
        this.recursoId = recursoId;
        this.usuario = usuario;
        this.inicio = inicio;
        this.fim = fim;
    }

    public Long getId() {
        return id;
    }

    public TipoRecurso getTipoRecurso() {
        return tipoRecurso;
    }

    public Long getRecursoId() {
        return recursoId;
    }

    public String getUsuario() {
        return usuario;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public LocalDateTime getOcorridoEm() {
        return ocorridoEm;
    }
}
