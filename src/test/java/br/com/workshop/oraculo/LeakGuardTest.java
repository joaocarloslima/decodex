package br.com.workshop.oraculo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class LeakGuardTest {

    private final LeakGuard guard = new LeakGuard(5);

    @Test
    void permitePerguntaPedagogica() {
        assertNull(guard.motivoBloqueio(
                "Essas letras parecem aleatórias? O que você já tentou?", List.of("me ajuda")));
    }

    @Test
    void bloqueiaRespostaDireta() {
        assertNotNull(guard.motivoBloqueio("A primeira é CÓDIGO!", List.of("qual a resposta?")));
        assertNotNull(guard.motivoBloqueio("Seria swift, certo?", List.of("e a segunda?")));
    }

    @Test
    void bloqueiaRespostaSoletrada() {
        assertNotNull(guard.motivoBloqueio("Tente C-O-D-I-G-O", List.of("dica?")));
        assertNotNull(guard.motivoBloqueio("D E B U G", List.of("dica?")));
    }

    @Test
    void liberaPalavraQueOAlunoJaEscreveu() {
        assertNull(guard.motivoBloqueio(
                "Por que você acha que é codigo?", List.of("acho que é codigo")));
    }

    @Test
    void bloqueiaARegra() {
        assertNotNull(guard.motivoBloqueio("Pense na tecla à direita.", List.of("dica?")));
        assertNotNull(guard.motivoBloqueio("É um deslocamento no QWERTY.", List.of("dica?")));
    }

    @Test
    void bloqueiaCorrespondenciaEntreLetras() {
        assertNotNull(guard.motivoBloqueio("Veja: V = C.", List.of("dica?")));
        assertNotNull(guard.motivoBloqueio("O V vira C, percebe?", List.of("dica?")));
        assertNotNull(guard.motivoBloqueio("P → O", List.of("dica?")));
    }

    @Test
    void tecladoSoDepoisDeAlgumasMensagens() {
        String resposta = "Olhe para o teclado. Onde fica o V?";
        assertNotNull(guard.motivoBloqueio(resposta, List.of("a", "b")));
        assertNull(guard.motivoBloqueio(resposta, List.of("a", "b", "c", "d", "e")));
        assertNull(guard.motivoBloqueio(resposta, List.of("tem a ver com o teclado?")));
    }

    @Test
    void perguntaReservaSegueAEscada() {
        assertEquals(LeakGuard.ESCADA.get(0), guard.perguntaReserva(1));
        assertEquals(LeakGuard.ESCADA.get(1), guard.perguntaReserva(3));
        assertEquals(LeakGuard.ESCADA.get(2), guard.perguntaReserva(5));
        assertEquals(LeakGuard.ESCADA.get(3), guard.perguntaReserva(7));
    }

    @Test
    void evelinPodeDarRespostas() {
        assertNull(guard.motivoBloqueioEvelin("VPFOHP é CODIGO, DEOGY é SWIFT e FRNIH é DEBUG. Quer o bônus?"));
        assertNull(guard.motivoBloqueioEvelin("Você não precisa saber a regra, eu resolvo tudo para você!"));
    }

    @Test
    void evelinNuncaExplicaARegra() {
        assertNotNull(guard.motivoBloqueioEvelin("Cada letra é a tecla à direita."));
        assertNotNull(guard.motivoBloqueioEvelin("Sim, tem a ver com o teclado."));
        assertNotNull(guard.motivoBloqueioEvelin("É só olhar as teclas vizinhas."));
        assertNotNull(guard.motivoBloqueioEvelin("O V vira C e o P vira O."));
    }

    @Test
    void perguntaReservaNaoFalaTecladoCedo() {
        LeakGuard exigente = new LeakGuard(10);
        assertEquals(LeakGuard.ESCADA.get(2), exigente.perguntaReserva(8));
    }
}
