import { useEffect, useState, useCallback, useRef } from 'react';
import { Spinner, Form } from 'react-bootstrap';
import {
  getLineaTiempoAlertas,
  type LineaTiempoEventoResponse,
  type LineaTiempoFiltros,
} from '../api/animalesApi';
import './LineaTiempoAlertas.css';

// ── Label maps ────────────────────────────────────────────────────────────────

const TIPO_ALERTA_LABEL: Record<string, string> = {
  CELO: 'Celo',
  SALUD: 'Sanitaria',
  ABIGEATO: 'Abigeato',
  FUERA_DE_PERIMETRO: 'Fuera de perímetro',
};

const TIPO_ALERTA_ICON: Record<string, string> = {
  CELO: 'bi-heart-pulse-fill',
  SALUD: 'bi-shield-fill-exclamation',
  ABIGEATO: 'bi-geo-alt-fill',
  FUERA_DE_PERIMETRO: 'bi-bounding-box-circles',
};

const TIPO_INTERVENCION_LABEL: Record<string, string> = {
  TRATAMIENTO: 'Tratamiento',
  VACUNACION: 'Vacunación',
  REVISION_VETERINARIA: 'Revisión Veterinaria',
  INSEMINACION: 'Inseminación',
  OTRO: 'Otro',
};

const TIPO_INTERVENCION_ICON: Record<string, string> = {
  TRATAMIENTO: 'bi-capsule-pill',
  VACUNACION: 'bi-syringe',
  REVISION_VETERINARIA: 'bi-clipboard-plus-fill',
  INSEMINACION: 'bi-eyedropper',
  OTRO: 'bi-clipboard2-pulse-fill',
};

const FILTER_OPTIONS = [
  { value: '', label: 'Todos los tipos' },
  { value: 'celo', label: 'Celo (Reproductivo)' },
  { value: 'sanitario', label: 'Sanitaria' },
  { value: 'geoespacial', label: 'Geoespacial (Abigeato)' },
  { value: 'perimetro', label: 'Fuera de perímetro' },
];

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleString('es-AR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function formatDateOnly(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function severidadClass(sev: string | null): string {
  if (sev === 'ALTA') return 'sev-alta';
  if (sev === 'MEDIA') return 'sev-media';
  if (sev === 'BAJA') return 'sev-baja';
  return 'sev-none';
}

function estadoClass(estado: string | null): string {
  if (estado === 'CONFIRMADA') return 'estado-confirmada';
  if (estado === 'DESCARTADA') return 'estado-descartada';
  return 'estado-sin-atender';
}

function estadoLabel(estado: string | null): string {
  if (estado === 'CONFIRMADA') return 'Confirmada';
  if (estado === 'DESCARTADA') return 'Descartada';
  return 'Sin atender';
}

/** Convert a local datetime-local input value (YYYY-MM-DDTHH:mm) to ISO-8601 for the API */
function localToIso(val: string): string {
  if (!val) return '';
  // datetime-local gives YYYY-MM-DDTHH:mm; add :00 for seconds
  return val.length === 16 ? `${val}:00` : val;
}

// ── Popup card ────────────────────────────────────────────────────────────────

interface PopupProps {
  evento: LineaTiempoEventoResponse;
  onClose: () => void;
  position: 'above' | 'below';
}

