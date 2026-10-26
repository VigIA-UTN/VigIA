import { useEffect, useMemo, useState, useCallback, useRef } from 'react';
import { Spinner } from 'react-bootstrap';
import {
  getLineaTiempoAlertas,
  ESTADO_REPRODUCTIVO_LABEL,
  type EstadoReproductivoResponse,
  type LineaTiempoEventoResponse,
  type LineaTiempoFiltros,
} from '../api/animalesApi';
import { esAnestro, getAlertaDetalle, type AlertaResponse } from '../api/alertaApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import AlertDetailSidebar from './AlertDetailSidebar';
import DateRangeCalendar from './heatmap/DateRangeCalendar';
import { dayStart, lastDaysRange } from './heatmap/heatmapFilters';
import './heatmap/heatmap.css';
import './LineaTiempoAlertas.css';

// ── Label maps ────────────────────────────────────────────────────────────────

const TIPO_ALERTA_LABEL: Record<string, string> = {
  CELO: 'Celo',
  ANESTRO: 'Anestro',
};

const TIPO_ALERTA_ICON: Record<string, string> = {
  CELO: 'bi-heart-pulse-fill',
  ANESTRO: 'bi-hourglass-split',
};

// estadoResolucion del backend: PENDIENTE llega como SIN_ATENDER.
const ESTADO_ALERTA_LABEL: Record<string, string> = {
  SIN_ATENDER: 'Pendiente',
  CONFIRMADA: 'Confirmada',
  DESCARTADA: 'Descartada',
  ARCHIVADA: 'Archivada',
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

const DAY_MS = 86400000;
const PRESETS: [string, string][] = [['30', '30 días'], ['90', '90 días'], ['365', '12 meses'], ['todo', 'Todo']];
const pad2 = (n: number) => String(n).padStart(2, '0');

/** Día (timestamp de inicio de día) -> ISO-8601 local para la API; `finDelDia` usa 23:59:59. */
function diaToIso(ts: number, finDelDia = false): string {
  const d = new Date(ts);
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${finDelDia ? '23:59:59' : '00:00:00'}`;
}

const fmtDia = (ts: number) => { const d = new Date(ts); return `${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}/${d.getFullYear()}`; };

/** Evento de la línea de tiempo: los del backend más los estados reproductivos registrados por el usuario. */
type TimelineEvento = Omit<LineaTiempoEventoResponse, 'tipoEvento'> & {
  tipoEvento: 'ALERTA' | 'INTERVENCION' | 'ESTADO';
  estadoRepro?: EstadoReproductivoResponse;
};

const estadoToEvento = (e: EstadoReproductivoResponse): TimelineEvento => ({
  tipoEvento: 'ESTADO', id: e.id, fecha: e.fechaRegistro, tipo: e.estado, severidad: null, estadoResolucion: null,
  tipoIntervencion: null, descripcion: null, interventor: e.registradoPor, alertaAsociadaId: null, estadoRepro: e,
});

/** Clave de las alertas para los mapas de label/ícono: el anestro llega con tipo CELO. */
const claveAlerta = (e: TimelineEvento) => (esAnestro(e) ? 'ANESTRO' : (e.tipo ?? '').toUpperCase());

// ── Popup card ────────────────────────────────────────────────────────────────

/** Apertura del detalle de una alerta desde su popup. */
interface VerAlertaProps {
  onVerAlerta: (evento: TimelineEvento) => void;
  abriendoAlertaId: number | null;
  alertaError: { id: number; msg: string } | null;
}

interface PopupProps extends VerAlertaProps {
  evento: TimelineEvento;
  onClose: () => void;
  position: 'above' | 'below';
}

function EventoPopup({ evento, onClose, position, onVerAlerta, abriendoAlertaId, alertaError }: PopupProps) {
  const isAlerta = evento.tipoEvento === 'ALERTA';
  const isEstado = evento.tipoEvento === 'ESTADO';
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
      className={`lt-popup ${position === 'above' ? 'lt-popup--above' : 'lt-popup--below'} ${isAlerta ? `lt-popup--alerta ${severidadClass(evento.severidad)}` : isEstado ? 'lt-popup--estado' : 'lt-popup--intervencion'}`}
    >
      <button className="lt-popup-close" onClick={onClose} aria-label="Cerrar">
        <i className="bi bi-x" />
      </button>

      {isEstado && evento.estadoRepro ? (
        <>
          <div className="lt-popup-header">
            <span className="lt-popup-icon-wrap lt-popup-icon-wrap--estado">
              <i className="bi bi-flag-fill" />
            </span>
            <div>
              <div className="lt-popup-title">
                Estado reproductivo: {ESTADO_REPRODUCTIVO_LABEL[evento.estadoRepro.estado ?? ''] ?? evento.estadoRepro.estado ?? '—'}
              </div>
              <div className="lt-popup-fecha">{formatDateTime(evento.fecha)}</div>
            </div>
          </div>
          <div className="lt-popup-desc">
            Último parto: <strong>{formatDateOnly(evento.estadoRepro.fechaUltimoParto)}</strong><br />
            Última inseminación: <strong>{formatDateOnly(evento.estadoRepro.fechaUltimaInseminacion)}</strong>
          </div>
          {evento.interventor && (
            <div className="lt-popup-interventor">
              <i className="bi bi-person-circle me-1" />
              {evento.interventor}
            </div>
          )}
        </>
      ) : isAlerta ? (
        <>
          {/* Header */}
          <div className="lt-popup-header">
            <span className={`lt-popup-icon-wrap lt-popup-icon-wrap--alerta ${severidadClass(evento.severidad)}`}>
              <i className={`bi ${TIPO_ALERTA_ICON[claveAlerta(evento)] ?? 'bi-bell-fill'}`} />
            </span>
            <div>
              <div className="lt-popup-title">
                Alerta {TIPO_ALERTA_LABEL[claveAlerta(evento)] ?? evento.tipo ?? '—'}
              </div>
              <div className="lt-popup-fecha">{formatDateTime(evento.fecha)}</div>
            </div>
          </div>

          {/* Descripción contextual */}
          <div className="lt-popup-desc">
            {claveAlerta(evento) === 'CELO' && (
              <>
                <i className="bi bi-info-circle me-1" />
                Detección automática de signos de celo. La ventana óptima de inseminación es de 6 a 24 hs desde la generación.
              </>
            )}
            {claveAlerta(evento) === 'ANESTRO' && (
              <>
                {evento.valorDetectado != null ? (
                  // Los días ya vienen como dato: no se repite la descripción, que los incluye en el texto
                  <>
                    <strong>{Math.round(evento.valorDetectado)} días sin ciclo</strong>
                    {evento.valorUmbral != null && ` (umbral: ${Math.round(evento.valorUmbral)} días)`}
                    <br />
                    <i className="bi bi-info-circle me-1" />
                    Se recomienda revisión veterinaria.
                  </>
                ) : (
                  <>
                    <i className="bi bi-info-circle me-1" />
                    {/* descripcion = acción recomendada del detector, con los días sin ciclo */}
                    {evento.descripcion ?? 'El animal superó el umbral de días sin celo registrado. Se recomienda revisión veterinaria.'}
                  </>
                )}
              </>
            )}
          </div>

          {/* Estado y acceso al detalle en una fila, para que el popup no crezca de alto */}
          <div className="lt-popup-footer">
            {evento.estadoResolucion && (
              <span className="lt-popup-interventor mb-0">
                <i className="bi bi-check2-circle me-1" />
                Estado:&nbsp;<strong>{ESTADO_ALERTA_LABEL[evento.estadoResolucion] ?? evento.estadoResolucion}</strong>
              </span>
            )}
            <button
              type="button"
              className="lt-popup-link"
              onClick={() => onVerAlerta(evento)}
              disabled={abriendoAlertaId === evento.id}
            >
              <i className="bi bi-box-arrow-up-right me-1" />
              {abriendoAlertaId === evento.id ? 'Abriendo...' : `Ver alerta #${evento.id}`}
            </button>
          </div>
          {alertaError?.id === evento.id && (
            <div role="alert" style={{ color: '#D50000', fontSize: '0.8rem' }}>{alertaError.msg}</div>
          )}
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

interface NodoProps extends VerAlertaProps {
  evento: TimelineEvento;
  position: 'above' | 'below';
  index: number;
}

function EventoNodo({ evento, position, index, ...verAlerta }: NodoProps) {
  const [open, setOpen] = useState(false);
  const isAlerta = evento.tipoEvento === 'ALERTA';
  const isEstado = evento.tipoEvento === 'ESTADO';

  const nodeClass = [
    'lt-nodo',
    isAlerta ? `lt-nodo--alerta ${severidadClass(evento.severidad)}` : isEstado ? 'lt-nodo--estado' : 'lt-nodo--intervencion',
  ].join(' ');

  const labelClass = [
    'lt-nodo-label',
    position === 'above' ? 'lt-nodo-label--above' : 'lt-nodo-label--below',
  ].join(' ');

  const label = isAlerta
    ? TIPO_ALERTA_LABEL[claveAlerta(evento)] ?? evento.tipo ?? 'Alerta'
    : isEstado
      ? ESTADO_REPRODUCTIVO_LABEL[evento.tipo ?? ''] ?? evento.tipo ?? 'Estado'
      : TIPO_INTERVENCION_LABEL[evento.tipoIntervencion ?? ''] ?? 'Intervención';

  const icon = isAlerta
    ? TIPO_ALERTA_ICON[claveAlerta(evento)] ?? 'bi-bell-fill'
    : isEstado
      ? 'bi-flag-fill'
      : TIPO_INTERVENCION_ICON[evento.tipoIntervencion?.toUpperCase() ?? ''] ?? 'bi-clipboard2-pulse-fill';

  return (
    <div className="lt-nodo-wrapper" style={{ '--nodo-index': index } as React.CSSProperties}>
      {/* Label flotante */}
      <div className={labelClass} onClick={() => setOpen(o => !o)}>
        <span className="lt-nodo-label-type">{label}</span>
        <span className="lt-nodo-label-time">{formatDateOnly(evento.fecha)}</span>
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
          {...verAlerta}
        />
      )}
    </div>
  );
}

