import type { AlertaResponse } from '../api/alertaApi';
import SidebarCard, { type Severidad } from './SidebarCard';

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
};

function haceCuanto(ts: string) {
  const min = Math.floor((Date.now() - new Date(ts).getTime()) / 60000);
  if (isNaN(min)) return '';
  if (min < 1) return 'Ahora';
  if (min < 60) return `Hace ${min} m`;
  if (min < 1440) return `Hace ${Math.floor(min / 60)} h`;
  return `Hace ${Math.floor(min / 1440)} d`;
}

const textoAccionRecomendada = (alerta: AlertaResponse | null) => {
  if (alerta?.accionRecomendada) {
    return alerta.accionRecomendada;
  } else if (alerta && alerta.tipo === 'SALUD') {
    return 'Revisar al animal y realizar seguimiento clínico.';
  } else if (alerta && alerta.tipo === 'CELO') {
    return 'Realizar inseminación artificial dentro de la ventana óptima.';
  } else {
    return 'Revisar al animal y realizar seguimiento.';
  }
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

  return (
    <SidebarCard
      severidad={severidad}
      icono={ICONO[alerta.tipo] ?? 'bi-exclamation-triangle-fill'}
      titulo={`#${alerta.animalId} en estado ${ESTADO_ANIMAL[severidad]}`}
      cuerpo={
        <div
          title={textoAccionRecomendada(alerta)}
          style={{ display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}
        >
          {textoAccionRecomendada(alerta)}
        </div>
      }
      timestamp={haceCuanto(alerta.tsGeneracion)}
      acento={isUnread}
      apagada={resuelta}
      seleccionada={isMenuOpen}
      onClick={() => onClick(alerta)}
      acciones={<i className="bi bi-chevron-right" style={{ color: '#98A2B3' }} />}
    />
  );
}
