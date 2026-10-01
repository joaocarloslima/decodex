package br.com.workshop.oraculo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Grava cada mensagem numa linha JSON (logs/conversas.jsonl) para análise depois do workshop.
 */
@Component
public class ConversaLog {

    private static final Logger log = LoggerFactory.getLogger(ConversaLog.class);

    private final Path arquivo;

    public ConversaLog(WorkshopProperties props) {
        this.arquivo = Path.of(props.arquivoLog());
    }

    public synchronized void registrar(SessaoAluno sessao, SessaoAluno.Turno turno) {
        String linha = "{"
                + "\"horario\":\"" + turno.horario() + "\","
                + "\"aluno\":\"" + sessao.id() + "\","
                + "\"codigo\":\"" + sessao.codigo() + "\","
                + "\"persona\":\"" + sessao.persona().name() + "\","
                + "\"papel\":\"" + turno.papel().name() + "\","
                + "\"texto\":" + json(turno.texto()) + ","
                + "\"bloqueio\":" + (turno.bloqueio() == null ? "null" : json(turno.bloqueio()))
                + "}\n";
        try {
            Path pasta = arquivo.toAbsolutePath().getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }
            Files.writeString(arquivo, linha, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Não consegui gravar o log de conversas: {}", e.getMessage());
        }
    }

    static String json(String texto) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
