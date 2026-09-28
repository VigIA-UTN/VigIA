import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ErrorBar, ResponsiveContainer,
} from 'recharts';
import type { BloqueComparativo, DetalleSanitario, SeveridadNivel } from '../api/alertaApi';

// ─── Severidad ───────────────────────────────────────────────────────────────

export const SEVERIDAD_STYLE: Record<SeveridadNivel, { bg: string; fg: string; dot: string; label: string }> = {
  ALTA: { bg: '#FED7D7', fg: '#C53030', dot: '#C53030', label: 'ALTA' },
  MEDIA: { bg: '#FEFCBF', fg: '#B7791F', dot: '#B7791F', label: 'MEDIA' },
  BAJA: { bg: '#C6F6D5', fg: '#276749', dot: '#276749', label: 'BAJA' },
};

export function SeveridadBadge({ severidad, size = 'md' }: { severidad?: SeveridadNivel | null; size?: 'sm' | 'md' }) {
  const s = severidad ? SEVERIDAD_STYLE[severidad] : null;
  const fontSize = size === 'sm' ? '0.78rem' : '0.9rem';
  const dotSize = size === 'sm' ? 8 : 10;
  if (!s) {
    return <span style={{ color: '#4A5568', fontWeight: 700, fontSize }}>SIN DATO</span>;
  }
  return (
    <span className="d-inline-flex align-items-center gap-2" style={{ color: s.fg, fontWeight: 700, fontSize }}>
      {s.label}
      <span style={{ width: dotSize, height: dotSize, borderRadius: '50%', background: s.dot }} />
    </span>
  );
}

// ─── Gráfico comparativo ─────────────────────────────────────────────────────

interface ChartRow {
  bloque: string;
  actual: number | null;
  referencia: number | null;
  desvio: number | null;
}

function toRows(serie: BloqueComparativo[]): ChartRow[] {
  return serie.map((b) => ({
    bloque: b.bloque,
    actual: b.actual,
    referencia: b.referenciaMedia,
    desvio: b.referenciaDesvio,
  }));
}

interface TooltipProps {
  active?: boolean;
  label?: string;
  payload?: { name: string; value: number | null; color: string; payload: ChartRow }[];
  unidad: string;
}

function ChartTooltip({ active, label, payload, unidad }: TooltipProps) {
  if (!active || !payload?.length) return null;
  const row = payload[0]?.payload;
  return (
    <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 8, padding: '8px 12px', fontSize: '0.82rem', boxShadow: '0 2px 8px rgba(0,0,0,0.08)' }}>
      <p style={{ margin: 0, fontWeight: 700, color: '#1E3A5F' }}>Bloque {label}</p>
      <p style={{ margin: '4px 0 0', color: '#38A169' }}>
        Actual: {row?.actual != null ? row.actual.toFixed(1) : 's/d'} {unidad}
      </p>
      <p style={{ margin: '2px 0 0', color: '#3182CE' }}>
        Referencia: {row?.referencia != null ? row.referencia.toFixed(1) : 's/d'}
        {row?.desvio != null ? ` ± ${row.desvio.toFixed(1)}` : ''} {unidad}
      </p>
    </div>
  );
}

