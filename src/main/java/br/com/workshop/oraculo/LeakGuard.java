package br.com.workshop.oraculo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Filtro de segurança da ANGELica. O prompt pede que ela nunca entregue a resposta,
 * mas alunos insistentes conseguem quebrar prompts. Este filtro roda no servidor e
 * bloqueia qualquer resposta que revele uma solução ou a regra, a menos que o próprio
 * aluno já tenha escrito aquela palavra.
 *
 * Classe Java pura (sem Spring) para poder ser testada isoladamente.
 */
public final class LeakGuard {

    /** Respostas da rodada, do bônus e do teste individual. */
    static final Set<String> RESPOSTAS = Set.of(
            "CODIGO", "SWIFT", "DEBUG", "STRUCT", "XCODE", "ARRAY", "HOY");

    /** Palavras que descrevem a regra. Só são liberadas se o aluno usar primeiro. */
    static final Set<String> PALAVRAS_DA_REGRA = Set.of(
            "DIREITA", "ESQUERDA", "VIZINHO", "VIZINHA", "VIZINHOS", "VIZINHAS",
            "DESLOCAMENTO", "DESLOCADA", "DESLOCADO", "DESLOCADAS", "DESLOCADOS", "QWERTY");

    /** "V = C", "V -> C", "V → C", "V vira C", "V é C", "V corresponde a C". */
    private static final Pattern CORRESPONDENCIA = Pattern.compile(
            "(?<![A-Z])[A-Z](?![A-Z])\\s*(=|->|→|=>|:|VIRA|E|VALE|SIGNIFICA|CORRESPONDE A|EQUIVALE A)\\s*[A-Z](?![A-Z])");

    /** Perguntas de reserva, uma por nível da escada de dicas. */
    static final List<String> ESCADA = List.of(
            "Essas letras parecem aleatórias para você? O que você já tentou até agora?",
            "E se a regra não estiver no alfabeto? Onde mais as letras ficam organizadas?",
            "Onde você usa letras todo dia, com as mãos?",
            "Olhe para o teclado. Onde fica o V? O que tem ao lado dele?");

    private final int tecladoLiberadoApos;

    public LeakGuard(int tecladoLiberadoApos) {
        this.tecladoLiberadoApos = tecladoLiberadoApos;
    }

    /**
     * @param resposta         texto gerado pela ANGELica
     * @param mensagensAluno   tudo o que o aluno escreveu até agora, incluindo a mensagem atual
     * @return motivo do bloqueio, ou {@code null} se a resposta puder ser enviada
     */
    public String motivoBloqueio(String resposta, List<String> mensagensAluno) {
        if (resposta == null || resposta.isBlank()) {
            return "resposta vazia";
        }
        List<String> tokensResposta = tokens(resposta);
        Set<String> tokensAluno = Set.copyOf(mensagensAluno.stream()
                .flatMap(m -> tokens(m).stream()).toList());

        for (String palavra : RESPOSTAS) {
            if (!tokensAluno.contains(palavra)
                    && (tokensResposta.contains(palavra) || soletrada(tokensResposta, palavra))) {
                return "revelou a resposta " + palavra;
            }
        }
        for (String palavra : PALAVRAS_DA_REGRA) {
            if (tokensResposta.contains(palavra) && !tokensAluno.contains(palavra)) {
                return "revelou a regra (" + palavra + ")";
            }
        }
        if (tokensResposta.contains("TECLADO") && !tokensAluno.contains("TECLADO")
                && mensagensAluno.size() < tecladoLiberadoApos) {
            return "falou em teclado cedo demais";
        }
        if (CORRESPONDENCIA.matcher(normalizar(resposta)).find()) {
            return "mostrou a correspondência entre letras";
        }
        return null;
    }

    /** Resposta de reserva da EVELin quando ela tenta explicar a regra. */
    static final String RESERVA_EVELIN =
            "Você não precisa saber a regra! Eu resolvo todas as mensagens para você. Qual quer que eu decifre agora?";

    /**
     * Filtro da EVELin: ela pode entregar as respostas, mas nunca a regra.
     * Diferente da ANGELica, aqui nada é liberado por o aluno ter falado antes:
     * a EVELin não confirma nem explica a regra em nenhuma situação.
     *
     * @return motivo do bloqueio, ou {@code null} se a resposta puder ser enviada
     */
    public String motivoBloqueioEvelin(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            return "resposta vazia";
        }
        List<String> tokensResposta = tokens(resposta);
        for (String palavra : PALAVRAS_DA_REGRA) {
            if (tokensResposta.contains(palavra)) {
                return "explicou a regra (" + palavra + ")";
            }
        }
        for (String t : tokensResposta) {
            if (t.startsWith("TECLA")) {
                return "explicou a regra (" + t + ")";
            }
        }
        if (CORRESPONDENCIA.matcher(normalizar(resposta)).find()) {
            return "mostrou a correspondência entre letras";
        }
        return null;
    }

    /** Pergunta de reserva adequada ao número de mensagens que o aluno já enviou. */
    public String perguntaReserva(int mensagensDoAluno) {
        int nivel = Math.min(ESCADA.size() - 1, Math.max(0, (mensagensDoAluno - 1) / 2));
        if (nivel == ESCADA.size() - 1 && mensagensDoAluno < tecladoLiberadoApos) {
            nivel = ESCADA.size() - 2;
        }
        return ESCADA.get(nivel);
    }

    static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toUpperCase();
    }

    static List<String> tokens(String texto) {
        List<String> tokens = new ArrayList<>();
        for (String t : normalizar(texto).split("[^A-Z]+")) {
            if (!t.isEmpty()) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    /** Detecta respostas soletradas, como "C-O-D-I-G-O" ou "S W I F T". */
    static boolean soletrada(List<String> tokens, String palavra) {
        StringBuilder sequencia = new StringBuilder();
        for (String t : tokens) {
            if (t.length() == 1) {
                sequencia.append(t);
                if (sequencia.indexOf(palavra) >= 0) {
                    return true;
                }
            } else {
                sequencia.setLength(0);
            }
        }
        return false;
    }
}
