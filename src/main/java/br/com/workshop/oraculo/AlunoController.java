package br.com.workshop.oraculo;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * API usada pela tela do aluno. Nenhuma resposta daqui revela a persona antes da revelação.
 */
@RestController
@RequestMapping("/api")
public class AlunoController {

    private static final Logger log = LoggerFactory.getLogger(AlunoController.class);

    public record EntrarRequest(String codigo) { }
    public record EntrarResponse(String token) { }
    public record MensagemRequest(String token, String texto) { }
    public record MensagemResponse(String resposta) { }
    public record EstadoResponse(boolean aberta, boolean revelada,
                                 String nome, String papel, String descricao) { }

    private final SessaoService sessoes;
    private final ChatService chat;
    private final WorkshopProperties props;

    public AlunoController(SessaoService sessoes, ChatService chat, WorkshopProperties props) {
        this.sessoes = sessoes;
        this.chat = chat;
        this.props = props;
    }

    @PostMapping("/entrar")
    public ResponseEntity<?> entrar(@RequestBody EntrarRequest req, HttpServletRequest http) {
        SessaoService.Entrada entrada = sessoes.entrar(req.codigo(), http.getRemoteAddr());
        if (entrada.token() == null) {
            return erro(HttpStatus.BAD_REQUEST, entrada.erro());
        }
        return ResponseEntity.ok(new EntrarResponse(entrada.token()));
    }

    @GetMapping("/estado")
    public ResponseEntity<?> estado(@RequestParam String token) {
        return sessoes.porToken(token)
                .<ResponseEntity<?>>map(s -> {
                    boolean revelada = sessoes.revelada();
                    Persona p = s.persona();
                    return ResponseEntity.ok(new EstadoResponse(sessoes.aberta(), revelada,
                            revelada ? p.nome() : null,
                            revelada ? p.papel() : null,
                            revelada ? p.descricao() : null));
                })
                .orElseGet(() -> erro(HttpStatus.UNAUTHORIZED, "Sessão não encontrada. Entre de novo com o código."));
    }

    @PostMapping("/mensagem")
    public ResponseEntity<?> mensagem(@RequestBody MensagemRequest req) {
        var sessao = sessoes.porToken(req.token()).orElse(null);
        if (sessao == null) {
            return erro(HttpStatus.UNAUTHORIZED, "Sessão não encontrada. Entre de novo com o código.");
        }
        if (!sessoes.aberta()) {
            return erro(HttpStatus.LOCKED, "A rodada terminou. Feche o notebook e aguarde.");
        }
        String texto = req.texto() == null ? "" : req.texto().strip();
        if (texto.isEmpty()) {
            return erro(HttpStatus.BAD_REQUEST, "Escreva uma mensagem.");
        }
        if (texto.length() > props.maxCaracteresPorMensagem()) {
            return erro(HttpStatus.BAD_REQUEST,
                    "Mensagem longa demais (máximo " + props.maxCaracteresPorMensagem() + " caracteres).");
        }
        if (sessao.totalMensagensDoAluno() >= props.maxMensagensPorAluno()) {
            return erro(HttpStatus.TOO_MANY_REQUESTS, "Você atingiu o limite de mensagens desta rodada.");
        }
        if (!sessao.emAndamento().tryLock()) {
            return erro(HttpStatus.TOO_MANY_REQUESTS, "Aguarde a resposta anterior.");
        }
        try {
            return ResponseEntity.ok(new MensagemResponse(chat.responder(sessao, texto)));
        } catch (ChatService.IndisponivelException e) {
            log.warn("Falha ao responder {}: {}", sessao.id(), e.getCause() == null ? e.getMessage() : e.getCause().toString());
            return erro(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Erro inesperado para {}", sessao.id(), e);
            return erro(HttpStatus.SERVICE_UNAVAILABLE, "A assistente está indisponível agora. Tente de novo.");
        } finally {
            sessao.emAndamento().unlock();
        }
    }

    static ResponseEntity<Map<String, String>> erro(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("erro", mensagem));
    }
}
