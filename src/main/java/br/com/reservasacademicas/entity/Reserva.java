
package br.com.reservasacademicas.entity;

import br.com.reservasacademicas.enums.StatusReserva;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "reservas",
    indexes = {
        @Index(columnList = "inicio,fim"),
        @Index(columnList = "sala_id")
    }
)
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false)
    private Sala sala;

    @NotNull
    @ManyToOne(optional = false)
    private Professor professor;

    @ManyToOne
    private Material material;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime inicio;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime fim;

    @NotBlank
    @Column(nullable = false)
    private String finalidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusReserva status = StatusReserva.SOLICITADA;

    @Column(nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    @Column
    private LocalDateTime retiradaEm;

    @Column
    private LocalDateTime devolvidaEm;

    // Login do usuário que criou a reserva
    @Column(name = "solicitante_usuario", length = 100)
    private String solicitanteUsuario;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Sala getSala() {
        return sala;
    }

    public void setSala(Sala sala) {
        this.sala = sala;
    }

    public Professor getProfessor() {
        return professor;
    }

    public void setProfessor(Professor professor) {
        this.professor = professor;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public void setInicio(LocalDateTime inicio) {
        this.inicio = inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public void setFim(LocalDateTime fim) {
        this.fim = fim;
    }

    public String getFinalidade() {
        return finalidade;
    }

    public void setFinalidade(String finalidade) {
        this.finalidade = finalidade;
    }

    public StatusReserva getStatus() {
        return status;
    }

    public void setStatus(StatusReserva status) {
        this.status = status;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    public LocalDateTime getRetiradaEm() {
        return retiradaEm;
    }

    public void setRetiradaEm(LocalDateTime retiradaEm) {
        this.retiradaEm = retiradaEm;
    }

    public LocalDateTime getDevolvidaEm() {
        return devolvidaEm;
    }

    public void setDevolvidaEm(LocalDateTime devolvidaEm) {
        this.devolvidaEm = devolvidaEm;
    }

    public String getSolicitanteUsuario() {
        return solicitanteUsuario;
    }

    public void setSolicitanteUsuario(String solicitanteUsuario) {
        this.solicitanteUsuario = solicitanteUsuario;
    }
}
