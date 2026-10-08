const UMBRAL_BATERIA_BAJA = 20;
const UMBRAL_SENIAL_SILENCIOSA_DIAS = 7;

export const ESTADO_SENSOR_LABEL: Record<string, string> = {
  DISPONIBLE: 'LIBRE',
  VINCULADO: 'ASIGNADA',
  INACTIVO: 'BAJA',
};

export function formatBateria(bateria: number | null): { text: string; amber: boolean } {
  if (bateria === null) return { text: '—', amber: false };
  return { text: `${Math.round(bateria)}%`, amber: bateria < UMBRAL_BATERIA_BAJA };
}

export function formatUltimaSenial(iso: string | null): { text: string; amber: boolean; title?: string } {
  if (!iso) return { text: '—', amber: false };
  const fecha = new Date(iso);
  const diffMs = Date.now() - fecha.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  const diffHoras = Math.floor(diffMin / 60);
  const diffDias = Math.floor(diffHoras / 24);

  let text: string;
  if (diffMin < 1) text = 'recién';
  else if (diffMin < 60) text = `hace ${diffMin} min`;
  else if (diffHoras < 24) text = `hace ${diffHoras} h`;
  else text = `hace ${diffDias} día${diffDias === 1 ? '' : 's'}`;

  return { text, amber: diffDias > UMBRAL_SENIAL_SILENCIOSA_DIAS, title: iso };
}

/** Texto de una caravana en un <option>: omite los datos de telemetría que no tiene. */
export function labelCaravanaOpcion(c: {
  alias: string | null;
  codigo: string | null;
  devEui: string;
  bateria: number | null;
  ultimaSenial: string | null;
}): string {
  const nombre = c.alias || c.codigo || c.devEui;
  if (c.bateria === null && !c.ultimaSenial) return `${nombre} · sin transmisiones`;
  const partes = [nombre];
  if (c.bateria !== null) partes.push(`batería ${formatBateria(c.bateria).text}`);
  if (c.ultimaSenial) partes.push(`señal ${formatUltimaSenial(c.ultimaSenial).text}`);
  return partes.join(' · ');
}
