# VigIA - Simulador de IoT Ganadero

Script en Python que emula el comportamiento de cientos de collares IoT transmitiendo datos de telemetría en tiempo real hacia el backend. 

## Funcionalidades
- **Random Walk:** Simula el pastoreo normal de los animales en el campo.
- **Inyección de Anomalías:** Genera automáticamente historial anómalo (ej. un animal que repentinamente aumenta su actividad) para probar la detección de celo u otros modelos.
- **Consumo de Batería:** Emula el desgaste progresivo de la batería de los collares.

## Configuración

Las variables de entorno principales son:
- `API_URL`: URL del webhook del backend (por defecto apunta al contenedor del `backend-worker`).
- `NUM_ANIMALES`: Cantidad de collares a simular (default 200).
- `INTERVALO_SEGUNDOS`: Frecuencia de envío de datos. Para facilitar las pruebas sin esperar horas, por defecto está seteado en **30 segundos** (tanto para el ciclo continuo como para la separación del historial).

## Ejecución

Lo ideal es correrlo a través de Docker, junto con el resto de la infraestructura:
```bash
cd ../infra
docker-compose up -d simulador
```
