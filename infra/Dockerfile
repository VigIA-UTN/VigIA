# Postgres oficial (Debian) + PostGIS + TimescaleDB, para correr como
# servicio simple de un solo nodo en Railway (sin Patroni/HA).
FROM postgis/postgis:16-3.4

# bullseye-security esta rota (paquetes 404 tras el fin de soporte de
# Debian 11) y no la necesitamos para instalar curl/gnupg/lsb-release:
# la sacamos de la lista de fuentes antes de actualizar.
RUN (sed -i '/bullseye-security/d' /etc/apt/sources.list 2>/dev/null || true) \
    && (sed -i '/bullseye-security/d' /etc/apt/sources.list.d/*.list 2>/dev/null || true) \
    && (find /etc/apt/sources.list.d -name '*security*' -delete 2>/dev/null || true) \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl gnupg ca-certificates lsb-release \
    && curl -fsSL https://packagecloud.io/timescale/timescaledb/gpgkey \
       | gpg --dearmor -o /usr/share/keyrings/timescaledb-archive-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/timescaledb-archive-keyring.gpg] https://packagecloud.io/timescale/timescaledb/debian/ $(lsb_release -cs) main" \
       > /etc/apt/sources.list.d/timescaledb.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends timescaledb-2-postgresql-16 \
    && apt-get purge -y --auto-remove curl gnupg lsb-release \
    && rm -rf /var/lib/apt/lists/*

# Precarga la librería de TimescaleDB (se aplica en el primer initdb, vía
# el template que usa la imagen oficial de Postgres).
RUN echo "shared_preload_libraries = 'timescaledb'" >> /usr/share/postgresql/16/postgresql.conf.sample

# Se ejecuta una sola vez, en el primer arranque con el data dir vacío.
COPY init.sql /docker-entrypoint-initdb.d/01-extensions.sql
