# VigIA

## Link al Drive

https://drive.google.com/drive/folders/1OaNcqx1iyuzeKOyNrNP1Tt-S7YMCXce3

---

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
- **Simulador y Modelos ML:** Corriendo en background e inyectando/analizando datos automáticamente cada 30 segundos.

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

### 4. Levantar Simulador y Entorno de ML (Para Pruebas)

Para probar todo el flujo de alertas, podés prender los módulos faltantes:

```bash
cd infra
docker-compose up -d ml-celo ml-salud ml-geo simulador
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
