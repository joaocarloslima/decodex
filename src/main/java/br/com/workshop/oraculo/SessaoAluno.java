package br.com.workshop.oraculo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Uma conversa de um aluno. Fica só no servidor: o navegador conhece apenas o token.
 */
public final class SessaoAluno {

    public enum Papel { ALUNO, ASSISTENTE }

    public record Turno(Instant horario, Papel papel, String texto, String bloqueio) { }

    private final String id;
    private final String codigo;
    private final Persona persona;
    private final Instant criadaEm = Instant.now();
    private final List<Turno> turnos = new ArrayList<>();

    /** Garante uma mensagem por vez por aluno (evita histórico embaralhado com cliques duplos). */
    private final ReentrantLock emAndamento = new ReentrantLock();

    public SessaoAluno(String id, String codigo, Persona persona) {
        this.id = id;
        this.codigo = codigo;
        this.persona = persona;
    }

    public String id() { return id; }
    public String codigo() { return codigo; }
    public Persona persona() { return persona; }
    public Instant criadaEm() { return criadaEm; }
    public ReentrantLock emAndamento() { return emAndamento; }

    public synchronized void adicionar(Turno turno) {
        turnos.add(turno);
    }

    public synchronized List<Turno> turnos() {
        return List.copyOf(turnos);
    }

    public synchronized List<String> mensagensDoAluno() {
        return turnos.stream().filter(t -> t.papel() == Papel.ALUNO).map(Turno::texto).toList();
    }

    public synchronized int totalMensagensDoAluno() {
        return (int) turnos.stream().filter(t -> t.papel() == Papel.ALUNO).count();
    }
}
