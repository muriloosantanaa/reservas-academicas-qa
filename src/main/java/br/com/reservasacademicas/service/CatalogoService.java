package br.com.reservasacademicas.service;

import br.com.reservasacademicas.entity.Material;
import br.com.reservasacademicas.entity.Professor;
import br.com.reservasacademicas.entity.Sala;
import br.com.reservasacademicas.enums.TipoMaterial;
import br.com.reservasacademicas.exception.RecursoNaoEncontradoException;
import br.com.reservasacademicas.repository.MaterialRepository;
import br.com.reservasacademicas.repository.ProfessorRepository;
import br.com.reservasacademicas.repository.SalaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogoService {

    private final SalaRepository salas;
    private final ProfessorRepository professores;
    private final MaterialRepository materiais;

    public CatalogoService(
        SalaRepository salas,
        ProfessorRepository professores,
        MaterialRepository materiais
    ) {
        this.salas = salas;
        this.professores = professores;
        this.materiais = materiais;
    }

    public List<Sala> salas() {
        return salas.findAll();
    }

    @Transactional
    public Sala salvarSala(Sala sala) {
        if (sala.getId() != null) {
            salas.findByIdForUpdate(sala.getId())
                .orElseThrow(() ->
                    new RecursoNaoEncontradoException("Sala não encontrada")
                );
        }

        return salas.save(sala);
    }

    public Sala sala(Long id) {
        return salas.findById(id)
            .orElseThrow(() ->
                new RecursoNaoEncontradoException("Sala não encontrada")
            );
    }

    public void excluirSala(Long id) {
        salas.delete(sala(id));
    }

    public List<Professor> professores() {
        return professores.findAll();
    }

    @Transactional
    public Professor salvarProfessor(Professor professor) {
        if (professor.getId() != null) {
            professores.findByIdForUpdate(professor.getId())
                .orElseThrow(() ->
                    new RecursoNaoEncontradoException(
                        "Professor não encontrado"
                    )
                );
        }

        return professores.save(professor);
    }

    public Professor professor(Long id) {
        return professores.findById(id)
            .orElseThrow(() ->
                new RecursoNaoEncontradoException("Professor não encontrado")
            );
    }

    public void excluirProfessor(Long id) {
        professores.delete(professor(id));
    }

    public List<Material> materiais() {
        return materiais.findAll();
    }

    @Transactional
    public Material salvarMaterial(Material material) {
        if (material.getId() != null) {
            materiais.findByIdForUpdate(material.getId())
                .orElseThrow(() ->
                    new RecursoNaoEncontradoException(
                        "Material não encontrado"
                    )
                );
        }

        return materiais.save(material);
    }

    public Material material(Long id) {
        return materiais.findById(id)
            .orElseThrow(() ->
                new RecursoNaoEncontradoException("Material não encontrado")
            );
    }

    public void excluirMaterial(Long id) {
        materiais.delete(material(id));
    }

    public List<Sala> pesquisarSalas(int capacidade, String localizacao) {
        return salas.findByCapacidadeGreaterThanEqualAndLocalizacaoContainingIgnoreCaseAndAtivaTrue(
            capacidade,
            localizacao == null ? "" : localizacao
        );
    }

    public List<Professor> pesquisarProfessores(String competencia) {
        return professores.findByCompetenciaContainingIgnoreCaseAndAtivoTrue(
            competencia == null ? "" : competencia
        );
    }

    public List<Material> pesquisarMateriais(TipoMaterial tipo) {
        if (tipo == null) {
            return materiais.findAll();
        }

        return materiais.findByTipoAndDisponivelTrueAndEmManutencaoFalse(tipo);
    }
}