function EventoPopup({ evento, onClose, position }: PopupProps) {
  const isAlerta = evento.tipoEvento === 'ALERTA';
  const ref = useRef<HTMLDivElement>(null);

  // Close on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [onClose]);

  return (
    <div
      ref={ref}
      className={`lt-popup ${position === 'above' ? 'lt-popup--above' : 'lt-popup--below'} ${isAlerta ? 'lt-popup--alerta' : 'lt-popup--intervencion'}`}
    >
      <button className="lt-popup-close" onClick={onClose} aria-label="Cerrar">
        <i className="bi bi-x" />
      </button>

      {isAlerta ? (
        <>
          {/* Header */}
          <div className="lt-popup-header">
            <span className={`lt-popup-icon-wrap lt-popup-icon-wrap--alerta ${severidadClass(evento.severidad)}`}>
              <i className={`bi ${TIPO_ALERTA_ICON[evento.tipo ?? ''] ?? 'bi-bell-fill'}`} />
            </span>
            <div>
              <div className="lt-popup-title">
                Alerta {TIPO_ALERTA_LABEL[evento.tipo ?? ''] ?? evento.tipo ?? '—'}
              </div>
              <div className="lt-popup-fecha">{formatDateTime(evento.fecha)}</div>
            </div>
          </div>

          {/* Chips */}
          <div className="lt-popup-chips">
            {evento.severidad && (
              <span className={`lt-chip lt-chip--sev ${severidadClass(evento.severidad)}`}>
                <i className="bi bi-exclamation-triangle-fill me-1" />
                Severidad {evento.severidad.charAt(0) + evento.severidad.slice(1).toLowerCase()}
              </span>
            )}
            <span className={`lt-chip lt-chip--estado ${estadoClass(evento.estadoResolucion)}`}>
              {evento.estadoResolucion === 'CONFIRMADA' && <i className="bi bi-check-circle-fill me-1" />}
              {evento.estadoResolucion === 'DESCARTADA' && <i className="bi bi-x-circle-fill me-1" />}
              {evento.estadoResolucion === 'SIN_ATENDER' && <i className="bi bi-clock-fill me-1" />}
              {estadoLabel(evento.estadoResolucion)}
            </span>
          </div>

          {/* Descripción contextual */}
          <div className="lt-popup-desc">
            {evento.tipo === 'CELO' && (
              <>
                <i className="bi bi-info-circle me-1" />
                Detección automática de signos de celo. La ventana óptima de inseminación es de 6 a 24 hs desde la generación.
              </>
            )}
            {evento.tipo === 'SALUD' && (
              <>
                <i className="bi bi-info-circle me-1" />
                Desvío en indicadores sanitarios respecto al perfil de referencia del animal.
              </>
            )}
            {evento.tipo === 'ABIGEATO' && (
              <>
                <i className="bi bi-info-circle me-1" />
                Patrón de desplazamiento inusual detectado. Posible riesgo de abigeato o extravío.
              </>
            )}
            {evento.tipo === 'FUERA_DE_PERIMETRO' && (
              <>
                <i className="bi bi-info-circle me-1" />
                El animal fue detectado fuera de los límites del perímetro configurado.
              </>
            )}
          </div>
        </>
      ) : (
        <>
          {/* Header intervención */}
          <div className="lt-popup-header">
            <span className="lt-popup-icon-wrap lt-popup-icon-wrap--intervencion">
              <i className={`bi ${TIPO_INTERVENCION_ICON[evento.tipoIntervencion ?? ''] ?? 'bi-clipboard2-pulse-fill'}`} />
            </span>
            <div>
              <div className="lt-popup-title">
                {TIPO_INTERVENCION_LABEL[evento.tipoIntervencion ?? ''] ?? evento.tipoIntervencion ?? 'Intervención'}
              </div>
              <div className="lt-popup-fecha">{formatDateTime(evento.fecha)}</div>
            </div>
          </div>

          {/* Interventor */}
          {evento.interventor && (
            <div className="lt-popup-interventor">
              <i className="bi bi-person-circle me-1" />
              {evento.interventor}
            </div>
          )}

          {/* Descripción */}
          {evento.descripcion && (
            <div className="lt-popup-desc">
              "{evento.descripcion.length > 120
                ? evento.descripcion.slice(0, 120) + '…'
                : evento.descripcion}"
            </div>
          )}

          {/* Alerta asociada */}
          {evento.alertaAsociadaId && (
            <div className="lt-popup-associated">
              <i className="bi bi-link-45deg me-1" />
              Asociada a alerta <strong>#{evento.alertaAsociadaId}</strong>
            </div>
          )}
        </>
      )}
    </div>
  );
}

// ── Nodo de la línea de tiempo ────────────────────────────────────────────────

interface NodoProps {
  evento: LineaTiempoEventoResponse;
  position: 'above' | 'below';
  index: number;
}