function ComparativoChart({ titulo, subtitulo, serie, unidad }: { titulo: string; subtitulo: string; serie: BloqueComparativo[] | null; unidad: string }) {
  const sinTelemetria = serie == null;
  const rows = serie ? toRows(serie) : [];
  const hayReferencia = rows.some((r) => r.referencia != null);
  const hayActual = rows.some((r) => r.actual != null);

  return (
    <div className="card border-0 shadow-sm rounded-4 mb-3">
      <div className="card-body">
        <div className="d-flex justify-content-between align-items-baseline mb-1">
          <span className="fw-semibold" style={{ color: '#1E3A5F' }}>{titulo}</span>
          <small className="text-muted">{unidad}</small>
        </div>
        <p className="text-muted mb-2" style={{ fontSize: '0.78rem' }}>{subtitulo}</p>

        {sinTelemetria || !hayActual ? (
          <div className="text-muted text-center py-4" style={{ fontSize: '0.85rem' }}>
            <i className="bi bi-graph-up d-block fs-4 mb-2" />
            No hay telemetría suficiente en la ventana evaluada para reconstruir el gráfico.
          </div>
        ) : (
          <>
            <ResponsiveContainer width="100%" height={190}>
              <BarChart data={rows} margin={{ top: 8, right: 8, left: -18, bottom: 0 }} barGap={2} barCategoryGap="28%">
                <CartesianGrid strokeDasharray="3 3" stroke="#F0F4F8" vertical={false} />
                <XAxis dataKey="bloque" tick={{ fontSize: 11, fill: '#718096' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 10, fill: '#A0AEC0' }} axisLine={false} tickLine={false} />
                <Tooltip content={<ChartTooltip unidad={unidad} />} cursor={{ fill: 'rgba(0,0,0,0.03)' }} />
                <Legend wrapperStyle={{ fontSize: '0.78rem' }} />
                <Bar dataKey="actual" name="Actual" fill="#48BB78" radius={[4, 4, 0, 0]} maxBarSize={26} />
                {hayReferencia && (
                  <Bar dataKey="referencia" name="Perfil de referencia" fill="#90CDF4" radius={[4, 4, 0, 0]} maxBarSize={26}>
                    <ErrorBar dataKey="desvio" width={4} strokeWidth={1.5} stroke="#3182CE" />
                  </Bar>
                )}
              </BarChart>
            </ResponsiveContainer>
            {!hayReferencia && (
              <p className="text-muted mb-0 mt-1" style={{ fontSize: '0.76rem' }}>
                <i className="bi bi-info-circle me-1" />
                Sin perfil de referencia disponible (animal en calibración o recalibración): se muestra solo el comportamiento actual.
              </p>
            )}
          </>
        )}
      </div>
    </div>
  );
}

// ─── Componente principal ────────────────────────────────────────────────────

function formatDateTime(iso: string | null) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '—';
  return d.toLocaleString([], { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export default function SanitarioAlertDetail({ detalle }: { detalle: DetalleSanitario }) {
  return (
    <>
      {/* Descripción del desvío + severidad */}
      <div>
        <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
          Desvío sanitario detectado
        </h6>
        <div className="card border-0 shadow-sm rounded-4">
          <div className="card-body">
            <div className="d-flex justify-content-between align-items-center">
              <span className="text-muted" style={{ fontSize: '0.85rem' }}>Severidad</span>
              <SeveridadBadge severidad={detalle.severidad} />
            </div>
          </div>
        </div>
      </div>

      {/* Posible causa */}
      <div className="p-3 rounded-3 d-flex gap-3 align-items-start" style={{ backgroundColor: '#FFF5F5', color: '#742A2A', border: '1px solid #FEB2B2' }}>
        <i className="bi bi-clipboard2-pulse fs-5 mt-1" style={{ color: '#E53E3E' }} />
        <div>
          <span className="fw-semibold d-block mb-1">Posible causa</span>
          <p className="m-0" style={{ fontSize: '0.95rem' }}>
            {detalle.posibleCausa || 'No determinada por el modelo.'}
          </p>
        </div>
      </div>

      {/* Gráfico comparativo actual vs. referencia */}
      <div>
        <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
          Comportamiento actual vs. perfil de referencia
        </h6>
        <ComparativoChart
          titulo="Actividad"
          subtitulo="Nivel de movimiento promedio por bloque de 6 h de la ventana evaluada"
          serie={detalle.serieActividad}
          unidad="mov."
        />
        <ComparativoChart
          titulo="Desplazamiento"
          subtitulo="Distancia GPS recorrida (proxy) por bloque de 6 h"
          serie={detalle.serieDesplazamiento}
          unidad="dist."
        />
      </div>

      {/* Metadatos del análisis */}
      <div>
        <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
          Detalle del análisis
        </h6>
        <div className="card border-0 shadow-sm rounded-4">
          <div className="card-body d-flex flex-wrap gap-3">
            <div style={{ flex: '1 1 12rem', minWidth: 0 }}>
              <small className="text-muted d-block mb-1">Ventana evaluada</small>
              <span className="fw-semibold text-dark text-break">
                {formatDateTime(detalle.ventanaDesde)} → {formatDateTime(detalle.ventanaHasta)}
              </span>
            </div>
            <div style={{ flex: '1 1 12rem', minWidth: 0 }}>
              <small className="text-muted d-block mb-1">Perfil de referencia</small>
              <span className="fw-semibold text-dark text-break">
                {detalle.perfilCalculadoEn ? `calculado ${formatDateTime(detalle.perfilCalculadoEn)}` : 'no disponible'}
              </span>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
