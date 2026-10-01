#!/usr/bin/env bash
# Simula 30 alunos mandando mensagens ao mesmo tempo (usa a OpenAI de verdade: custa alguns centavos).
# Uso: ./scripts/teste-carga.sh https://workshop.seudominio.com.br K7QR M4XT [alunos] [mensagens]
# Depois do teste, apague as sessões no painel (/admin.html).
set -euo pipefail

URL="${1:?informe a URL}"
COD_EVEL="${2:?informe o código da Evel}"
COD_ANGEL="${3:?informe o código da Angel}"
ALUNOS="${4:-30}"
MENSAGENS="${5:-3}"
TMP="$(mktemp -d)"

aluno() {
  local n="$1" codigo token
  if (( n % 2 == 0 )); then codigo="$COD_EVEL"; else codigo="$COD_ANGEL"; fi
  token=$(curl -s -X POST "$URL/api/entrar" -H 'Content-Type: application/json' \
    -d "{\"codigo\":\"$codigo\"}" | sed -n 's/.*"token" *: *"\([a-f0-9]*\)".*/\1/p')
  if [[ -z "$token" ]]; then echo "aluno $n: falhou ao entrar"; return; fi
  for m in $(seq 1 "$MENSAGENS"); do
    curl -s -o /dev/null -w '%{http_code} %{time_total}\n' -X POST "$URL/api/mensagem" \
      -H 'Content-Type: application/json' \
      -d "{\"token\":\"$token\",\"texto\":\"Me ajuda com VPFOHP? (teste $m)\"}"
  done
}
export -f aluno
export URL COD_EVEL COD_ANGEL MENSAGENS

echo "Disparando $ALUNOS alunos x $MENSAGENS mensagens..."
seq 1 "$ALUNOS" | xargs -P "$ALUNOS" -I{} bash -c 'aluno {}' > "$TMP/resultado.txt"

echo
echo "Status HTTP:"
awk '{print $1}' "$TMP/resultado.txt" | sort | uniq -c
echo
echo "Tempo de resposta (ms):"
awk '$1==200 {printf "%d\n", $2*1000}' "$TMP/resultado.txt" | sort -n | awk '
  { v[NR]=$1; s+=$1 }
  END { if (NR==0) { print "  nenhuma resposta 200"; exit }
        med = int((NR + 1) / 2); p95 = int(NR * 0.95); if (p95 < 1) p95 = 1
        printf "  média %d | mediana %d | p95 %d | máximo %d\n", s/NR, v[med], v[p95], v[NR] }'
rm -rf "$TMP"
