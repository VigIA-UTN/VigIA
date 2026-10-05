import { tieneDatoDesactualizado, type AlertaResponse } from '../api/alertaApi';
import { formatUltimaSenial } from '../utils/caravanaTelemetria';

/**
 * H0008: aviso compacto de "dato posiblemente desactualizado" para tarjetas y listados de alertas.
 * No renderiza nada si la alerta no lo requiere.
 */
export default function DatoDesactualizadoBadge({ alerta }: { alerta: AlertaResponse }) {
  if (!tieneDatoDesactualizado(alerta)) return null;

  const texto = alerta.caravanaUltimaSenial
    ? `Sin señal ${formatUltimaSenial(alerta.caravanaUltimaSenial).text}`
    : 'Sin transmisiones de la caravana';
  const titulo = `Dato posiblemente desactualizado: la caravana no transmite hace más de ${alerta.horasUmbralDatoDesactualizado ?? 2} h. Verificá el animal en campo antes de inseminar.`;

  return (
    <span
      title={titulo}
      className="d-inline-flex align-items-center gap-1 rounded-pill px-2"
      style={{ backgroundColor: '#FFF8E1', border: '1px solid #F2C94C', color: '#7A5A00', fontSize: '0.75rem', fontWeight: 600, whiteSpace: 'nowrap' }}
    >
      <i className="bi bi-wifi-off" /> {texto}
    </span>
  );
}
