#!/usr/bin/env bash
# Una corrida de volumen (RNF01 y RNF02, ver README, "Volumen"): un establecimiento con <sensores> animales, <n> en
# celo, <n> con desvío sanitario y uno que se aísla. Desde la raíz del repo, con el stack en modo carga levantado:
#   loadtest/volumen.sh <sensores> <n> [duración] [corrida]   p. ej. loadtest/volumen.sh 200 10 70m
# Deja todo en loadtest/resultados/<corrida>/ (por defecto vol-<sensores>), y borra lo que hubiera ahí.
set -euo pipefail
S=$1
N=$2
DURACION=${3:-70m}
CORRIDA=${4:-vol-$S}
DIR=loadtest/resultados/$CORRIDA
DC="docker compose -f infra/docker-compose.yml -f infra/docker-compose.carga.yml"
PSQL="docker exec -i vigia-db psql -U vigia_user -d vigia"
VARS="-e CLIENTES=1 -e SENSORES_POR_CLIENTE=$S -e CELO=$N -e SALUD=$N -e AISLAMIENTO=1 -e PENSAR_S=5"
ESCENARIOS=sensores,clientes_navegacion
rm -rf "$DIR"
mkdir -p "$DIR"
export PYTHONIOENCODING=utf-8
log() { echo "$CORRIDA: $* $(date +%H:%M:%S)" | tee -a "$DIR/bitacora.txt"; }

# Con el worker parado: si no, sus jobs pueden chocar con el borrado (ver README, "Limpiar").
docker stop vigia-backend-worker > /dev/null
$PSQL < loadtest/limpiar.sql > /dev/null
cat loadtest/trayectoria.sql loadtest/seed.sql | $PSQL -q -v perfil="$(cat loadtest/perfil.json)" \
  -v clientes=1 -v sensores="$S" -v pendientes_h=0 -v celo="$N" -v salud="$N" -v aislamiento=1 | tee "$DIR/semilla.txt"
INICIO=$($PSQL -qtA -c "SELECT (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::timestamp(0)")
log "semilla lista (inicio $INICIO)"

# Celo, salud y geo corren al arrancar el worker y después cada 30 min (fixedRate).
docker start vigia-backend-worker > /dev/null
until curl -sf localhost:8081/actuator/health > /dev/null; do sleep 5; done
log "worker listo"

# 2 min de calentamiento, sin medir. Con ESCALONAR tan largo, los animales con anomalía siguen callados.
$DC run --rm k6 run -q $VARS -e ESCENARIOS=$ESCENARIOS -e DURACION=2m -e ESCALONAR=1000h carga.js > /dev/null || true
log "calentamiento listo"

MINUTOS=${DURACION%m}
python loadtest/observar.py "$CORRIDA" --minutos "$((MINUTOS + 1))" > /dev/null &
OBSERVADOR=$!
set +e
$DC run --rm k6 run -q $VARS -e ESCENARIOS=$ESCENARIOS -e DURACION="$DURACION" -e ESCALONAR=30m \
  --out csv="resultados/$CORRIDA/k6.csv.gz" carga.js > "$DIR/k6-resumen.txt" 2>&1
log "k6 terminó con código $?"
set -e
wait $OBSERVADOR || true

for c in worker api; do log "$c reinicios=$(docker inspect -f '{{.RestartCount}}' vigia-backend-$c)"; done
python loadtest/observar.py --resumen "$CORRIDA" > "$DIR/resumen.txt"
$PSQL -q -v inicio="$INICIO" -v sensores="$S" -v celo="$N" -v salud="$N" -v aislamiento=1 \
  < loadtest/latencia.sql > "$DIR/latencia.txt"
log "listo"
