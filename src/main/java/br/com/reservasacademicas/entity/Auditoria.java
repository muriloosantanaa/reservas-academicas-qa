package br.com.reservasacademicas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "auditorias")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservaId;

    @Column(nullable = false)
    private String acao;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private LocalDateTime ocorridoEm = LocalDateTime.now();

    protected Auditoria() {
    }

    public Auditoria(Long reservaId, String acao, String usuario) {
        this.reservaId = reservaId;
        this.acao = acao;
        this.usuario = usuario;
    }

    public Long getId() {
        return id;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public String getAcao() {
        return acao;
    }

    public String getUsuario() {
        return usuario;
    }

    public LocalDateTime getOcorridoEm() {
        return ocorridoEm;
    }
}
