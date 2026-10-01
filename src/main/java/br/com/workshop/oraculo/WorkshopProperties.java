package br.com.workshop.oraculo;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workshop")
public record WorkshopProperties(
        String adminKey,
        List<Acesso> acessos,
        long tempoMinimoRespostaMs,
        int timeoutRespostaSegundos,
        int maxChamadasSimultaneas,
        int maxMensagensPorAluno,
        int maxCaracteresPorMensagem,
        int maxSessoes,
        int maxTentativasLoginPorMinuto,
        int tecladoLiberadoApos,
        String arquivoLog) {

    /** Um código de acesso e a assistente para a qual ele leva. */
    public record Acesso(String codigo, Persona persona) { }
}
