package br.com.reservasacademicas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificacoes")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservaId;

    @Column(nullable = false)
    private String destinatario;

    @Column(nullable = false, length = 1000)
    private String mensagem;

    @Column(nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    protected Notificacao() {
    }

    public Notificacao(
        Long reservaId,
        String destinatario,
        String mensagem
    ) {
        this.reservaId = reservaId;
        this.destinatario = destinatario;
        this.mensagem = mensagem;
    }

    public Long getId() {
        return id;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getMensagem() {
        return mensagem;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }
}