function EventoNodo({ evento, position, index }: NodoProps) {
  const [open, setOpen] = useState(false);
  const isAlerta = evento.tipoEvento === 'ALERTA';

  const nodeClass = [
    'lt-nodo',
    isAlerta ? `lt-nodo--alerta ${severidadClass(evento.severidad)}` : 'lt-nodo--intervencion',
  ].join(' ');

  const labelClass = [
    'lt-nodo-label',
    position === 'above' ? 'lt-nodo-label--above' : 'lt-nodo-label--below',
  ].join(' ');

  const label = isAlerta
    ? TIPO_ALERTA_LABEL[evento.tipo ?? ''] ?? evento.tipo ?? 'Alerta'
    : TIPO_INTERVENCION_LABEL[evento.tipoIntervencion ?? ''] ?? 'Intervención';

  const icon = isAlerta
    ? TIPO_ALERTA_ICON[evento.tipo?.toUpperCase() ?? ''] ?? 'bi-bell-fill'
    : TIPO_INTERVENCION_ICON[evento.tipoIntervencion?.toUpperCase() ?? ''] ?? 'bi-clipboard2-pulse-fill';

  return (
    <div className="lt-nodo-wrapper" style={{ '--nodo-index': index } as React.CSSProperties}>
      {/* Label flotante */}
      <div className={labelClass} onClick={() => setOpen(o => !o)}>
        <span className="lt-nodo-label-type">
          <i className={`bi ${icon} me-1`} />
          {label}
        </span>
        <span className="lt-nodo-label-time">{formatDateOnly(evento.fecha)}</span>
        <span className="lt-nodo-label-cta">Más info</span>
      </div>

      {/* Punto sobre la línea */}
      <button
        className={nodeClass}
        onClick={() => setOpen(o => !o)}
        aria-label={`Ver detalle de ${label}`}
        aria-expanded={open}
      >
        <i className={`bi ${icon}`} />
      </button>

      {/* Popup */}
      {open && (
        <EventoPopup
          evento={evento}
          onClose={() => setOpen(false)}
          position={position}
        />
      )}
    </div>
  );
}

// ── Componente principal ──────────────────────────────────────────────────────

interface Props {
  animalId: number;
}

