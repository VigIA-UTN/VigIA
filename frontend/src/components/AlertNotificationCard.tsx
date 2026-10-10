import { esAislamiento, esAnestro, textoAccionRecomendada, type AlertaResponse } from '../api/alertaApi';
import { formatMinutos } from '../utils/fechas';
import SidebarCard, { type Categoria, type Severidad } from './SidebarCard';
import DatoDesactualizadoBadge from './DatoDesactualizadoBadge';

interface AlertNotificationCardProps {
  alerta: AlertaResponse;
  onClick: (alerta: AlertaResponse) => void;
  isMenuOpen?: boolean;
  isUnread?: boolean;
}

const ESTADO_ANIMAL: Record<Severidad, string> = { BAJA: 'NORMAL', MEDIA: 'BORDE', ALTA: 'CRITICO' };
const ICONO: Record<string, string> = {
  CELO: 'bi-arrow-through-heart',
  SALUD: 'bi-heart-pulse',
  GEOESPACIAL: 'bi-globe-americas',
  ABIGEATO: 'bi-globe-americas',
  AISLAMIENTO_SOCIAL: 'bi-people-fill',
};
// Colores alineados con las tarjetas de resumen de MapPage (Reproductivo/Sanitario/Geoespacial).
const CATEGORIA: Record<string, Categoria> = {
  CELO: { label: 'Reproductivo', color: '#0d6efd', icono: 'bi-arrow-through-heart' },
  SALUD: { label: 'Sanitario', color: '#198754', icono: 'bi-heart-pulse' },
  GEOESPACIAL: { label: 'Geoespacial', color: '#dc3545', icono: 'bi-globe-americas' },
  ABIGEATO: { label: 'Geoespacial', color: '#dc3545', icono: 'bi-globe-americas' },
  AISLAMIENTO_SOCIAL: { label: 'Geoespacial', color: '#dc3545', icono: 'bi-globe-americas' },
};

function haceCuanto(ts: string) {
  const min = Math.floor((Date.now() - new Date(ts).getTime()) / 60000);
  if (isNaN(min)) return '';
  if (min < 1) return 'Ahora';
  if (min < 60) return `Hace ${min} m`;
  if (min < 1440) return `Hace ${Math.floor(min / 60)} h`;
  return `Hace ${Math.floor(min / 1440)} d`;
}

const minutosDesde = (ts: string) => Math.max(0, Math.floor((Date.now() - new Date(ts).getTime()) / 60000)) || 0;

function tiempoFueraBadge(ts: string) {
  const min = Math.floor((Date.now() - new Date(ts).getTime()) / 60000);
  if (isNaN(min) || min < 0) return 'Fuera ahora';
  if (min < 1) return 'Fuera hace menos de 1 min';
  if (min < 60) return `Fuera hace ${min} min`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  if (m === 0) return `Fuera hace ${h}h`;
  return `Fuera hace ${h}h ${m}m`;
}

export default function AlertNotificationCard({
  alerta,
  onClick,
  isMenuOpen = false,
  isUnread = false,
}: AlertNotificationCardProps) {
  const resuelta = alerta.estado === 'CONFIRMADA' || alerta.estado === 'DESCARTADA';
  // Severidad: el backend manda ALTA/MEDIA/BAJA. Fallback por tipo mientras no llegue.
  const severidad: Severidad = alerta.severidad ?? (alerta.tipo === 'CELO' ? 'MEDIA' : 'ALTA');
  const anestro = esAnestro(alerta);
  // El mapper de AnimalServiceImpl manda el tipo crudo; el anestro se estiliza como reproductivo igual.
  const clave = anestro ? 'CELO' : alerta.tipo;
  const tipoNorm = (alerta.tipo ?? '').toUpperCase();
  const isGeoespacial = tipoNorm.includes('FUERA') || tipoNorm.includes('PERIMETRO') || tipoNorm === 'GEOESPACIAL';
  const aislamiento = esAislamiento(alerta);
  const aislados = alerta.animalesAislados ?? [];

  let titulo = anestro ? `#${alerta.animalId} en anestro` : `#${alerta.animalId} en estado ${ESTADO_ANIMAL[severidad]}`;
  if (aislamiento) {
    titulo = aislados.length > 1 ? `${aislados.length} animales aislados del rodeo` : `#${aislados[0]?.identificador ?? alerta.animalId} aislado del rodeo`;
  }
  const minutosAislado = aislados.length
    ? Math.max(...aislados.map(a => a.minutosFuera))
    : minutosDesde(alerta.tsGeneracion);

  const cuerpoPrincipal = aislamiento ? (
    // Reserva las 3 líneas que ocupa la acción recomendada en las demás cards, para igualar la altura.
    <div style={{ minHeight: 'calc(3 * 1.35em)' }}>
      Fuera del grupo hace {formatMinutos(minutosAislado)}
    </div>
  ) : isGeoespacial ? (
    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
      <i
        className="bi bi-geo-alt-fill"
        style={{ color: '#dc3545', fontSize: '0.95rem', animation: 'geoPulse 1.4s ease-in-out infinite' }}
      />
      <span style={{ color: '#B00020', fontWeight: 600, fontSize: '0.88rem' }}>
        {tiempoFueraBadge(alerta.tsGeneracion)}
      </span>
    </div>
  ) : (
    <>
      <DatoDesactualizadoBadge alerta={alerta} />
      <div
        title={textoAccionRecomendada(alerta)}
        style={{ display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}
      >
        {textoAccionRecomendada(alerta)}
      </div>
    </>
  );

  return (
    <>
      <SidebarCard
        severidad={severidad}
        icono={anestro ? 'bi-hourglass-split' : ICONO[clave] ?? 'bi-exclamation-triangle-fill'}
        categoria={CATEGORIA[clave]}
        titulo={titulo}
        cuerpo={cuerpoPrincipal}
        timestamp={haceCuanto(alerta.tsGeneracion)}
        acento={isUnread}
        apagada={resuelta}
        seleccionada={isMenuOpen}
        onClick={() => onClick(alerta)}
        acciones={<i className="bi bi-chevron-right" style={{ color: '#98A2B3' }} />}
      />
      <style>{`
        @keyframes geoPulse {
          0%, 100% { opacity: 1; transform: scale(1); }
          50%       { opacity: 0.5; transform: scale(1.3); }
        }
      `}</style>
    </>
  );
}
