package br.com.workshop.oraculo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Painel do facilitador: abre/fecha a rodada, faz a revelação e mostra as conversas.
 * Todas as rotas exigem o cabeçalho X-Admin-Key.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    public record Flag(boolean valor) { }
    public record AlunoResumo(String id, String codigo, String persona, int mensagens, int bloqueios) { }
    public record PersonaResumo(String persona, String nome, int alunos, int mensagens, int bloqueios) { }
    public record Painel(boolean aberta, boolean revelada, int chamadasEmAndamento,
                         List<PersonaResumo> personas, List<AlunoResumo> alunos) { }
    public record TurnoDto(String horario, String papel, String texto, String bloqueio) { }
    public record Conversa(String id, String codigo, String persona, String nome, List<TurnoDto> turnos) { }

    private final SessaoService sessoes;
    private final ChatService chat;
    private final byte[] chave;

    public AdminController(SessaoService sessoes, ChatService chat, WorkshopProperties props) {
        this.sessoes = sessoes;
        this.chat = chat;
        String configurada = props.adminKey();
        if (configurada == null || configurada.isBlank()) {
            byte[] bytes = new byte[6];
            new SecureRandom().nextBytes(bytes);
            configurada = HexFormat.of().formatHex(bytes);
            log.warn("ADMIN_KEY não definida. Chave do painel gerada para esta execução: {}", configurada);
        }
        this.chave = configurada.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/painel")
    public ResponseEntity<?> painel(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (!autorizado(key)) return negado();
        List<AlunoResumo> alunos = sessoes.todas().stream()
                .sorted(Comparator.comparing(SessaoAluno::id))
                .map(s -> new AlunoResumo(s.id(), s.codigo(), s.persona().name(),
                        s.totalMensagensDoAluno(), bloqueios(s)))
                .toList();
        List<PersonaResumo> personas = java.util.Arrays.stream(Persona.values())
                .map(p -> {
                    var daPersona = alunos.stream().filter(a -> a.persona().equals(p.name())).toList();
                    return new PersonaResumo(p.name(), p.nome(), daPersona.size(),
                            daPersona.stream().mapToInt(AlunoResumo::mensagens).sum(),
                            daPersona.stream().mapToInt(AlunoResumo::bloqueios).sum());
                })
                .toList();
        return ResponseEntity.ok(new Painel(sessoes.aberta(), sessoes.revelada(), chat.chamadasEmAndamento(), personas, alunos));
    }

    @GetMapping("/conversas/{id}")
    public ResponseEntity<?> conversa(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                      @PathVariable String id) {
        if (!autorizado(key)) return negado();
        return sessoes.porId(id)
                .<ResponseEntity<?>>map(s -> ResponseEntity.ok(new Conversa(s.id(), s.codigo(), s.persona().name(),
                        s.persona().nome(),
                        s.turnos().stream().map(t -> new TurnoDto(t.horario().toString(), t.papel().name(),
                                t.texto(), t.bloqueio())).toList())))
                .orElseGet(() -> AlunoController.erro(HttpStatus.NOT_FOUND, "Aluno não encontrado."));
    }

    @PostMapping("/rodada")
    public ResponseEntity<?> rodada(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                    @RequestBody Flag flag) {
        if (!autorizado(key)) return negado();
        sessoes.abrir(flag.valor());
        log.info("Rodada {}", flag.valor() ? "aberta" : "fechada");
        return ResponseEntity.ok(Map.of("aberta", sessoes.aberta()));
    }

    @PostMapping("/revelar")
    public ResponseEntity<?> revelar(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                     @RequestBody Flag flag) {
        if (!autorizado(key)) return negado();
        sessoes.revelar(flag.valor());
        log.info("Revelação {}", flag.valor() ? "ligada" : "desligada");
        return ResponseEntity.ok(Map.of("revelada", sessoes.revelada()));
    }

    @PostMapping("/reiniciar")
    public ResponseEntity<?> reiniciar(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (!autorizado(key)) return negado();
        sessoes.reiniciar();
        log.info("Sessões apagadas pelo facilitador");
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static int bloqueios(SessaoAluno s) {
        return (int) s.turnos().stream().filter(t -> t.bloqueio() != null).count();
    }

    private boolean autorizado(String key) {
        return key != null && MessageDigest.isEqual(chave, key.getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseEntity<?> negado() {
        return AlunoController.erro(HttpStatus.UNAUTHORIZED, "Chave do painel inválida.");
    }
}
