package br.com.workshop.oraculo;

/**
 * As duas assistentes. O nome real só aparece para o aluno depois da revelação.
 */
public enum Persona {

    EVELIN("EVELin", "o Oráculo", "Ela te dava as respostas prontas.", "prompts/evelin.txt"),
    ANGELICA("ANGELica", "a Tutora", "Ela só te fazia perguntas.", "prompts/angelica.txt");

    private final String nome;
    private final String papel;
    private final String descricao;
    private final String arquivoPrompt;

    Persona(String nome, String papel, String descricao, String arquivoPrompt) {
        this.nome = nome;
        this.papel = papel;
        this.descricao = descricao;
        this.arquivoPrompt = arquivoPrompt;
    }

    public String nome() { return nome; }
    public String papel() { return papel; }
    public String descricao() { return descricao; }
    public String arquivoPrompt() { return arquivoPrompt; }
}
