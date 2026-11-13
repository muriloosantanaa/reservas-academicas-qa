package br.com.reservasacademicas.controller;

import br.com.reservasacademicas.entity.Material;
import br.com.reservasacademicas.entity.Professor;
import br.com.reservasacademicas.entity.Sala;
import br.com.reservasacademicas.enums.TipoMaterial;
import br.com.reservasacademicas.service.CatalogoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CatalogoController {

    private final CatalogoService catalogo;

    public CatalogoController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping("/salas")
    public List<Sala> salas() {
        return catalogo.salas();
    }

    @GetMapping("/salas/pesquisa")
    public List<Sala> pesquisarSalas(
        @RequestParam(defaultValue = "1") int capacidade,
        @RequestParam(required = false) String localizacao
    ) {
        return catalogo.pesquisarSalas(capacidade, localizacao);
    }

    @PostMapping("/admin/salas")
    public Sala criarSala(@Valid @RequestBody Sala sala) {
        return catalogo.salvarSala(sala);
    }

    @PutMapping("/admin/salas/{id}")
    public Sala editarSala(
        @PathVariable Long id,
        @Valid @RequestBody Sala sala
    ) {
        sala.setId(id);
        return catalogo.salvarSala(sala);
    }

    @DeleteMapping("/admin/salas/{id}")
    public void excluirSala(@PathVariable Long id) {
        catalogo.excluirSala(id);
    }

    @GetMapping("/professores")
    public List<Professor> professores() {
        return catalogo.professores();
    }

    @GetMapping("/professores/pesquisa")
    public List<Professor> pesquisarProfessores(
        @RequestParam(required = false) String competencia
    ) {
        return catalogo.pesquisarProfessores(competencia);
    }

    @PostMapping("/admin/professores")
    public Professor criarProfessor(@Valid @RequestBody Professor professor) {
        return catalogo.salvarProfessor(professor);
    }

    @PutMapping("/admin/professores/{id}")
    public Professor editarProfessor(
        @PathVariable Long id,
        @Valid @RequestBody Professor professor
    ) {
        professor.setId(id);
        return catalogo.salvarProfessor(professor);
    }

    @DeleteMapping("/admin/professores/{id}")
    public void excluirProfessor(@PathVariable Long id) {
        catalogo.excluirProfessor(id);
    }

    @GetMapping("/materiais")
    public List<Material> materiais() {
        return catalogo.materiais();
    }

    @GetMapping("/materiais/pesquisa")
    public List<Material> pesquisarMateriais(
        @RequestParam(required = false) TipoMaterial tipo
    ) {
        return catalogo.pesquisarMateriais(tipo);
    }

    @PostMapping("/admin/materiais")
    public Material criarMaterial(@Valid @RequestBody Material material) {
        return catalogo.salvarMaterial(material);
    }

    @PutMapping("/admin/materiais/{id}")
    public Material editarMaterial(
        @PathVariable Long id,
        @Valid @RequestBody Material material
    ) {
        material.setId(id);
        return catalogo.salvarMaterial(material);
    }

    @DeleteMapping("/admin/materiais/{id}")
    public void excluirMaterial(@PathVariable Long id) {
        catalogo.excluirMaterial(id);
    }
}
