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

CREATE TABLE animales (
    id BIGSERIAL PRIMARY KEY,
    establecimiento_id BIGINT REFERENCES establecimientos(id),
    rodeo_lote_id BIGINT REFERENCES rodeo_lote(id),
    id_caravana_oficial VARCHAR(255),
    alias VARCHAR(255),
    fecha_nacimiento TIMESTAMP,
    raza VARCHAR(255),
    estado_actual VARCHAR(50),
    fecha_alta TIMESTAMP,
);
