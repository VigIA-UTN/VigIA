"""Observador de la prueba de carga (ver README). Solo biblioteca estándar; usa el CLI de docker.

    python loadtest/observar.py <corrida> [--minutos N]   muestrea hasta Ctrl-C (o N minutos) y escribe
                                                         loadtest/resultados/<corrida>/muestras.csv
    python loadtest/observar.py --resumen <corrida>      máximo y p95 de cada columna y, si está
                                                         resultados/<corrida>/k6.csv.gz, el webhook minuto a minuto

Cada 15 s: pools de Hikari de la API y el worker, conexiones a Postgres por estado, CPU y memoria por contenedor y la
última corrida de celo, salud y geo. Cada 60 s: filas de las últimas 7 h todavía pendientes para celo y salud (cubre
las 6 h pendientes de la semilla), con lo que tarda esa consulta, para ver que el observador no cargue la base.
"""
import csv
import datetime as dt
import gzip
import json
import math
import subprocess
import sys
import time
import urllib.request
from collections import defaultdict
from pathlib import Path

RESULTADOS = Path(__file__).parent / 'resultados'
CADA_S = 15
BACKLOG_CADA_S = 60
SERVICIOS = {'api': 'http://localhost:8080', 'worker': 'http://localhost:8081'}
CONTENEDORES = ['db', 'backend-api', 'backend-worker', 'ml-celo', 'ml-salud', 'ml-geo', 'rabbitmq', 'k6']
JOBS = ['celoJob', 'saludJob', 'geoJob']
# Los timestamps de la base son hora local (como LocalDateTime.now() en la API y el worker); la sesión de psql, UTC.
AHORA_LOCAL = "now() AT TIME ZONE 'America/Argentina/Buenos_Aires'"

COLUMNAS = (['hora']
            + [f'{s}_hikari_{m}' for s in SERVICIOS for m in ('activas', 'pendientes', 'timeouts')]
            + ['pg_active', 'pg_idle', 'pg_idle_in_transaction']
            + [f'{c}_{m}' for c in CONTENEDORES for m in ('cpu_pct', 'mem_mb')]
            + [f'{j}_{m}' for j in JOBS for m in ('ultima_dur_s', 'en_curso_s', 'entre_inicios_s')]
            + ['pend_celo', 'pend_salud', 'backlog_ms'])


def psql(sql):
    salida = subprocess.run(['docker', 'exec', 'vigia-db', 'psql', '-U', 'vigia_user', '-d', 'vigia', '-tA', '-F',
                             '|', '-c', sql], capture_output=True, text=True, check=True).stdout
    return [linea.split('|') for linea in salida.splitlines() if linea]


def hikari(base, metrica):
    with urllib.request.urlopen(f'{base}/actuator/metrics/hikaricp.connections.{metrica}', timeout=5) as r:
        return int(json.load(r)['measurements'][0]['value'])


def a_mb(texto):  # "512.3MiB" / "1.2GiB" / "800kB"
    for sufijo, factor in (('GiB', 1024), ('MiB', 1), ('KiB', 1 / 1024), ('GB', 1000), ('MB', 1), ('kB', 1 / 1000),
                           ('B', 1 / 1024 / 1024)):
        if texto.endswith(sufijo):
            return round(float(texto[:-len(sufijo)]) * factor, 1)
    return ''


def muestra(con_backlog):
    fila = {'hora': dt.datetime.now().strftime('%H:%M:%S'), 'pg_active': 0, 'pg_idle': 0, 'pg_idle_in_transaction': 0}
    for s, base in SERVICIOS.items():
        for m, metrica in (('activas', 'active'), ('pendientes', 'pending'), ('timeouts', 'timeout')):
            try:
                fila[f'{s}_hikari_{m}'] = hikari(base, metrica)
            except Exception:
                fila[f'{s}_hikari_{m}'] = ''  # caído o sin métricas expuestas (ver README: stack en modo carga)
    for estado, n in psql("SELECT state, count(*) FROM pg_stat_activity WHERE datname = 'vigia' "
                          "AND backend_type = 'client backend' AND pid <> pg_backend_pid() GROUP BY state"):
        clave = 'pg_' + estado.replace(' ', '_')
        if clave in COLUMNAS:
            fila[clave] = int(n)
    stats = subprocess.run(['docker', 'stats', '--no-stream', '--format', '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}'],
                           capture_output=True, text=True).stdout
    for linea in stats.splitlines():
        nombre, cpu, mem = linea.split('|')
        # vigia-<servicio>, o infra-k6-run-<id> para el k6 de "docker compose run"
        c = 'k6' if nombre.startswith('infra-k6') else nombre.removeprefix('vigia-')
        if c in CONTENEDORES:
            fila[f'{c}_cpu_pct'] = float(cpu.rstrip('%'))
            fila[f'{c}_mem_mb'] = a_mb(mem.split('/')[0].strip())
    corridas = defaultdict(list)
    for job, inicio, fin, ahora in psql(
            f"SELECT job_name, start_time, end_time, {AHORA_LOCAL} FROM ("
            "  SELECT i.job_name, e.start_time, e.end_time, row_number() OVER (PARTITION BY i.job_name "
            "    ORDER BY e.job_execution_id DESC) AS n "
            "  FROM batch_job_execution e JOIN batch_job_instance i USING (job_instance_id) "
            f"  WHERE e.start_time > {AHORA_LOCAL} - interval '7 hours') x WHERE n <= 3 ORDER BY job_name, n"):
        corridas[job].append((dt.datetime.fromisoformat(inicio), dt.datetime.fromisoformat(fin) if fin else None,
                              dt.datetime.fromisoformat(ahora)))
    for job, lista in corridas.items():
        if job not in JOBS:
            continue
        terminada = next((c for c in lista if c[1]), None)
        if terminada:
            fila[f'{job}_ultima_dur_s'] = round((terminada[1] - terminada[0]).total_seconds(), 1)
        if lista[0][1] is None:
            fila[f'{job}_en_curso_s'] = round((lista[0][2] - lista[0][0]).total_seconds(), 1)
        if len(lista) > 1:
            fila[f'{job}_entre_inicios_s'] = round((lista[0][0] - lista[1][0]).total_seconds(), 1)
    if con_backlog:
        t0 = time.perf_counter()
        (celo, salud), = psql(
            "SELECT count(*) FILTER (WHERE estado_inferencia_celo = 'PENDIENTE'), "
            "count(*) FILTER (WHERE estado_inferencia_salud = 'PENDIENTE') FROM telemetria "
            f"WHERE fecha_hora > {AHORA_LOCAL} - interval '7 hours'")
        fila.update(pend_celo=int(celo), pend_salud=int(salud),
                    backlog_ms=round((time.perf_counter() - t0) * 1000))  # incluye el docker exec (~100 ms)
    return fila


