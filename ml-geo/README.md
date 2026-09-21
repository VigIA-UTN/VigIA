# Microservicio de Análisis Geoespacial (ml-geo)

Este microservicio se encarga de procesar el historial de coordenadas GPS del rodeo para detectar anomalías de posicionamiento y generar mapas de calor de densidad.

## Funcionalidades Principales

- **Mapa de Calor:** Discretiza el área cubierta por las coordenadas en una grilla dinámica y calcula la intensidad de uso basada en la frecuencia de los puntos GPS.
- **Índice de Cohesión:** Calcula la distancia relativa (en metros, usando la fórmula de Haversine) de cada animal con respecto al centroide del resto del rodeo.
- **Alerta de Aislamiento:** Dispara una alerta si la distancia relativa de un animal supera un umbral de aislamiento establecido.

## Notas Técnicas y Configuraciones Pendientes (TODO)

- **Umbral de Aislamiento:** Actualmente, el límite para considerar a un animal aislado (`UMBRAL_AISLAMIENTO_METROS = 100.0`) está **hardcodeado** en el código (`app/main.py`). 
Queda pendiente definir a nivel producto cómo se manejará esta configuración:
  - ¿Se configurará mediante variables de entorno del microservicio?
  - ¿Lo configurará el administrador globalmente desde la app?
  - ¿Será un parámetro ajustable por cada productor/campo de forma independiente?
