#!/usr/bin/env bash
# Estrés de 25.000 caravanas (500 × 50) con el stack en modo carga (ver README, "Orden de una corrida"). Desde la raíz:
#   loadtest/estres.sh rafaga                      ráfaga hasta que se corta; reinicia el worker al final
#   loadtest/estres.sh estable [90m] [corrida]     carga sostenida, con una anomalía de cada tipo por establecimiento
#                                                  (arrancan a los 5 min) y su latencia al terminar
# Deja todo en loadtest/resultados/<corrida>/ (por defecto stress-<modo>).
set -euo pipefail
MODO=$1
DURACION=${2:-90m}
CORRIDA=${3:-stress-$MODO}
DIR=loadtest/resultados/$CORRIDA
DC="docker compose -f infra/docker-compose.yml -f infra/docker-compose.carga.yml"
PSQL="docker exec -i vigia-db psql -U vigia_user -d vigia"
ANOMALIAS=""
[ "$MODO" = estable ] && ANOMALIAS="-v celo=1 -v salud=1 -v aislamiento=1"
rm -rf "$DIR"
mkdir -p "$DIR"
export PYTHONIOENCODING=utf-8
log() { echo "$CORRIDA: $* $(date +%H:%M:%S)" | tee -a "$DIR/bitacora.txt"; }
salud_worker() { until curl -sf localhost:8081/actuator/health > /dev/null; do sleep 5; done; }

# Semilla por defecto: 24 h, las últimas 6 h pendientes para celo y salud.
docker stop vigia-backend-worker > /dev/null
$PSQL < loadtest/limpiar.sql > /dev/null
cat loadtest/trayectoria.sql loadtest/seed.sql | $PSQL -q -v perfil="$(cat loadtest/perfil.json)" $ANOMALIAS \
  | tee "$DIR/semilla.txt"
INICIO=$($PSQL -qtA -c "SELECT (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::timestamp(0)")
log "semilla lista (inicio $INICIO)"
docker start vigia-backend-worker > /dev/null
salud_worker
log "worker listo"

set +e
if [ "$MODO" = rafaga ]; then
  python loadtest/observar.py "$CORRIDA" --minutos 12 > /dev/null &
  OBSERVADOR=$!
  $DC run --rm k6 run -q -e ESCENARIOS=rafaga --out csv="resultados/$CORRIDA/k6.csv.gz" carga.js \
    > "$DIR/k6-resumen.txt" 2>&1
  log "k6 terminó con código $?"
else
  python loadtest/observar.py "$CORRIDA" --minutos "$((${DURACION%m} + 1))" > /dev/null &
  OBSERVADOR=$!
  $DC run --rm k6 run -q -e ESCENARIOS=sensores,clientes_streams,clientes_navegacion -e DURACION="$DURACION" \
    -e CELO=1 -e SALUD=1 -e AISLAMIENTO=1 -e ESCALONAR=10m --out csv="resultados/$CORRIDA/k6.csv.gz" carga.js \
    > "$DIR/k6-resumen.txt" 2>&1
  log "k6 terminó con código $?"
fi
set -e
wait $OBSERVADOR || true

for c in worker api; do log "$c reinicios=$(docker inspect -f '{{.RestartCount}}' vigia-backend-$c)"; done
python loadtest/observar.py --resumen "$CORRIDA" > "$DIR/resumen.txt"
if [ "$MODO" = rafaga ]; then
  # La ráfaga deja al worker sin heap (ver README, "rafaga").
  docker restart vigia-backend-worker > /dev/null
  salud_worker
  log "worker reiniciado"
else
  # A 25.000 una pasada de celo más una de salud dura más que la corrida: los que falten quedan en "Problemas".
  $PSQL -q -v inicio="$INICIO" -v sensores=50 -v celo=1 -v salud=1 -v aislamiento=1 < loadtest/latencia.sql \
    > "$DIR/latencia.txt"
fi
log "listo"