def observar(corrida, minutos):
    destino = RESULTADOS / corrida
    destino.mkdir(parents=True, exist_ok=True)
    fin = time.time() + minutos * 60 if minutos else math.inf
    ultimo_backlog = 0.0
    with open(destino / 'muestras.csv', 'a', newline='', encoding='utf-8') as f:
        escritor = csv.DictWriter(f, COLUMNAS)
        if f.tell() == 0:
            escritor.writeheader()
        print(f'Muestreando en {destino / "muestras.csv"} (Ctrl-C para terminar)')
        try:
            while time.time() < fin:
                inicio = time.time()
                con_backlog = inicio - ultimo_backlog >= BACKLOG_CADA_S
                if con_backlog:
                    ultimo_backlog = inicio
                try:
                    fila = muestra(con_backlog)
                except Exception as e:  # una muestra fallida no corta la corrida
                    print(f'{dt.datetime.now():%H:%M:%S} muestra fallida: {e}', file=sys.stderr)
                    fila = {'hora': dt.datetime.now().strftime('%H:%M:%S')}
                escritor.writerow(fila)
                f.flush()
                time.sleep(max(0.0, CADA_S - (time.time() - inicio)))
        except KeyboardInterrupt:
            pass


def p95(valores):
    valores = sorted(valores)
    return valores[min(len(valores) - 1, math.ceil(0.95 * len(valores)) - 1)]


def resumir(corrida):
    carpeta = RESULTADOS / corrida
    with open(carpeta / 'muestras.csv', encoding='utf-8') as f:
        filas = list(csv.DictReader(f))
    print(f'{len(filas)} muestras ({filas[0]["hora"]} a {filas[-1]["hora"]})\n')
    print(f'{"columna":34} {"máximo":>10} {"p95":>10}')
    for col in COLUMNAS[1:]:
        valores = [float(x[col]) for x in filas if x.get(col)]
        if valores:
            print(f'{col:34} {max(valores):>10g} {p95(valores):>10g}')
    k6 = carpeta / 'k6.csv.gz'
    if k6.exists():
        print('\nWebhook minuto a minuto (vara: errores < 0,1 %, p99 < 1 s, máximo < 5 s)')
        resumir_k6(k6)


def resumir_k6(ruta):
    """Por minuto, los uplinks (name=uplink) del CSV crudo de k6: tasa, p99, máximo y errores."""
    duraciones, fallidos = defaultdict(list), defaultdict(int)
    with gzip.open(ruta, 'rt', encoding='utf-8') as f:
        for r in csv.DictReader(f):
            if r['name'] != 'uplink':
                continue
            minuto = int(float(r['timestamp'])) // 60
            if r['metric_name'] == 'http_req_duration':
                duraciones[minuto].append(float(r['metric_value']))
            elif r['metric_name'] == 'http_req_failed' and float(r['metric_value']) == 1:  # k6 escribe 1.000000
                fallidos[minuto] += 1
    ultimo_ok, cumplen = None, 0
    print(f'{"minuto":8} {"req/s":>8} {"p99 ms":>9} {"máx ms":>9} {"errores":>8}  cumple')
    for i, minuto in enumerate(sorted(duraciones)):
        d = sorted(duraciones[minuto])
        p99 = d[min(len(d) - 1, math.ceil(0.99 * len(d)) - 1)]
        errores = fallidos[minuto] / len(d)
        cumple = errores < 0.001 and p99 < 1000 and d[-1] < 5000
        if cumple:
            ultimo_ok, cumplen = (i, len(d) / 60), cumplen + 1
        hora = dt.datetime.fromtimestamp(minuto * 60).strftime('%H:%M')
        print(f'{hora:8} {len(d) / 60:>8.1f} {p99:>9.0f} {d[-1]:>9.0f} {errores:>7.2%}  {"sí" if cumple else "no"}')
    print(f'\nCumplen la vara {cumplen} de {len(duraciones)} minutos (el primero y el último suelen estar incompletos).')
    if ultimo_ok:
        print(f'En una ráfaga, el límite: el último que cumple es el {ultimo_ok[0] + 1}.º, a {ultimo_ok[1]:.0f} uplinks/s')


if __name__ == '__main__':
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    if args[0] == '--resumen':
        resumir(args[1])
    else:
        observar(args[0], float(args[args.index('--minutos') + 1]) if '--minutos' in args else None)
