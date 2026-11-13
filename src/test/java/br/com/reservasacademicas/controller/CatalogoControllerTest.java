package br.com.reservasacademicas.controller;

import br.com.reservasacademicas.entity.Material;
import br.com.reservasacademicas.entity.Professor;
import br.com.reservasacademicas.entity.Sala;
import br.com.reservasacademicas.enums.TipoMaterial;
import br.com.reservasacademicas.service.CatalogoService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogoControllerTest {
    private CatalogoService service;
    private CatalogoController controller;

    @BeforeEach
    void configurar() {
        service = mock(CatalogoService.class);
        controller = new CatalogoController(service);
    }

    @Test
    void endpointsDeSalaDelegamAoServico() {
        Sala sala = new Sala();
        when(service.salas()).thenReturn(List.of(sala));
        when(service.pesquisarSalas(20, "Bloco A")).thenReturn(List.of(sala));
        assertEquals(1, controller.salas().size());
        assertEquals(1, controller.pesquisarSalas(20, "Bloco A").size());
        when(service.salvarSala(sala)).thenReturn(sala);
        assertSame(sala, controller.criarSala(sala));
        assertSame(sala, controller.editarSala(7L, sala));
        assertEquals(7L, sala.getId());
        controller.excluirSala(7L);
        verify(service).excluirSala(7L);
    }

    @Test
    void endpointsDeProfessorDelegamAoServico() {
        Professor professor = new Professor();
        when(service.professores()).thenReturn(List.of(professor));
        when(service.pesquisarProfessores("Java")).thenReturn(List.of(professor));
        assertEquals(1, controller.professores().size());
        assertEquals(1, controller.pesquisarProfessores("Java").size());
        when(service.salvarProfessor(professor)).thenReturn(professor);
        assertSame(professor, controller.criarProfessor(professor));
        assertSame(professor, controller.editarProfessor(8L, professor));
        assertEquals(8L, professor.getId());
        controller.excluirProfessor(8L);
        verify(service).excluirProfessor(8L);
    }

    @Test
    void endpointsDeMaterialDelegamAoServico() {
        Material material = new Material();
        when(service.materiais()).thenReturn(List.of(material));
        when(service.pesquisarMateriais(TipoMaterial.PROJETOR)).thenReturn(List.of(material));
        assertEquals(1, controller.materiais().size());
        assertEquals(1, controller.pesquisarMateriais(TipoMaterial.PROJETOR).size());
        when(service.salvarMaterial(material)).thenReturn(material);
        assertSame(material, controller.criarMaterial(material));
        assertSame(material, controller.editarMaterial(9L, material));
        assertEquals(9L, material.getId());
        controller.excluirMaterial(9L);
        verify(service).excluirMaterial(9L);
    }
}
