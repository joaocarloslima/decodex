# Oráculo x Tutor

Aplicação da atividade "Mensagens Secretas". Cada aluno entra com o código da sua folha e conversa com uma assistente de IA. Um código leva à **EVELin** (Oráculo: resolve tudo pelo aluno e nunca ensina a regra). O outro leva à **ANGELica** (Tutora: só faz perguntas). Os alunos veem apenas "Assistente" até você fazer a revelação no painel.

Stack: Java 21, Spring Boot 4.1.1, Spring AI 2.0.1, OpenAI. Deploy: veja [DEPLOY.md](DEPLOY.md).

## Rodar localmente

```bash
export OPENAI_API_KEY=sk-...
export ADMIN_KEY=uma-senha-sua
mvn spring-boot:run
```

- Tela do aluno: `http://localhost:8080`
- Painel do facilitador: `http://localhost:8080/admin.html`

Se `ADMIN_KEY` não for definida, uma chave aleatória é gerada e aparece no log.

Use um modelo que aceite `temperature` (padrão `gpt-4.1-mini`). Os modelos da família gpt-5 só aceitam o valor padrão.

## Códigos de acesso

| Grupo | Código padrão | Variável | Assistente |
|---|---|---|---|
| Evel | K7QR | `CODIGO_EVEL` | EVELin |
| Angel | M4XT | `CODIGO_ANGEL` | ANGELica |

Os códigos parecem aleatórios de propósito. Imprima metade das folhas com cada um e misture antes de distribuir.

## Como funciona

- **Persona escondida.** O navegador recebe só um token aleatório. O mapeamento código→persona, o nome e o prompt ficam no servidor. `/api/estado` só devolve o nome depois da revelação.
- **Mesmo tempo de resposta.** Toda resposta leva pelo menos 2 s, para ninguém perceber diferença entre as assistentes.
- **Filtros no servidor** (`LeakGuard`):
  - **ANGELica:** bloqueia respostas com uma solução (inclusive soletrada), palavras da regra, correspondências como "V = C" ou "teclado" cedo demais. Palavras que o aluno já escreveu ficam liberadas.
  - **EVELin:** pode dar as respostas, mas bloqueia qualquer explicação ou pista da regra (tecla, teclado, direita, esquerda, QWERTY, "V vira C"), mesmo que o aluno acerte o palpite.
  - Se bloquear, gera de novo com um aviso. Se bloquear outra vez, envia uma resposta de reserva.
- **Concorrência.** Virtual threads, semáforo de chamadas à OpenAI, timeout por mensagem e uma mensagem por vez por aluno. Detalhes em [DEPLOY.md](DEPLOY.md).
- **Controle da rodada.** No painel: *Fechar rodada* trava o chat e mostra "Feche o notebook". *Revelar nomes* troca "Assistente" por EVELin/ANGELica na tela de cada aluno.
- **Conversas.** O painel mostra as duas assistentes lado a lado, para projetar na revelação. Tudo fica gravado em `logs/conversas.jsonl`.

## Testes

```bash
mvn test                                   # testes dos filtros
./scripts/teste-carga.sh URL K7QR M4XT     # 30 alunos simultâneos (usa a OpenAI de verdade)
```

## Estrutura

```
src/main/java/br/com/workshop/oraculo/
  AlunoController.java    /api/entrar, /api/estado, /api/mensagem
  AdminController.java    /api/admin/* (cabeçalho X-Admin-Key)
  ChatService.java        chamada ao modelo, filtros, fila, timeout, tempo mínimo, log
  LeakGuard.java          filtros anti-vazamento (Java puro, testável)
  SessaoService.java      sessões em memória, códigos e estado da rodada
  ConversaLog.java        grava logs/conversas.jsonl
src/main/resources/
  prompts/angelica.txt    prompt da Tutora
  prompts/evelin.txt      prompt do Oráculo
  static/index.html       tela do aluno
  static/admin.html       painel do facilitador
Dockerfile, docker-compose.yml, Caddyfile, .env.example   deploy
scripts/teste-carga.sh    teste de carga
```