// ── Componente principal ──────────────────────────────────────────────────────

interface Props {
  animalId: number;
  /** Estados reproductivos del animal; se muestran como nodos propios (INSEMINADA se omite: ya figura como intervención). */
  estados?: EstadoReproductivoResponse[];
  /** Se llama al confirmar o descartar una alerta desde su detalle (confirmar un anestro registra un estado). */
  onAlertaResuelta?: () => void;
}

export default function LineaTiempoAlertas({ animalId, estados, onAlertaResuelta }: Props) {
  const [remotos, setRemotos] = useState<LineaTiempoEventoResponse[]>([]);
  const { selectedId } = useEstablecimientoStore();

  // Detalle de alerta abierto desde un popup; version fuerza la recarga al resolverla.
  const [alertaAbierta, setAlertaAbierta] = useState<AlertaResponse | null>(null);
  const [abriendoAlertaId, setAbriendoAlertaId] = useState<number | null>(null);
  const [alertaError, setAlertaError] = useState<{ id: number; msg: string } | null>(null);
  const [version, setVersion] = useState(0);
  const [loading, setLoading] = useState(true);
  const [cargado, setCargado] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Filtros
  const [desde, setDesde] = useState<number | null>(null);
  const [hasta, setHasta] = useState<number | null>(null);
  const [calOpen, setCalOpen] = useState(false);
  const calRef = useRef<HTMLDivElement>(null);
  const presetActivo = desde == null && hasta == null ? 'todo'
    : PRESETS.find(([k]) => k !== 'todo' && desde === lastDaysRange(Number(k)).from && hasta === lastDaysRange(Number(k)).to)?.[0];

  useEffect(() => {
    if (!calOpen) return;
    const close = (e: MouseEvent) => { if (!calRef.current?.contains(e.target as Node)) setCalOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [calOpen]);

  const fetchData = useCallback(async (filtros: LineaTiempoFiltros) => {
    setLoading(true);
    setError(null);
    try {
      const data = await getLineaTiempoAlertas(animalId, { ...filtros, tipo: 'celo' });
      // Solo celo: alertas de celo e inseminaciones (el backend no filtra intervenciones por tipo)
      setRemotos(data.filter(e =>
        e.tipoEvento === 'ALERTA' ? e.tipo === 'CELO' : e.tipoIntervencion === 'INSEMINACION'));
    } catch {
      setError('No se pudo cargar la línea de tiempo.');
    } finally {
      setLoading(false);
      setCargado(true);
    }
  }, [animalId]);

  useEffect(() => {
    const f: LineaTiempoFiltros = {};
    if (desde != null) f.desde = diaToIso(desde);
    if (hasta != null) f.hasta = diaToIso(hasta, true);
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchData(f);
  }, [desde, hasta, fetchData, version]);

  const abrirAlerta = async (ev: TimelineEvento) => {
    if (!selectedId) return;
    setAbriendoAlertaId(ev.id); setAlertaError(null);
    try {
      const d = await getAlertaDetalle(ev.id, selectedId);
      // El detalle solo trae el disparador (CELO): el subtipo del evento es el que marca el anestro
      setAlertaAbierta({
        id: d.id, tipo: d.datoDisparador, subtipo: ev.subtipo, animalId: d.animalId,
        tsGeneracion: d.tsGeneracion, estado: d.estadoActual, accionRecomendada: d.accionRecomendada,
      });
    } catch {
      setAlertaError({ id: ev.id, msg: 'No se pudo abrir la alerta.' });
    } finally {
      setAbriendoAlertaId(null);
    }
  };

  const eventos = useMemo<TimelineEvento[]>(() => {
    const deMs = desde ?? -Infinity;
    const haMs = hasta != null ? hasta + DAY_MS - 1 : Infinity;
    const propios = (estados ?? [])
      .filter(e => e.estado !== 'INSEMINADA')
      .map(estadoToEvento)
      .filter(e => { const t = new Date(e.fecha).getTime(); return t >= deMs && t <= haMs; });
    return [...(remotos as TimelineEvento[]), ...propios]
      .sort((a, b) => new Date(a.fecha).getTime() - new Date(b.fecha).getTime());
  }, [remotos, estados, desde, hasta]);

  return (
    <section className="lt-section">
      {/* ── Encabezado ── */}
      <div className="lt-header">
        <div className="lt-header-left">
          <h3 className="lt-title">
            Línea de tiempo reproductiva
          </h3>
        </div>
      </div>

      {/* ── Filtros ── */}
      <div className="lt-filters">
        <div className="lt-presets">
          {PRESETS.map(([k, l]) => (
            <button key={k} type="button" className={presetActivo === k ? 'on' : ''}
              onClick={() => {
                const r = k === 'todo' ? null : lastDaysRange(Number(k));
                setDesde(r?.from ?? null); setHasta(r?.to ?? null);
              }}>{l}</button>
          ))}
        </div>

        <div className="lt-filter-group" ref={calRef}>
          <button type="button" className="lt-date-btn" aria-expanded={calOpen} onClick={() => setCalOpen(o => !o)}>
            <i className="bi bi-calendar3" />
            {desde == null && hasta == null ? 'Todo el historial' : `${desde != null ? fmtDia(desde) : '…'} → ${hasta != null ? fmtDia(hasta) : '…'}`}
          </button>
          {calOpen && (
            <div className="hm-panel lt-cal-pop">
              <DateRangeCalendar
                from={desde ?? hasta ?? dayStart(new Date())}
                to={hasta ?? desde ?? dayStart(new Date())}
                onChange={(f, t) => { setDesde(f); setHasta(t); if (f !== t) setCalOpen(false); }}
              />
            </div>
          )}
        </div>
      </div>

      {/* ── Estados ── */}
      {/* Primera carga: esqueleto con la misma altura que el rail para que la página no salte */}
      {!cargado && (
        <div className="lt-scroll-wrapper lt-skeleton">
          <div className="lt-rail-container">
            <div className="lt-axis" />
            <Spinner animation="border" className="lt-skeleton-spinner" />
          </div>
        </div>
      )}

      {error && !loading && (
        <div className="lt-state-wrapper lt-state--error">
          <i className="bi bi-exclamation-triangle-fill me-2" />
          {error}
        </div>
      )}

      {/* Recargas: se mantiene el contenido anterior atenuado hasta que llegan los datos */}
      {cargado && !error && eventos.length === 0 && (
        <div className={`lt-state-wrapper lt-state--empty${loading ? ' lt-refrescando' : ''}`}>
          <i className="bi bi-hourglass me-2" />
          Sin eventos registrados
          {(desde != null || hasta != null) ? ' con los filtros aplicados.' : ' para este animal.'}
        </div>
      )}

      {/* ── Rail de la línea de tiempo ── */}
      {cargado && !error && eventos.length > 0 && (
        <div className={`lt-scroll-wrapper${loading ? ' lt-refrescando' : ''}`}>
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
                onVerAlerta={abrirAlerta}
                abriendoAlertaId={abriendoAlertaId}
                alertaError={alertaError}
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

      {alertaAbierta && (
        <AlertDetailSidebar
          alerta={alertaAbierta}
          mode="fixed"
          onClose={() => setAlertaAbierta(null)}
          onActionComplete={() => { setVersion(v => v + 1); onAlertaResuelta?.(); }}
        />
      )}
    </section>
  );
}
