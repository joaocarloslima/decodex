package br.com.workshop.oraculo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import br.com.workshop.oraculo.SessaoAluno.Papel;
import br.com.workshop.oraculo.SessaoAluno.Turno;

/**
 * Conversa com a OpenAI.
 *
 * Concorrência: cada requisição roda numa virtual thread (spring.threads.virtual.enabled), então
 * 30 alunos esperando a OpenAI ao mesmo tempo não ocupam threads de sistema. Um semáforo limita
 * quantas chamadas vão à OpenAI em paralelo (as outras esperam na fila) e cada chamada tem timeout.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String AVISO_REESCREVER_ANGELICA = """

            ATENÇÃO: sua resposta anterior revelou a solução ou a regra, o que é proibido.
            Escreva uma nova resposta curta que NÃO revele nada e termine com uma pergunta.
            """;

    private static final String AVISO_REESCREVER_EVELIN = """

            ATENÇÃO: sua resposta anterior explicou ou deu pistas da regra da cifra, o que é proibido.
            Escreva uma nova resposta curta dizendo que o aluno não precisa saber a regra
            porque você resolve tudo por ele, e ofereça decifrar uma mensagem.
            """;

    /** Erro de negócio com mensagem pronta para o aluno. */
    public static class IndisponivelException extends RuntimeException {
        public IndisponivelException(String mensagem, Throwable causa) { super(mensagem, causa); }
    }

    private final ChatClient chatClient;
    private final WorkshopProperties props;
    private final ConversaLog conversaLog;
    private final LeakGuard leakGuard;
    private final Semaphore vagasOpenAi;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<Persona, String> prompts = new EnumMap<>(Persona.class);

    public ChatService(ChatClient.Builder builder, WorkshopProperties props, ConversaLog conversaLog) {
        this.chatClient = builder.build();
        this.props = props;
        this.conversaLog = conversaLog;
        this.leakGuard = new LeakGuard(props.tecladoLiberadoApos());
        this.vagasOpenAi = new Semaphore(props.maxChamadasSimultaneas(), true);
        for (Persona p : Persona.values()) {
            prompts.put(p, carregar(p.arquivoPrompt()));
        }
    }

    /**
     * Envia a mensagem do aluno para a persona da sessão e devolve a resposta.
     * O tempo total é sempre pelo menos {@code tempoMinimoRespostaMs}, para as duas personas parecerem iguais.
     */
    public String responder(SessaoAluno sessao, String textoAluno) {
        long inicio = System.currentTimeMillis();
        long prazo = inicio + props.timeoutRespostaSegundos() * 1000L;

        List<Message> historico = historico(sessao);
        List<String> mensagensAluno = new ArrayList<>(sessao.mensagensDoAluno());
        mensagensAluno.add(textoAluno);

        String resposta;
        String bloqueio = null;
        if (sessao.persona() == Persona.ANGELICA) {
            resposta = chamar(sessao.persona(), historico, textoAluno, "", prazo);
            bloqueio = leakGuard.motivoBloqueio(resposta, mensagensAluno);
            if (bloqueio != null) {
                log.info("ANGELica bloqueada para {} ({}). Gerando de novo.", sessao.id(), bloqueio);
                resposta = chamar(sessao.persona(), historico, textoAluno, AVISO_REESCREVER_ANGELICA, prazo);
                String segundo = leakGuard.motivoBloqueio(resposta, mensagensAluno);
                if (segundo != null) {
                    resposta = leakGuard.perguntaReserva(mensagensAluno.size());
                    bloqueio = bloqueio + "; " + segundo + "; usou reserva";
                }
            }
        } else {
            resposta = chamar(sessao.persona(), historico, textoAluno, "", prazo);
            bloqueio = leakGuard.motivoBloqueioEvelin(resposta);
            if (bloqueio != null) {
                log.info("EVELin bloqueada para {} ({}). Gerando de novo.", sessao.id(), bloqueio);
                resposta = chamar(sessao.persona(), historico, textoAluno, AVISO_REESCREVER_EVELIN, prazo);
                String segundo = leakGuard.motivoBloqueioEvelin(resposta);
                if (segundo != null) {
                    resposta = LeakGuard.RESERVA_EVELIN;
                    bloqueio = bloqueio + "; " + segundo + "; usou reserva";
                }
            }
        }

        // Só grava a troca depois que a resposta deu certo: se a OpenAI falhar, o aluno reenvia sem duplicar o histórico.
        Turno turnoAluno = new Turno(Instant.ofEpochMilli(inicio), Papel.ALUNO, textoAluno, null);
        Turno turnoAssistente = new Turno(Instant.now(), Papel.ASSISTENTE, resposta, bloqueio);
        sessao.adicionar(turnoAluno);
        sessao.adicionar(turnoAssistente);
        conversaLog.registrar(sessao, turnoAluno);
        conversaLog.registrar(sessao, turnoAssistente);

        esperarTempoMinimo(inicio);
        return resposta;
    }

    /** Uma chamada à OpenAI, respeitando o limite de chamadas simultâneas e o prazo total. */
    private String chamar(Persona persona, List<Message> historico, String textoAluno, String avisoExtra, long prazo) {
        long restante = prazo - System.currentTimeMillis();
        boolean entrou = false;
        try {
            entrou = restante > 0 && vagasOpenAi.tryAcquire(restante, TimeUnit.MILLISECONDS);
            if (!entrou) {
                throw new IndisponivelException("A assistente está muito ocupada. Tente de novo em instantes.", null);
            }
            Future<String> futuro = executor.submit(() -> chatClient.prompt()
                    .system(prompts.get(persona) + avisoExtra)
                    .messages(historico)
                    .user(textoAluno)
                    .call()
                    .content());
            try {
                String conteudo = futuro.get(Math.max(1, prazo - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                return conteudo == null ? "" : conteudo.trim();
            } catch (TimeoutException e) {
                futuro.cancel(true);
                throw new IndisponivelException("A assistente demorou demais. Tente de novo.", e);
            } catch (ExecutionException e) {
                throw new IndisponivelException("A assistente está indisponível agora. Tente de novo.", e.getCause());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IndisponivelException("A assistente está indisponível agora. Tente de novo.", e);
        } finally {
            if (entrou) {
                vagasOpenAi.release();
            }
        }
    }

    /** Chamadas à OpenAI em andamento agora (para o painel). */
    public int chamadasEmAndamento() {
        return props.maxChamadasSimultaneas() - vagasOpenAi.availablePermits();
    }

    /** Histórico enviado ao modelo. Respostas bloqueadas nunca entram: só a versão enviada ao aluno. */
    private List<Message> historico(SessaoAluno sessao) {
        List<Message> mensagens = new ArrayList<>();
        for (Turno t : sessao.turnos()) {
            mensagens.add(t.papel() == Papel.ALUNO
                    ? new UserMessage(t.texto())
                    : new AssistantMessage(t.texto()));
        }
        return mensagens;
    }

    private void esperarTempoMinimo(long inicio) {
        long restante = props.tempoMinimoRespostaMs() - (System.currentTimeMillis() - inicio);
        if (restante > 0) {
            try {
                Thread.sleep(restante);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String carregar(String caminho) {
        try {
            return new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt não encontrado: " + caminho, e);
        }
    }
}
