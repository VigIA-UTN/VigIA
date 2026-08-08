import type { AlertaResponse } from '../api/alertaApi';

interface AlertNotificationCardProps {
  alerta: AlertaResponse;
  onClick: (alerta: AlertaResponse) => void;
  isMenuOpen?: boolean;
  isUnread?: boolean;
}

export default function AlertNotificationCard({
  alerta,
  onClick,
  isMenuOpen = false,
  isUnread = false,
}: AlertNotificationCardProps) {
  const isResolved = alerta.estado === 'CONFIRMADA' || alerta.estado === 'DESCARTADA';
  const isClickable = true;

  // Severidad: el backend manda ALTA/MEDIA/BAJA. Fallback por tipo mientras no llegue.
  const gravedad: 'ALTA' | 'MEDIA' | 'BAJA' =
    (alerta.severidad as 'ALTA' | 'MEDIA' | 'BAJA') ??
    (alerta.tipo === 'CELO' ? 'MEDIA' : 'ALTA');

  const severityMap = {
    ALTA: {
      stripColor: '#E53E3E',
      iconBg: '#FED7D7',
      iconColor: '#C53030',
      iconClass: 'bi-exclamation-octagon-fill',
    },
    MEDIA: {
      stripColor: '#D69E2E',
      iconBg: '#FEFCBF',
      iconColor: '#B7791F',
      iconClass: 'bi-exclamation-triangle-fill',
    },
    BAJA: {
      stripColor: '#38A169',
      iconBg: '#C6F6D5',
      iconColor: '#276749',
      iconClass: 'bi-info-circle-fill',
    },
  };
  const sev = severityMap[gravedad] || severityMap['MEDIA'];

  // Estado resuelto: icono y colores neutrales
  const resolvedIconClass = alerta.estado === 'CONFIRMADA' ? 'bi-check-circle-fill' : 'bi-x-circle-fill';
  const resolvedIconColor = alerta.estado === 'CONFIRMADA' ? '#38A169' : '#A0AEC0';
  const resolvedIconBg = alerta.estado === 'CONFIRMADA' ? '#C6F6D5' : '#EDF2F7';

  const formatTime = (ts: string) => {
    const date = new Date(ts);
    if (isNaN(date.getTime())) return '--:--';
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div
      className={`card border-0 rounded-4 ${isClickable ? 'alerta-clickable' : ''}`}
      style={{
        overflow: 'hidden',
        cursor: isClickable ? 'pointer' : 'default',
        transition: 'transform 0.15s ease, box-shadow 0.15s ease, background-color 0.15s ease',
        boxShadow: isMenuOpen ? '0 0 0 2px #1E3A5F' : isResolved ? 'none' : '0 1px 3px rgba(0,0,0,0.08)',
        backgroundColor: isMenuOpen ? '#E2E8F0' : isResolved ? '#F7FAFC' : 'white',
        opacity: isResolved ? 0.72 : 1,
      }}
      onClick={() => onClick(alerta)}
    >
      <div className="d-flex h-100">
        {isUnread && (
          <div style={{ width: '6px', backgroundColor: sev.stripColor, flexShrink: 0 }}></div>
        )}
        <div className="card-body p-3 d-flex gap-3 w-100 align-items-center">
          <div
            className="rounded-3 d-flex align-items-center justify-content-center"
            style={{
              width: '45px', height: '45px', flexShrink: 0,
              backgroundColor: isResolved ? resolvedIconBg : sev.iconBg,
            }}
          >
            <i
              className={`bi ${isResolved ? resolvedIconClass : sev.iconClass} fs-4`}
              style={{ color: isResolved ? resolvedIconColor : sev.iconColor }}
            ></i>
          </div>
          <div className="w-100">
            <div className="d-flex justify-content-between align-items-start mb-1">
              <h6 className={`m-0 fw-bold lh-sm ${isResolved ? 'text-muted' : ''}`} style={{ fontSize: '0.9rem' }}>
                #{alerta.animalId} en estado {alerta.estado}
              </h6>
              <small className="text-muted" style={{ fontSize: '0.75rem', whiteSpace: 'nowrap', marginLeft: '8px' }}>
                {formatTime(alerta.tsGeneracion)}
              </small>
            </div>
            <small className="text-muted lh-sm d-block" style={{ fontSize: '0.8rem' }}>
              {alerta.tipo === 'CELO' ? `Se estima alerta de ${alerta.tipo.toLowerCase()}` : `Alerta de ${alerta.tipo?.toLowerCase() || 'desconocida'}`}
            </small>
          </div>
        </div>
      </div>
    </div>
  );
}
