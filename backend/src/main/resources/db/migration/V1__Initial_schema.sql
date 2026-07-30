CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255),
    apellido VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    rol VARCHAR(50),
    activo SMALLINT NOT NULL DEFAULT 1,
    fecha_alta DATE,
    proveedor_auth VARCHAR(255)
);

CREATE TABLE establecimientos (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255),
    latitud DOUBLE PRECISION,
    longitud DOUBLE PRECISION,
    activo SMALLINT NOT NULL DEFAULT 1,
    fecha_alta DATE
);

CREATE TABLE empleado (
    id BIGSERIAL PRIMARY KEY,
    establecimiento_id BIGINT REFERENCES establecimientos(id),
    usuario_id BIGINT REFERENCES usuarios(id),
    activo SMALLINT NOT NULL DEFAULT 1,
    fecha_vinculacion TIMESTAMP NOT NULL,
    fecha_desvinculacion TIMESTAMP
);

CREATE TABLE invitaciones (
    id BIGSERIAL PRIMARY KEY,
    email_destino VARCHAR(255) NOT NULL,
    rol VARCHAR(50) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL,
    fecha_expiracion TIMESTAMP,
    creador_id BIGINT REFERENCES usuarios(id),
    establecimiento_id BIGINT REFERENCES establecimientos(id),
    token VARCHAR(255) NOT NULL,
    tipo_invitacion VARCHAR(50) NOT NULL,
    estado_invitacion VARCHAR(50) NOT NULL
);

CREATE TABLE rodeo_lote (
    id BIGSERIAL PRIMARY KEY,
    establecimiento_id BIGINT REFERENCES establecimientos(id),
    nombre VARCHAR(255),
    activo SMALLINT NOT NULL DEFAULT 1,
    fecha_alta TIMESTAMP
);

CREATE TABLE caravanas (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(255) NOT NULL UNIQUE,
    fecha_asignacion DATE,
    ultima_senial TIMESTAMP,
    fecha_alta DATE,
    activo SMALLINT NOT NULL DEFAULT 1
);

CREATE TABLE animales (
    id BIGSERIAL PRIMARY KEY,
    establecimiento_id BIGINT REFERENCES establecimientos(id),
    rodeo_lote_id BIGINT REFERENCES rodeo_lote(id),
    id_caravana_oficial VARCHAR(255),
    alias VARCHAR(255),
    fecha_nacimiento DATE,
    raza VARCHAR(255),
    estado_actual VARCHAR(50),
    fecha_alta DATE,
    caravana_id BIGINT REFERENCES caravanas(id)
);

CREATE TABLE estados_reproductivos (
    id BIGSERIAL PRIMARY KEY,
    animal_id BIGINT REFERENCES animales(id),
    usuario_registro_id BIGINT REFERENCES usuarios(id),
    estado VARCHAR(50),
    fecha_ultimo_parto DATE,
    fecha_ultima_inseminacion DATE,
    origen VARCHAR(50),
    fecha_registro TIMESTAMP
);

CREATE TABLE alertas (
    id BIGSERIAL PRIMARY KEY,
    animal_id BIGINT REFERENCES animales(id),
    usuario_confirmacion_id BIGINT REFERENCES usuarios(id),
    tipo VARCHAR(255),
    severidad VARCHAR(50),
    ts_generacion TIMESTAMP NOT NULL,
    disparador VARCHAR(50) NOT NULL,
    valor_umbral DOUBLE PRECISION NOT NULL,
    valor_detectado DOUBLE PRECISION,
    estado VARCHAR(50) NOT NULL,
    ts_confirmacion TIMESTAMP,
    motivo_descarte VARCHAR(255),
    ventana_inicio_ins TIMESTAMP,
    ventana_fin_ins TIMESTAMP,
    accion_recomendada VARCHAR(255),
    confianza_modelo DOUBLE PRECISION, -- TODO: REVISAR
    ventana_optima_horas INT -- TODO: REVISAR
);

CREATE TABLE telemetria (
    id BIGSERIAL PRIMARY KEY,
    fecha_hora TIMESTAMP NOT NULL,
    caravana_id BIGINT NOT NULL REFERENCES caravanas(id),
    latitud DOUBLE PRECISION,
    longitud DOUBLE PRECISION,
    accel_x DOUBLE PRECISION,
    accel_y DOUBLE PRECISION,
    accel_z DOUBLE PRECISION,
    nivel_rumia DOUBLE PRECISION,
    bateria DOUBLE PRECISION,
    estado_inferencia VARCHAR(50) DEFAULT 'PENDIENTE'
);

CREATE TABLE intervenciones (
    id BIGSERIAL PRIMARY KEY,
    alerta_id BIGINT REFERENCES alertas(id),
    animal_id BIGINT NOT NULL REFERENCES animales(id),
    interventor_id BIGINT NOT NULL REFERENCES usuarios(id),
    descripcion VARCHAR(1000) NOT NULL,
    fecha_hora TIMESTAMP NOT NULL
);