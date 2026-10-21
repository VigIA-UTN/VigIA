# VigIA — Guía de desarrollo y despliegue

Cómo levantar VigIA en local, cómo funciona el CI y cómo se despliega en Railway. Para una visión general del proyecto, ver el [README principal](../README.md).

## 🚀 Inicio Rápido (Levantar todo con Docker)

Si querés probar la plataforma completa de una sola vez (Base de datos, Backend, Modelos de ML y el Simulador de Telemetría), podés levantar toda la infraestructura utilizando Docker Compose:

1. Abrí una terminal en la carpeta `/infra`.

2. Ejecutá el comando para construir y levantar todos los servicios en segundo plano:

   ```bash
   docker-compose up -d --build
   ```

3. Para el Frontend, abrí otra terminal en `/frontend` y ejecutá:

   ```bash
   npm install && npm run dev
   ```

Con esto, tendrás corriendo:
- **Backend (APIs):** `http://localhost:8080`
- **Maildev (Mails simulados):** `http://localhost:8025`
- **Frontend:** `http://localhost:5173`
- **Base de Datos (PostgreSQL):** Puerto `5432`
- **Modelos ML:** Corriendo en background y analizando datos automáticamente cada 30 segundos.

Para apagar todo cuando termines:

```bash
cd infra
docker-compose down -v
```

---

## 🛠️ Paso a Paso (Para Desarrollo Local)

Si vas a programar y necesitás levantar servicios específicos para aprovechar tu IDE (autocompletado, debug, live-reload), seguí este paso a paso:

### 1. Levantar Infraestructura (Base de Datos y Mail)

Siempre que vayas a trabajar en el backend, necesitás la base de datos y el servidor de correos encendidos:

```bash
cd infra
docker-compose up -d db mail
```

### 2. Levantar el Backend (Spring Boot Multi-Módulo)

El backend de VigIA está dividido en módulos: `backend-api` (para las APIs REST del frontend), `backend-worker` (Batch jobs y colas asíncronas) y `backend-core-lib` (librerías compartidas).

- **Opción Recomendada (IDE):** Abrí la carpeta `/backend` en tu IDE (IntelliJ, Eclipse). Podés darle 'Run' tanto a la clase principal de `backend-api` como a la de `backend-worker` al mismo tiempo.
- **Opción Alternativa:** Si solo vas a tocar frontend y no querés usar el IDE para Java, podés levantar el backend por Docker como se explica en la sección de *Inicio Rápido*.

### 3. Levantar el Frontend

Si vas a trabajar en la interfaz visual:

```bash
cd frontend
npm install
npm run dev
```

### 4. Levantar Entorno de ML y Simulador (Para Pruebas)

Para probar todo el flujo de alertas, primero prendé los modelos de ML mediante Docker:

```bash
cd infra
docker-compose up -d ml-celo ml-salud ml-geo
```

Y luego, levantá el **Simulador** localmente desde tu terminal (necesita Python):

```bash
cd simulador
py -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
py simulador.py
```

**Nota sobre el tiempo:** Para agilizar las pruebas locales, la frecuencia de simulación y del Batch Scheduler del backend (`backend-worker`) están configuradas a **30 segundos** (en lugar de los típicos 5 a 60 minutos de la vida real). Esto permite generar inferencias rápidamente sin tener que esperar horas.

---

## ⚙️ Extras: CI/CD, Pruebas y Resiliencia

### Integración Continua

VigIA cuenta con un pipeline de CI en GitHub Actions (`.github/workflows/ci.yml`). 
- **Frontend y Backend:** Se valida la compilación y el linting.
- **Microservicios ML:** Cuentan con suites de pruebas unitarias y de integración (ej: `ml-celo/tests`) utilizando `pytest`, donde se valida el correcto funcionamiento de las inferencias (utilizando mocks de TensorFlow) y se aseguran los contratos HTTP.

### Resiliencia (Circuit Breaker)

No es estrictamente obligatorio levantar los contenedores ML para correr el backend. Si el microservicio de Celo está apagado o tarda en responder, el patrón Circuit Breaker (Resilience4j) en el `backend-worker` capturará el error de forma segura, marcará la telemetría como `"INFERENCIA_PENDIENTE"` en base de datos, y permitirá que la aplicación siga funcionando sin perder datos.

---

## ☁️ Despliegue en Producción (Railway)

Para desplegar VigIA en un entorno de producción como Railway, es necesario configurar correctamente las **Variables de Entorno** de cada servicio. Las credenciales de producción **nunca deben commitearse** en el repositorio (utilice el panel de variables de Railway).

Puede usar el archivo `infra/.env.example` como referencia para conocer todas las variables requeridas.

### Variables por Servicio en Railway:

#### 1. Base de Datos (PostgreSQL / TimescaleDB)
Si utiliza el plugin de PostgreSQL de Railway, este proveerá sus propias credenciales. Asegúrese de que el Backend consuma estos valores:
- `DB_HOST`: Host interno provisto por Railway (ej. `postgres.railway.internal`).
- `DB_PORT`: Puerto de la DB (usualmente `5432`).
- `DB_NAME`: Nombre de la base de datos.
- `DB_USER`: Usuario.
- `DB_PASSWORD`: Contraseña.

#### 2. Message Broker (RabbitMQ)
- `SPRING_RABBITMQ_HOST`: Host interno del servicio RabbitMQ desplegado en Railway (ej. `rabbitmq.railway.internal`).
- `SPRING_RABBITMQ_PORT`: `5672`
- `SPRING_RABBITMQ_USERNAME`: Usuario configurado en RabbitMQ.
- `SPRING_RABBITMQ_PASSWORD`: Contraseña de RabbitMQ.

#### 3. Backend (API y Worker)
- `SPRING_PROFILES_ACTIVE`: **producción → dejar sin setear o `prod`.** El default de la app ya es `prod`, así que un redeploy nunca ejecuta el `DataInitializer` (que limpia y re-siembra la base). **Solo el entorno de `develop` debe setear `SPRING_PROFILES_ACTIVE=dev`** para volver a tener datos de prueba en cada deploy.
- `CORS_ORIGINS`: URLs de su frontend en producción (ej. `https://vigia-frontend.up.railway.app`).
- `TZ`: `America/Argentina/Buenos_Aires`

> **Datos iniciales de producción:** la base de prod se puebla a mano con `infra/prod/seed_prod.sql` (y se limpia con `infra/prod/wipe_prod.sql`). Ver `infra/prod/README.md`.

**Microservicios de Machine Learning (Usados por el Backend Worker):**
Asegúrese de configurar las URLs apuntando a la red interna de Railway.
- `ML_CELO_URL`: URL interna del servicio (ej. `http://ml-celo.railway.internal:8001`)
- `ML_SALUD_URL`: URL interna del servicio (ej. `http://ml-salud.railway.internal:8002`)
- `ML_GEO_URL`: URL interna del servicio (ej. `http://ml-geo.railway.internal:8003`)

#### 4. Servidor de Correos (SMTP)
Para el envío de alertas reales en producción, configure un proveedor SMTP (SendGrid, AWS SES, Resend, Gmail, etc.):
- `MAIL_HOST`: Host del proveedor (ej. `smtp.sendgrid.net`).
- `MAIL_PORT`: Puerto SMTP (ej. `587`).
- `MAIL_USER`: Usuario SMTP.
- `MAIL_PASSWORD`: Contraseña o API Key SMTP.
