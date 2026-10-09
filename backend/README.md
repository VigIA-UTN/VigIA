# VigIA - Backend

Este directorio contiene el código fuente del backend de la plataforma VigIA, estructurado como un proyecto Multi-Módulo en Spring Boot.

## Arquitectura

El backend está dividido en tres módulos principales para separar las responsabilidades de atención a clientes HTTP y procesamiento asíncrono/batch:

1. **`backend-core-lib`**: Contiene las entidades JPA, repositorios, DTOs y lógica de negocio compartida.
2. **`backend-api`**: Expone los endpoints RESTful utilizados por el Frontend (consultas de rodeos, alertas, estado de caravanas, etc.).
3. **`backend-worker`**: Se encarga del procesamiento de colas (ej. RabbitMQ/Kafka si aplica) y de ejecutar el **Spring Batch**. Este Batch recolecta la telemetría periódicamente y la envía a los microservicios de ML para inferir alertas.

### Comunicación con los microservicios de ML

Hay dos caminos distintos hacia los microservicios, según quién dispara el análisis:

- **Batch, desde `backend-worker`** — el Spring Batch junta la telemetría pendiente y la empuja a los microservicios para que generen alertas. Implementado para **Celo** (`CeloDetectorStrategyImpl`), **Salud** (`SaludDetectorStrategyImpl`) y **Geo** (`GeoDetectorStrategyImpl`, para cohesión/aislamiento y alertas de `ABIGEATO`).
- **On-demand, desde `backend-api`** — el usuario pide un análisis y el backend lo resuelve en el momento. Es el caso de **Geo**: `GET /api/v1/mapa-calor` → `MapaCalorServiceImpl` → `MlGeoClient` → `ml-geo`, para el mapa de calor de pastoreo (H0025).

## Tecnologías Principales
- **Java 17**
- **Spring Boot 3.x** (Web, Data JPA, Batch)
- **PostgreSQL**
- **Resilience4j** (Circuit Breaker para la comunicación con ML)

## Desarrollo Local

Para compilar el proyecto:
```bash
mvn clean install
```

Podés levantar los módulos independientemente utilizando tu IDE, o a través de Docker Compose en la carpeta `/infra`.

### Configuración del Batch Scheduler
El `BatchScheduler` dentro de `backend-worker` está configurado para ejecutarse cada 30 segundos (para pruebas). En producción, este valor debería ser ajustado (ej. 1 hora) dependiendo de la frecuencia de envío real de las caravanas.
