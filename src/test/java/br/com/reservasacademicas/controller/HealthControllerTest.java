package br.com.reservasacademicas.controller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthControllerTest {
    @Test
    void healthRetornaStatusDaAplicacao() {
        var resposta = new HealthController().health();
        assertEquals("UP", resposta.get("status"));
        assertEquals("reservas-academicas", resposta.get("application"));
    }
}
