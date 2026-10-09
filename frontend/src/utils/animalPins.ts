import L from 'leaflet';
import type { AnimalMapa } from '../api/animalesApi';

const pinUrls = import.meta.glob('../assets/pins/*.png', { eager: true, query: '?url', import: 'default' }) as Record<string, string>;

// 1x + 2x (Leaflet elige el 2x en pantallas retina). Punta del pin = centro inferior.
const makePin = (estado: string, selected: boolean) => {
  const [w, h] = selected ? [60, 78] : [40, 52];
  const base = `../assets/pins/pin-${estado}${selected ? '-sel' : ''}`;
  return new L.Icon({
    iconUrl: pinUrls[`${base}-1x.png`],
    iconRetinaUrl: pinUrls[`${base}-2x.png`],
    iconSize: [w, h],
    iconAnchor: [w / 2, h],
    popupAnchor: [0, -h],
  });
};

const PIN_ICONS = Object.fromEntries(
  ['normal', 'borde', 'critico', 'sindatos'].map(e => [e, [makePin(e, false), makePin(e, true)]])
) as Record<string, [L.Icon, L.Icon]>;

/** Pin del animal según sus alertas (crítico / borde / normal) o si no tiene datos recientes. */
export function determinarIcono(animal: AnimalMapa, selected = false) {
  const severidades = animal.alertasAnimal.map(a => a.severidad);
  const estado = animal.sinDatosRecientes ? 'sindatos'
    : severidades.includes('ALTA') ? 'critico'
      : severidades.includes('MEDIA') || severidades.includes('BAJA') ? 'borde'
        : 'normal';
  return PIN_ICONS[estado][selected ? 1 : 0];
}
