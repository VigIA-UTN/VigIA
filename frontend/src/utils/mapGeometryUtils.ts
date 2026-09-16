import type { CoordenadaDTO } from '../api/perimetrosApi';

/**
 * Calcula la distancia aproximada al cuadrado de un punto P a un segmento AB.
 */
function distToSegmentSquared(
  p: { lat: number; lng: number },
  v: { lat: number; lng: number },
  w: { lat: number; lng: number }
): number {
  const l2 = (v.lat - w.lat) ** 2 + (v.lng - w.lng) ** 2;
  if (l2 === 0) return (p.lat - v.lat) ** 2 + (p.lng - v.lng) ** 2;
  let t = ((p.lat - v.lat) * (w.lat - v.lat) + (p.lng - v.lng) * (w.lng - v.lng)) / l2;
  t = Math.max(0, Math.min(1, t));
  return (p.lat - (v.lat + t * (w.lat - v.lat))) ** 2 + (p.lng - (v.lng + t * (w.lng - v.lng))) ** 2;
}

/**
 * Inserta un nuevo punto en la arista más cercana del polígono para no desarmar su forma.
 */
export function insertPointInClosestEdge(
  coords: CoordenadaDTO[],
  newPoint: { lat: number; lng: number }
): CoordenadaDTO[] {
  if (coords.length < 3) {
    return [...coords, { latitud: newPoint.lat, longitud: newPoint.lng }];
  }

  let minDistance = Infinity;
  let bestIndex = coords.length; // Por defecto al final

  for (let i = 0; i < coords.length; i++) {
    const nextIdx = (i + 1) % coords.length;
    const v = { lat: coords[i].latitud, lng: coords[i].longitud };
    const w = { lat: coords[nextIdx].latitud, lng: coords[nextIdx].longitud };
    const d2 = distToSegmentSquared(newPoint, v, w);

    if (d2 < minDistance) {
      minDistance = d2;
      bestIndex = nextIdx;
    }
  }

  const result = [...coords];
  result.splice(bestIndex, 0, { latitud: newPoint.lat, longitud: newPoint.lng });
  return result;
}
