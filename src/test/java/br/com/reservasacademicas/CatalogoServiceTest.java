package br.com.reservasacademicas;

import br.com.reservasacademicas.entity.Material;
import br.com.reservasacademicas.entity.Professor;
import br.com.reservasacademicas.entity.Sala;
import br.com.reservasacademicas.enums.TipoMaterial;
import br.com.reservasacademicas.exception.RecursoNaoEncontradoException;
import br.com.reservasacademicas.repository.MaterialRepository;
import br.com.reservasacademicas.repository.ProfessorRepository;
import br.com.reservasacademicas.repository.SalaRepository;
import br.com.reservasacademicas.service.CatalogoService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock private SalaRepository salas;
    @Mock private ProfessorRepository professores;
    @Mock private MaterialRepository materiais;
    @InjectMocks private CatalogoService service;

    @Test
    void deveListarSalas() {
        when(salas.findAll()).thenReturn(List.of(new Sala()));
        assertEquals(1, service.salas().size());
    }

    @Test
    void deveSalvarSalaNova() {
        Sala sala = new Sala();
        when(salas.save(sala)).thenReturn(sala);
        assertSame(sala, service.salvarSala(sala));
        verify(salas, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void deveAtualizarSalaExistente() {
        Sala sala = new Sala();
        sala.setId(1L);
        when(salas.findByIdForUpdate(1L)).thenReturn(Optional.of(sala));
        when(salas.save(sala)).thenReturn(sala);
        assertSame(sala, service.salvarSala(sala));
    }

    @Test
    void atualizarSalaInexistenteDeveFalhar() {
        Sala sala = new Sala();
        sala.setId(99L);
        when(salas.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.salvarSala(sala));
    }

    @Test
    void buscarSalaDeveRetornarOuFalhar() {
        Sala sala = new Sala();
        when(salas.findById(1L)).thenReturn(Optional.of(sala));
        assertSame(sala, service.sala(1L));
        when(salas.findById(2L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.sala(2L));
    }

    @Test
    void excluirSalaDeveBuscarAntesDeExcluir() {
        Sala sala = new Sala();
        when(salas.findById(1L)).thenReturn(Optional.of(sala));
        service.excluirSala(1L);
        verify(salas).delete(sala);
    }

    @Test
    void deveListarEManipularProfessores() {
        Professor professor = new Professor();
        when(professores.findAll()).thenReturn(List.of(professor));
        assertEquals(1, service.professores().size());
        when(professores.save(professor)).thenReturn(professor);
        assertSame(professor, service.salvarProfessor(professor));
        professor.setId(2L);
        when(professores.findByIdForUpdate(2L)).thenReturn(Optional.of(professor));
        assertSame(professor, service.salvarProfessor(professor));
    }

    @Test
    void atualizarProfessorInexistenteDeveFalhar() {
        Professor professor = new Professor();
        professor.setId(99L);
        when(professores.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.salvarProfessor(professor));
    }

    @Test
    void buscarEExcluirProfessor() {
        Professor professor = new Professor();
        when(professores.findById(1L)).thenReturn(Optional.of(professor));
        assertSame(professor, service.professor(1L));
        service.excluirProfessor(1L);
        verify(professores).delete(professor);
        when(professores.findById(2L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.professor(2L));
    }

    @Test
    void deveListarEManipularMateriais() {
        Material material = new Material();
        when(materiais.findAll()).thenReturn(List.of(material));
        assertEquals(1, service.materiais().size());
        when(materiais.save(material)).thenReturn(material);
        assertSame(material, service.salvarMaterial(material));
        material.setId(3L);
        when(materiais.findByIdForUpdate(3L)).thenReturn(Optional.of(material));
        assertSame(material, service.salvarMaterial(material));
    }

    @Test
    void atualizarMaterialInexistenteDeveFalhar() {
        Material material = new Material();
        material.setId(88L);
        when(materiais.findByIdForUpdate(88L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.salvarMaterial(material));
    }

    @Test
    void buscarEExcluirMaterial() {
        Material material = new Material();
        when(materiais.findById(1L)).thenReturn(Optional.of(material));
        assertSame(material, service.material(1L));
        service.excluirMaterial(1L);
        verify(materiais).delete(material);
        when(materiais.findById(2L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.material(2L));
    }

    @Test
    void pesquisarSalasNormalizaLocalizacaoNula() {
        when(salas.findByCapacidadeGreaterThanEqualAndLocalizacaoContainingIgnoreCaseAndAtivaTrue(10, ""))
            .thenReturn(List.of());
        assertTrue(service.pesquisarSalas(10, null).isEmpty());
    }

    @Test
    void pesquisarProfessoresNormalizaCompetenciaNula() {
        when(professores.findByCompetenciaContainingIgnoreCaseAndAtivoTrue(""))
            .thenReturn(List.of());
        assertTrue(service.pesquisarProfessores(null).isEmpty());
    }

    @Test
    void pesquisarMateriaisSemTipoRetornaTodos() {
        when(materiais.findAll()).thenReturn(List.of(new Material()));
        assertEquals(1, service.pesquisarMateriais(null).size());
    }

    @Test
    void pesquisarMateriaisComTipoFiltraDisponiveis() {
        when(materiais.findByTipoAndDisponivelTrueAndEmManutencaoFalse(TipoMaterial.PROJETOR))
            .thenReturn(List.of(new Material()));
        assertEquals(1, service.pesquisarMateriais(TipoMaterial.PROJETOR).size());
    }
}