export default function LineaTiempoAlertas({ animalId }: Props) {
  const [eventos, setEventos] = useState<LineaTiempoEventoResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filtros
  const [tipo, setTipo] = useState('');
  const [desde, setDesde] = useState('');
  const [hasta, setHasta] = useState('');

  const fetchData = useCallback(async (filtros: LineaTiempoFiltros) => {
    setLoading(true);
    setError(null);
    try {
      const data = await getLineaTiempoAlertas(animalId, filtros);
      setEventos(data);
    } catch {
      setError('No se pudo cargar la línea de tiempo.');
    } finally {
      setLoading(false);
    }
  }, [animalId]);

  useEffect(() => {
    const f: LineaTiempoFiltros = {};
    if (tipo) f.tipo = tipo;
    if (desde) f.desde = localToIso(desde);
    if (hasta) f.hasta = localToIso(hasta);
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchData(f);
  }, [tipo, desde, hasta, fetchData]);

  const totalAlertas = eventos.filter(e => e.tipoEvento === 'ALERTA').length;
  const totalIntervenciones = eventos.filter(e => e.tipoEvento === 'INTERVENCION').length;

  return (
    <section className="lt-section">
      {/* ── Encabezado ── */}
      <div className="lt-header">
        <div className="lt-header-left">
          <h3 className="lt-title">
            <i className="bi bi-clock-history me-2" />
            Línea de Tiempo
          </h3>
          {!loading && !error && (
            <div className="lt-counts">
              <span className="lt-count-badge lt-count-alerta">
                <i className="bi bi-bell-fill me-1" />{totalAlertas} alerta{totalAlertas !== 1 ? 's' : ''}
              </span>
              <span className="lt-count-badge lt-count-intervencion">
                <i className="bi bi-clipboard2-pulse-fill me-1" />{totalIntervenciones} intervención{totalIntervenciones !== 1 ? 'es' : ''}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* ── Filtros ── */}
      <div className="lt-filters">
        <div className="lt-filter-group">
          <label htmlFor="lt-tipo" className="lt-filter-label">
            <i className="bi bi-funnel me-1" />Tipo de alerta
          </label>
          <Form.Select
            id="lt-tipo"
            size="sm"
            value={tipo}
            onChange={e => setTipo(e.target.value)}
            className="lt-filter-select"
          >
            {FILTER_OPTIONS.map(o => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </Form.Select>
        </div>

        <div className="lt-filter-group">
          <label htmlFor="lt-desde" className="lt-filter-label">
            <i className="bi bi-calendar-event me-1" />Desde
          </label>
          <input
            id="lt-desde"
            type="datetime-local"
            className="lt-filter-input"
            value={desde}
            onChange={e => setDesde(e.target.value)}
          />
        </div>

        <div className="lt-filter-group">
          <label htmlFor="lt-hasta" className="lt-filter-label">
            <i className="bi bi-calendar-check me-1" />Hasta
          </label>
          <input
            id="lt-hasta"
            type="datetime-local"
            className="lt-filter-input"
            value={hasta}
            onChange={e => setHasta(e.target.value)}
          />
        </div>

        {(tipo || desde || hasta) && (
          <button
            className="lt-filter-clear"
            onClick={() => { setTipo(''); setDesde(''); setHasta(''); }}
          >
            <i className="bi bi-x-circle me-1" />Limpiar filtros
          </button>
        )}
      </div>

      {/* ── Estados ── */}
      {loading && (
        <div className="lt-state-wrapper">
          <Spinner animation="border" style={{ color: '#3B8D99' }} />
          <span className="ms-3 text-secondary">Cargando eventos…</span>
        </div>
      )}

      {error && !loading && (
        <div className="lt-state-wrapper lt-state--error">
          <i className="bi bi-exclamation-triangle-fill me-2" />
          {error}
        </div>
      )}

      {!loading && !error && eventos.length === 0 && (
        <div className="lt-state-wrapper lt-state--empty">
          <i className="bi bi-hourglass me-2" />
          Sin eventos registrados
          {(tipo || desde || hasta) ? ' con los filtros aplicados.' : ' para este animal.'}
        </div>
      )}

      {/* ── Rail de la línea de tiempo ── */}
      {!loading && !error && eventos.length > 0 && (
        <div className="lt-scroll-wrapper">
          <div className="lt-rail-container" style={{ '--event-count': eventos.length } as React.CSSProperties}>

            {/* Eje horizontal */}
            <div className="lt-axis" />

            {/* Nodos */}
            {eventos.map((ev, i) => (
              <EventoNodo
                key={`${ev.tipoEvento}-${ev.id}`}
                evento={ev}
                position={i % 2 === 0 ? 'above' : 'below'}
                index={i}
              />
            ))}

            {/* Fecha inicio y fin del eje */}
            <div className="lt-axis-label lt-axis-label--start">
              {formatDateOnly(eventos[0].fecha)}
            </div>
            <div className="lt-axis-label lt-axis-label--end">
              {formatDateOnly(eventos[eventos.length - 1].fecha)}
            </div>
          </div>
        </div>
      )}

      {/* ── Leyenda ── */}
      {!loading && !error && eventos.length > 0 && (
        <div className="lt-legend">
          <div className="lt-legend-item">
            <span className="lt-legend-dot lt-legend-dot--alta" />
            <span>Severidad Alta</span>
          </div>
          <div className="lt-legend-item">
            <span className="lt-legend-dot lt-legend-dot--media" />
            <span>Severidad Media</span>
          </div>
          <div className="lt-legend-item">
            <span className="lt-legend-dot lt-legend-dot--baja" />
            <span>Severidad Baja</span>
          </div>
          <div className="lt-legend-item">
            <span className="lt-legend-dot lt-legend-dot--intervencion" />
            <span>Intervención</span>
          </div>
        </div>
      )}
    </section>
  );
}
