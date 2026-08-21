# VigIA - Simulador de IoT Ganadero

Script en Python que emula el comportamiento de cientos de collares IoT transmitiendo datos de telemetría en tiempo real hacia el backend. 

## Funcionalidades
- **Random Walk:** Simula el pastoreo normal de los animales en el campo.
- **Inyección de Anomalías:** Genera automáticamente historial anómalo para probar la detección de celo y de desvíos sanitarios (salud):
  - **Celo** (`VIGIA-ARG-001` por defecto): alta actividad sostenida y errática, alineada con el modelo poblacional de celo.
  - **Salud** (`VIGIA-ARG-002` por defecto): inyecta 8 días de historial (7+ de calibración "sana" + 1 día de cuadro de enfermedad con inmovilidad), y sigue mandando ese patrón de inmovilidad en el loop en vivo. Ver el comentario grande en `simulador.py` sobre por qué esto **requiere** que `fecha_alta` del animal ya tenga >= `vigia.salud.calibracion-dias` (7 por defecto) de antigüedad real — si no, el backend bloquea toda predicción de salud por RF03 sin importar la telemetría enviada. `VIGIA-ARG-002` viene backdateada en `DataInitializer.java` justamente para poder demostrar esto el mismo día.
- **Consumo de Batería:** Emula el desgaste progresivo de la batería de los collares.

## Configuración

Las variables de entorno principales son:
- `API_URL`: URL del webhook del backend (por defecto apunta al contenedor del `backend-worker`).
- `NUM_ANIMALES`: Cantidad de collares a simular (default 200).
- `INTERVALO_SEGUNDOS`: Frecuencia de envío de datos. Para facilitar las pruebas sin esperar horas, por defecto está seteado en **30 segundos** (tanto para el ciclo continuo como para la separación del historial).
- `FORZAR_CELO_DEVICE_ID`: device_id que siempre se simula en celo (default `VIGIA-ARG-001`).
- `FORZAR_SALUD_DEVICE_ID`: device_id que siempre se simula con un desvío sanitario (default `VIGIA-ARG-002`).

## Ejecución

El simulador se ejecuta localmente usando un entorno virtual de Python:

```bash
# 1. Crear el entorno virtual
py -m venv .venv

# 2. Activar el entorno virtual (PowerShell)
.\.venv\Scripts\activate
# En Linux/Mac: source .venv/bin/activate

# 3. Instalar dependencias
pip install -r requirements.txt

# 4. Ejecutar el simulador
py simulador.py
```
