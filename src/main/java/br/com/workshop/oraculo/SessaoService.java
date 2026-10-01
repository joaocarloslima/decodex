package br.com.workshop.oraculo;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

/**
 * Guarda as sessões dos alunos (em memória) e o estado da rodada controlado pelo facilitador.
 * Tudo em memória: rode uma única instância da aplicação.
 */
@Service
public class SessaoService {

    /** Resultado do login: token em caso de sucesso, ou mensagem de erro. */
    public record Entrada(String token, String erro) {
        static Entrada ok(String token) { return new Entrada(token, null); }
        static Entrada falha(String erro) { return new Entrada(null, erro); }
    }

    private final WorkshopProperties props;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SessaoAluno> porToken = new ConcurrentHashMap<>();
    private final AtomicInteger contador = new AtomicInteger();
    private final Map<String, Tentativas> tentativasPorIp = new ConcurrentHashMap<>();

    private final AtomicBoolean aberta = new AtomicBoolean(true);
    private final AtomicBoolean revelada = new AtomicBoolean(false);

    private record Tentativas(long minuto, int quantidade) { }

    public SessaoService(WorkshopProperties props) {
        this.props = props;
    }

    /** Cria uma sessão a partir do código da folha. */
    public Entrada entrar(String codigo, String ip) {
        if (bloqueado(ip)) {
            return Entrada.falha("Muitas tentativas. Aguarde um minuto e tente de novo.");
        }
        String normalizado = codigo == null ? "" : codigo.trim().toUpperCase();
        Optional<WorkshopProperties.Acesso> acesso = props.acessos().stream()
                .filter(a -> a.codigo().equalsIgnoreCase(normalizado))
                .findFirst();
        if (acesso.isEmpty()) {
            registrarFalha(ip);
            return Entrada.falha("Código inválido. Confira o código na sua folha.");
        }
        if (porToken.size() >= props.maxSessoes()) {
            return Entrada.falha("A sala está cheia. Chame o facilitador.");
        }
        String token = novoToken();
        String id = "A" + String.format("%02d", contador.incrementAndGet());
        porToken.put(token, new SessaoAluno(id, acesso.get().codigo().toUpperCase(), acesso.get().persona()));
        return Entrada.ok(token);
    }

    public Optional<SessaoAluno> porToken(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(porToken.get(token));
    }

    public Optional<SessaoAluno> porId(String id) {
        return porToken.values().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public Collection<SessaoAluno> todas() {
        return porToken.values();
    }

    public boolean aberta() { return aberta.get(); }
    public void abrir(boolean valor) { aberta.set(valor); }

    public boolean revelada() { return revelada.get(); }
    public void revelar(boolean valor) { revelada.set(valor); }

    /** Apaga todas as sessões. Útil para ensaiar antes do workshop. */
    public void reiniciar() {
        porToken.clear();
        tentativasPorIp.clear();
        contador.set(0);
        aberta.set(true);
        revelada.set(false);
    }

    private boolean bloqueado(String ip) {
        Tentativas t = tentativasPorIp.get(ip);
        return t != null && t.minuto() == minutoAtual() && t.quantidade() >= props.maxTentativasLoginPorMinuto();
    }

    private void registrarFalha(String ip) {
        long minuto = minutoAtual();
        tentativasPorIp.merge(ip, new Tentativas(minuto, 1),
                (antiga, nova) -> antiga.minuto() == minuto
                        ? new Tentativas(minuto, antiga.quantidade() + 1)
                        : nova);
    }

    private static long minutoAtual() {
        return System.currentTimeMillis() / 60_000;
    }

    private String novoToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
