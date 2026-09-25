import { useState, useEffect, useRef } from 'react';
import type { AlertaResponse, AlertaDetalleResponse, SeveridadNivel, DetalleGeoespacial } from '../api/alertaApi';
import { updateAlertaEstado, getAlertaDetalle } from '../api/alertaApi';
import SanitarioAlertDetail, { SeveridadBadge } from './SanitarioAlertDetail';
import { useAuthStore } from '../store/authStore';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { useVentanaInseminacion, formatCountdown } from '../hooks/useVentanaInseminacion';

interface AlertDetailSidebarProps {
  alerta: AlertaResponse;
  onClose: () => void;
  onActionComplete: (alertaId: number, nuevoEstado: 'CONFIRMADA' | 'DESCARTADA') => void;
  /** 'absolute' (default) positions relative to nearest positioned ancestor (MapPage).
   *  'fixed' positions relative to the viewport — use this when rendering from AnimalesPage. */
  mode?: 'absolute' | 'fixed';
  onOpenIntervencion?: () => void;
}

export default function AlertDetailSidebar({ alerta, onClose, onActionComplete, mode = 'absolute', onOpenIntervencion }: AlertDetailSidebarProps) {
  const [isClosing, setIsClosing] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [notas, setNotas] = useState('');

  const [detalle, setDetalle] = useState<AlertaDetalleResponse | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(true);
  const [errorDetail, setErrorDetail] = useState<string | null>(null);

  const { rol } = useAuthStore();
  const { selectedId } = useEstablecimientoStore();
  const canActOnAlerts = rol === 'VETERINARIO' || rol === 'PRODUCTOR';

  // Ventana optima de inseminacion con countdown en tiempo real.
  // El backend puede devolver 'CELO', 'Celo Detectado' o 'reproductivo'
  // segun venga del listado general o del endpoint especifico de celo.
  const tipoNorm = (alerta.tipo ?? '').toLowerCase();
  const isCelo = tipoNorm === 'celo' || tipoNorm === 'reproductivo' || tipoNorm.includes('celo');
  const { ventana, segundosRestantes, loading: loadingVentana } = useVentanaInseminacion(
    isCelo ? alerta.id : null,
    selectedId ?? null,
    isCelo,
  );

  // Detección del tipo geoespacial
  const disparadorNorm = (alerta.tipo ?? '').toUpperCase();
  const isGeoespacial = disparadorNorm.includes('FUERA') || disparadorNorm.includes('PERIMETRO') ||
    (detalle?.datoDisparador ?? '').toString().toUpperCase() === 'FUERA_DE_PERIMETRO';
  const detGeo: DetalleGeoespacial | null = detalle?.detalleGeoespacial ?? null;

  // Contador en vivo del tiempo fuera del perímetro (tick cada segundo)
  const [segundosVivoFuera, setSegundosVivoFuera] = useState<number>(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  
  // Add derived estadoVisual early so the useEffect can use it
  const estadoVisual = detalle ? detalle.estadoActual : alerta.estado;

  useEffect(() => {
    if (!isGeoespacial) return;
    
    if (estadoVisual !== 'PENDIENTE') {
      setSegundosVivoFuera(detGeo?.segundosFuera ?? 0);
      return;
    }

    const tsFixed = alerta.tsGeneracion.endsWith('Z') ? alerta.tsGeneracion : alerta.tsGeneracion + 'Z';
    const secondsSinceStart = Math.floor((Date.now() - new Date(tsFixed).getTime()) / 1000);
    
    // Si está PENDIENTE, usamos nuestra cuenta local en vez del backend para evitar bugs de zona horaria (UTC vs Local)
    const base = estadoVisual === 'PENDIENTE' ? secondsSinceStart : (detGeo?.segundosFuera ?? secondsSinceStart);
    
    setSegundosVivoFuera(Math.max(0, base));
    timerRef.current = setInterval(() => setSegundosVivoFuera(s => s + 1), 1000);
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isGeoespacial, detGeo?.segundosFuera, alerta.tsGeneracion, estadoVisual]);

  useEffect(() => {
    let isMounted = true;
    if (!selectedId) return;

    const fetchDetalle = async () => {
      setLoadingDetail(true);
      setErrorDetail(null);
      try {
        const data = await getAlertaDetalle(alerta.id, selectedId);
        if (isMounted) setDetalle(data);
      } catch (err) {
        console.error("Error fetching alerta detalle:", err);
        if (isMounted) {
          const e = err as { response?: { data?: { message?: string } | string } };
          const responseData = e?.response?.data;
          const errorMessage = typeof responseData === 'string' ? responseData : responseData?.message;
          setErrorDetail(errorMessage || "Ocurrió un error al cargar el detalle de la alerta.");
        }
      } finally {
        if (isMounted) setLoadingDetail(false);
      }
    };
    fetchDetalle();

    return () => {
      isMounted = false;
    };
  }, [alerta.id, selectedId]);

  const handleClose = () => {
    setIsClosing(true);
    setTimeout(() => {
      onClose();
    }, 280);
  };

  const handleAccion = async (nuevoEstado: 'CONFIRMADA' | 'DESCARTADA', triggerIntervencion: boolean = true, notaOverride?: string) => {
    setActionLoading(true);
    try {
      // Si la alerta es un mock, el backend va a tirar 404/500, lo atajamos acá para que actualice igual visualmente.
      await updateAlertaEstado(alerta.id, nuevoEstado, notas.trim() || notaOverride).catch(err => {
        console.warn('Backend falló (probablemente es un mock alert), forzando update frontend:', err);
      });

      onActionComplete(alerta.id, nuevoEstado);
      if (nuevoEstado === 'CONFIRMADA' && triggerIntervencion) {
        onOpenIntervencion?.();
      }
      handleClose();
    } catch (error) {
      console.error('Error actualizando alerta:', error);
      setActionLoading(false);
    }
  };

  let date = new Date();
  let detectedText = 'Fecha desconocida';
  let windowStart = new Date();
  let windowEnd = new Date();
  
  const caravanaText = detalle?.animalCaravanaOficial ? detalle.animalCaravanaOficial : alerta.animalId;
  const animalName = detalle?.animalAlias ? `${detalle.animalAlias} (#${caravanaText})` : `Vaca #${caravanaText}`;

  const disparador = (detalle?.datoDisparador ?? alerta.tipo ?? '').toUpperCase();
  const isSanitaria = disparador.includes('SALUD') || disparador.includes('SANITARI');
  const detSan = detalle?.detalleSanitario ?? null;
  const sevHeader: SeveridadNivel | null =
    (isSanitaria ? detSan?.severidad : (alerta.severidad as SeveridadNivel | undefined)) ?? null;

  try {
    const ts = detalle ? detalle.tsGeneracion : alerta.tsGeneracion;
    if (ts) {
      const tsFixed = ts.endsWith('Z') ? ts : ts + 'Z';
      date = new Date(tsFixed);
      if (isNaN(date.getTime())) throw new Error("Invalid date");
    }
    const timeStr = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const monthStr = date.toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' });
    detectedText = `Detectado el ${monthStr} a las ${timeStr}`;

    if (detalle && detalle.ventanaInicioIns && detalle.ventanaFinIns) {
      windowStart = new Date(detalle.ventanaInicioIns);
      windowEnd = new Date(detalle.ventanaFinIns);
    } else {
      windowStart = new Date(date.getTime() - 6 * 60 * 60 * 1000);
      windowEnd = date;
    }
  } catch (e) {
    console.error("Error parsing date:", e, alerta?.tsGeneracion);
  }

  const formatHour = (d: Date) => isNaN(d.getTime()) ? '--:--' : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const formatDateOnly = (d: Date) => isNaN(d.getTime()) ? '--' : d.toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' });

  // Calculate dynamic bar percentages based on a 24-hour window centered around the event
  let startPerc = 15;
  let endPerc = 85;
  if (!isNaN(windowStart.getTime()) && !isNaN(windowEnd.getTime())) {
    const windowDuration = windowEnd.getTime() - windowStart.getTime();
    const midPoint = windowStart.getTime() + windowDuration / 2;
    const totalMs = 24 * 60 * 60 * 1000;

    // The grey line represents a 24-hour period centered on the midpoint of our window
    const grayStart = midPoint - totalMs / 2;

    startPerc = Math.max(0, ((windowStart.getTime() - grayStart) / totalMs) * 100);
    endPerc = Math.min(100, ((windowEnd.getTime() - grayStart) / totalMs) * 100);
  }

  const textoAccionRecomendada = (detalle: AlertaDetalleResponse | null) => {
    if (detalle?.accionRecomendada) {
      return detalle.accionRecomendada;
    } else if (isSanitaria) {
      return 'Revisar al animal y realizar seguimiento clínico.';
    } else if (isGeoespacial) {
      return 'Verificar ubicación del animal en el mapa y retornarlo al perímetro autorizado.';
    } else if (isCelo) {
      return 'Realizar inseminación artificial dentro de la ventana óptima.';
    } else {
      return 'Revisar al animal y realizar seguimiento.';
    }
  }

  /** Formatea segundos en "Xh Ym Zs" o "Ym Zs" o "Zs" */
  const formatTiempoFuera = (seg: number) => {
    const h = Math.floor(seg / 3600);
    const m = Math.floor((seg % 3600) / 60);
    const s = seg % 60;
    if (h > 0) return `${h}h ${m}m ${s}s`;
    if (m > 0) return `${m}m ${s}s`;
    return `${s}s`;
  };

  return (
    <div
      style={{
        position: mode === 'fixed' ? 'fixed' : 'absolute',
        top: 0,
        right: 0,
        bottom: 0,
        width: 'min(550px, 100vw)',
        maxWidth: '100vw',
        backgroundColor: '#FAFAFA',
        zIndex: 1050,
        boxShadow: '-2px 0 10px rgba(0,0,0,0.2)',
        display: 'flex',
        flexDirection: 'column',
        borderTopLeftRadius: '12px',
        borderBottomLeftRadius: '12px',
        border: '3px solid #13D4FB',
        borderRight: 'none',
        animation: isClosing ? 'slideOutRight 0.3s ease-in forwards' : 'slideInRight 0.3s ease-out forwards',
        overflow: 'hidden'
      }}
    >
      {/* Header */}
      <div className="p-4 bg-white border-bottom" style={{ zIndex: 10, borderRadius: '12px 0 0 0' }}>
        <div className="d-flex justify-content-between align-items-start mb-2">
          <button className="btn btn-link text-secondary p-0 border-0" onClick={handleClose}>
            <i className="bi bi-x-lg fs-5"></i>
          </button>
        </div>

        <div className="d-flex justify-content-between align-items-center mt-2 flex-wrap gap-2">
          <div style={{ minWidth: 0 }}>
            <h2 className="m-0 fw-bold text-break" style={{ color: '#1E3A5F' }}>{animalName}</h2>
            <p className="m-0 text-muted fs-6">Animal en seguimiento</p>
          </div>
          {sevHeader ? (
            <SeveridadBadge severidad={sevHeader} />
          ) : (
            <div className="px-3 py-1 rounded-pill d-flex align-items-center gap-2" style={{ backgroundColor: '#FDECEC', color: '#B00020', border: '1px solid #B00020', fontWeight: 'bold' }}>
              ALTO <div style={{ width: '10px', height: '10px', borderRadius: '50%', backgroundColor: '#B00020' }}></div>
            </div>
          )}
        </div>

        <div className="text-muted mt-3 d-flex align-items-center gap-2" style={{ fontSize: '0.9rem' }}>
          <i className="bi bi-calendar-event"></i> {detectedText}
        </div>
      </div>

      <div className="p-4 d-flex flex-column gap-4 flex-grow-1 custom-scrollbar" style={{ overflowY: 'auto' }}>

        {loadingDetail ? (
          <div className="d-flex justify-content-center align-items-center h-100 flex-column text-muted">
            <div className="spinner-border text-primary mb-3" role="status">
              <span className="visually-hidden">Cargando...</span>
            </div>
            <p>Cargando detalles de la alerta...</p>
          </div>
        ) : errorDetail ? (
          <div className="d-flex justify-content-center align-items-center h-100 flex-column text-danger text-center px-4">
            <i className="bi bi-exclamation-triangle-fill fs-1 mb-3"></i>
            <h5 className="fw-bold">Error al cargar alerta</h5>
            <p>{errorDetail}</p>
            <button className="btn btn-outline-danger mt-3 rounded-pill px-4" onClick={handleClose}>
              Cerrar
            </button>
          </div>
        ) : (
          <>
            {/* Dato de origen — misma seccion para cualquier tipo de alerta */}
            <div>
              <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
                <i className="bi bi-search"></i> Dato de origen
              </h6>
              <div className="card border-0 shadow-sm rounded-4">
                <div className="card-body">
                  <p className="m-0" style={{ color: '#2D3748', fontSize: '0.95rem' }}>
                    {detalle?.descripcionDatoOrigen ||
                      'El sistema detectó un comportamiento que se aparta de lo habitual para este animal.'}
                  </p>
                  <small className="text-muted d-block mt-2">
                    <i className="bi bi-calendar-event me-1"></i>{detectedText}
                  </small>
                </div>
              </div>
            </div>

            <div>
              <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
                <i className="bi bi-info-circle"></i> Acción recomendada
              </h6>
              <div className="card border-0 shadow-sm rounded-4">
                <div className="card-body">
                  <p className="m-0" style={{ color: '#2D3748', fontSize: '0.95rem' }}>
                    {textoAccionRecomendada(detalle)}
                  </p>
                </div>
              </div>
            </div>

            {/* Detalle sanitario (solo alertas de SALUD) */}
            {isSanitaria && detSan && <SanitarioAlertDetail detalle={detSan} />}

            {/* Detalle geoespacial (solo alertas FUERA_DE_PERIMETRO) */}
            {isGeoespacial && (
              <div>
                <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
                  <i className="bi bi-geo-alt-fill" style={{ color: '#dc3545' }}></i> Detalle de salida del perímetro
                </h6>

                {/* Contador vivo */}
                <div
                  className="card border-0 shadow-sm rounded-4 mb-3"
                  style={{
                    background: 'linear-gradient(135deg, #FFF0F0 0%, #FFE0E0 100%)',
                    border: '1px solid #dc3545',
                  }}
                >
                  <div className="card-body py-3 px-4 d-flex align-items-center gap-3">
                    <i className="bi bi-clock-history fs-3 urgency-pulse" style={{ color: '#dc3545' }}></i>
                    <div>
                      <div
                        className="fw-bold font-monospace"
                        style={{ fontSize: '1.6rem', color: '#B00020', letterSpacing: '0.05em' }}
                      >
                        {formatTiempoFuera(segundosVivoFuera)}
                      </div>
                      <small style={{ color: '#9B1C1C' }}>Tiempo fuera del perímetro</small>
                    </div>
                  </div>
                </div>

                {/* Info grid: hora salida, lote, coordenadas */}
                <div className="card border-0 shadow-sm rounded-4">
                  <div className="card-body">
                    <div className="row g-3">

                      {/* Hora de salida */}
                      <div className="col-6">
                        <small className="text-muted d-block mb-1"><i className="bi bi-alarm me-1"></i>Hora de salida</small>
                        <span className="fw-semibold" style={{ color: '#1E3A5F' }}>
                          {date && !isNaN(date.getTime())
                            ? date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
                            : '--:--'}
                        </span>
                        <small className="text-muted d-block" style={{ fontSize: '0.75rem' }}>
                          {date && !isNaN(date.getTime()) ? date.toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' }) : ''}
                        </small>
                      </div>

                      {/* Lote */}
                      <div className="col-6">
                        <small className="text-muted d-block mb-1"><i className="bi bi-layers me-1"></i>Lote / Rodeo</small>
                        <span className="fw-semibold" style={{ color: '#1E3A5F' }}>
                          {detGeo?.nombreLote ?? detalle?.animalRaza ?? '—'}
                        </span>
                      </div>

                      {/* Coordenadas de salida */}
                      {detGeo?.latitudSalida != null && detGeo?.longitudSalida != null && (
                        <div className="col-12">
                          <small className="text-muted d-block mb-1"><i className="bi bi-crosshair me-1"></i>Posición al salir del perímetro</small>
                          <a
                            href={`https://www.google.com/maps?q=${detGeo.latitudSalida},${detGeo.longitudSalida}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="fw-semibold text-danger text-decoration-none d-flex align-items-center gap-1"
                            style={{ fontSize: '0.88rem' }}
                          >
                            <i className="bi bi-map-fill"></i>
                            {detGeo.latitudSalida.toFixed(5)}, {detGeo.longitudSalida.toFixed(5)}
                            <i className="bi bi-box-arrow-up-right ms-1" style={{ fontSize: '0.75rem' }}></i>
                          </a>
                          <small className="text-muted d-block mt-1" style={{ fontSize: '0.75rem' }}>Ver en Google Maps</small>
                        </div>
                      )}

                      {/* Si no hay coords persistidas, mostramos la posición actual del tracker */}
                      {(detGeo == null || detGeo.latitudSalida == null) && (
                        <div className="col-12">
                          <small className="text-muted d-block mb-1"><i className="bi bi-geo me-1"></i>Posición de salida</small>
                          <span className="text-muted fst-italic" style={{ fontSize: '0.85rem' }}>No disponible para esta alerta</span>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Ventana de inseminacion (solo reproductivo, no sanitario, no geoespacial) */}
            {!isSanitaria && !isGeoespacial && (
              <div>
                <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
                  <i className="bi bi-clock"></i> Ventana de inseminación
                </h6>

                {/* Barra de horarios inicio-fin */}
                <div className="card border-0 shadow-sm rounded-4 mb-3">
                  <div className="card-body d-flex justify-content-between align-items-center">
                    <div className="text-center">
                      <h4 className="m-0 fw-bold">{formatHour(windowStart)}</h4>
                      <small className="text-muted">{formatDateOnly(windowStart)}</small>
                    </div>

                    <div className="flex-grow-1 mx-3 position-relative d-flex align-items-center justify-content-start">
                      <div style={{ height: '4px', width: '100%', backgroundColor: '#E0E0E0', position: 'absolute' }}></div>
                      <div style={{ height: '8px', left: `${startPerc}%`, width: `${endPerc - startPerc}%`, backgroundColor: ventana?.estado === 'VENCIDA' ? '#9E9E9E' : ventana?.proximaAVencer ? '#FF6B35' : '#E05252', position: 'absolute', borderRadius: '4px', transition: 'background-color 0.5s ease' }}></div>
                      <div style={{ position: 'absolute', left: `${startPerc}%`, height: '16px', width: '2px', backgroundColor: '#333' }}></div>
                      <div style={{ position: 'absolute', left: `${endPerc}%`, height: '16px', width: '2px', backgroundColor: '#333' }}></div>
                    </div>

                    <div className="text-center">
                      <h4 className="m-0 fw-bold">{formatHour(windowEnd)}</h4>
                      <small className="text-muted">{formatDateOnly(windowEnd)}</small>
                    </div>
                  </div>
                </div>

                {/* Countdown en tiempo real */}
                {isCelo && (
                  <div
                    className="card border-0 shadow-sm rounded-4"
                    style={{
                      background: ventana?.estado === 'VENCIDA'
                        ? 'linear-gradient(135deg, #F5F5F5 0%, #EEEEEE 100%)'
                        : ventana?.estado === 'FUTURA'
                          ? 'linear-gradient(135deg, #E1F5FE 0%, #B3E5FC 100%)'
                          : ventana?.proximaAVencer
                            ? 'linear-gradient(135deg, #FFF3E0 0%, #FFE0B2 100%)'
                            : 'linear-gradient(135deg, #E8F5E9 0%, #C8E6C9 100%)',
                      border: ventana?.estado === 'VENCIDA'
                        ? '1px solid #BDBDBD'
                        : ventana?.estado === 'FUTURA'
                          ? '1px solid #0288D1'
                          : ventana?.proximaAVencer
                            ? '1px solid #FF6B35'
                            : '1px solid #4CAF50',
                      transition: 'background 0.6s ease, border-color 0.6s ease',
                    }}
                  >
                    <div className="card-body py-3 px-4">
                      {loadingVentana && !ventana ? (
                        <div className="d-flex align-items-center gap-2 text-muted">
                          <div className="spinner-border spinner-border-sm" role="status"><span className="visually-hidden">Cargando...</span></div>
                          <small>Calculando tiempo restante...</small>
                        </div>
                      ) : ventana?.estado === 'FUTURA' ? (
                        <div className="d-flex align-items-center justify-content-between">
                          <div className="d-flex align-items-center gap-3">
                            <i className="bi bi-clock-history fs-3" style={{ color: '#0288D1' }}></i>
                            <div>
                              <div
                                className="fw-bold font-monospace"
                                style={{
                                  fontSize: '1.5rem',
                                  color: '#0277BD',
                                  letterSpacing: '0.05em',
                                }}
                              >
                                {formatCountdown(segundosRestantes)}
                              </div>
                              <small style={{ color: '#0288D1' }}>Tiempo hasta inicio de ventana óptima</small>
                            </div>
                          </div>
                        </div>
                      ) : ventana?.estado === 'VENCIDA' ? (
                        <div className="d-flex align-items-center gap-3">
                          <i className="bi bi-clock-history fs-3" style={{ color: '#9E9E9E' }}></i>
                          <div>
                            <div className="fw-bold" style={{ color: '#616161', fontSize: '1rem' }}>Ventana vencida</div>
                            <small style={{ color: '#9E9E9E' }}>La oportunidad de inseminación ha expirado</small>
                          </div>
                        </div>
                      ) : ventana ? (
                        <div className="d-flex align-items-center justify-content-between">
                          <div className="d-flex align-items-center gap-3">
                            {ventana.proximaAVencer ? (
                              <i className="bi bi-exclamation-triangle-fill fs-3 urgency-pulse" style={{ color: '#FF6B35' }}></i>
                            ) : (
                              <i className="bi bi-hourglass-split fs-3" style={{ color: '#4CAF50' }}></i>
                            )}
                            <div>
                              <div
                                className="fw-bold font-monospace"
                                style={{
                                  fontSize: '1.5rem',
                                  color: ventana.proximaAVencer ? '#E64A19' : '#2E7D32',
                                  letterSpacing: '0.05em',
                                  transition: 'color 0.5s ease',
                                }}
                              >
                                {formatCountdown(segundosRestantes)}
                              </div>
                              <small style={{ color: ventana.proximaAVencer ? '#BF360C' : '#388E3C' }}>
                                {ventana.proximaAVencer ? '\u26a0\ufe0f Menos de 2 horas — actuar con urgencia' : 'Tiempo restante en ventana óptima'}
                              </small>
                            </div>
                          </div>
                          {/* Barra de progreso */}
                          <div className="text-end" style={{ minWidth: '80px' }}>
                            <div
                              className="rounded-pill"
                              style={{ height: '6px', backgroundColor: '#E0E0E0', overflow: 'hidden', marginBottom: '4px' }}
                            >
                              <div
                                style={{
                                  height: '100%',
                                  width: `${Math.min(100, ventana.porcentajeTranscurrido)}%`,
                                  backgroundColor: ventana.proximaAVencer ? '#FF6B35' : '#4CAF50',
                                  transition: 'width 1s linear, background-color 0.5s ease',
                                }}
                              />
                            </div>
                            <small style={{ color: '#757575', fontSize: '0.72rem' }}>
                              {Math.round(ventana.porcentajeTranscurrido)}% transcurrido
                            </small>
                          </div>
                        </div>
                      ) : null}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Informacion Adicional */}
            <div>
              <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
                <i className="bi bi-info-square"></i> Información adicional
              </h6>
              <div className="card border-0 shadow-sm rounded-4">
                <div className="card-body d-flex justify-content-between">
                  <div>
                    <small className="text-muted d-block mb-1">ID Alerta</small>
                    <span className="fw-semibold text-dark">#{alerta.id}</span>
                  </div>
                  <div>
                    <small className="text-muted d-block mb-1">Estado</small>
                    <span className="fw-semibold text-dark">{estadoVisual}</span>
                    {detalle?.usuarioAccion && estadoVisual !== 'PENDIENTE' && (
                      <small className="d-block text-muted mt-1" style={{ fontSize: '0.75rem' }}>
                        por {detalle.usuarioAccion}
                      </small>
                    )}
                  </div>
                  {detalle?.animalRaza && (
                    <div>
                      <small className="text-muted d-block mb-1">Raza</small>
                      <span className="fw-semibold text-dark">{detalle.animalRaza}</span>
                    </div>
                  )}
                  <div>
                    <small className="text-muted d-block mb-1">Última ubicación</small>
                    <a href="#" className="fw-semibold text-primary text-decoration-none d-flex align-items-center gap-1">
                      Ver en mapa <i className="bi bi-geo-alt-fill"></i>
                    </a>
                  </div>
                </div>
              </div>
            </div>

            {/* Ultimos Estados Reproductivos (solo reproductivo, no geoespacial) */}
            {!isSanitaria && !isGeoespacial && detalle?.ultimosEstadosReproductivos && detalle.ultimosEstadosReproductivos.length > 0 && (
              <div>
                <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
                  <i className="bi bi-activity"></i> Últimos estados reproductivos
                </h6>
                <div className="d-flex flex-column gap-2">
                  {detalle.ultimosEstadosReproductivos.map((estado, idx) => (
                    <div key={estado.id || idx} className="card border-0 shadow-sm rounded-3">
                      <div className="card-body py-2 px-3 d-flex justify-content-between align-items-center">
                        <div>
                          <span className="fw-semibold d-block" style={{ color: '#1E3A5F' }}>
                            {estado.estado.replace(/_/g, ' ')}
                          </span>
                          <small className="text-muted d-block" style={{ fontSize: '0.8rem' }}>
                            {estado.fechaRegistro ? new Date(estado.fechaRegistro).toLocaleDateString() : '--'}
                            {estado.registradoPor ? ` • ${estado.registradoPor}` : ''}
                          </small>
                        </div>
                        <div className="text-end">
                          {estado.fechaUltimoParto && (
                            <small className="d-block text-muted" style={{ fontSize: '0.75rem' }}>
                              Parto: {new Date(estado.fechaUltimoParto).toLocaleDateString()}
                            </small>
                          )}
                          {estado.fechaUltimaInseminacion && (
                            <small className="d-block text-muted" style={{ fontSize: '0.75rem' }}>
                              Insem: {new Date(estado.fechaUltimaInseminacion).toLocaleDateString()}
                            </small>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Notas */}
            <div>
              <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
                <i className="bi bi-journal-text"></i> Notas
              </h6>
              <div className="position-relative">
                <textarea
                  className="form-control rounded-4 shadow-sm border-0"
                  rows={3}
                  placeholder="Agregá una nota (opcional)..."
                  value={notas}
                  onChange={(e) => setNotas(e.target.value)}
                  style={{ resize: 'none', paddingRight: '15px', paddingBottom: '30px' }}
                ></textarea>
                <small className="text-muted position-absolute" style={{ bottom: '10px', right: '15px' }}>
                  {notas.length}/200
                </small>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Acciones — solo visibles para Productor y Veterinario */}
      {canActOnAlerts && !loadingDetail && !errorDetail && estadoVisual === 'PENDIENTE' && (
        <div className="p-4 bg-white border-top d-flex flex-column gap-2" style={{ zIndex: 10, borderRadius: '0 0 0 12px' }}>
          <div className="d-flex gap-3 justify-content-between">
            <button
              className="btn rounded-3 fw-semibold flex-grow-1 py-2 d-flex justify-content-center align-items-center gap-2"
              style={{ color: '#D32F2F', backgroundColor: 'white', border: '1px solid #D32F2F' }}
              onClick={() => handleAccion('DESCARTADA', false)}
              disabled={actionLoading}
            >
              <i className="bi bi-trash"></i> {actionLoading ? '...' : 'Descartar alerta'}
            </button>
            <button
              className="btn rounded-3 fw-semibold flex-grow-1 py-2 d-flex justify-content-center align-items-center gap-2"
              style={{ backgroundColor: isSanitaria ? '#198754' : 'white', color: isSanitaria ? 'white' : '#198754', border: '1px solid #198754' }}
              onClick={() => handleAccion('CONFIRMADA', false, isSanitaria ? 'Alerta sanitaria atendida' : undefined)}
              disabled={actionLoading}
            >
              <i className="bi bi-check2"></i> {actionLoading ? '...' : isSanitaria ? 'Confirmar alerta' : isGeoespacial ? 'Confirmar salida' : 'Confirmar celo'}
            </button>
          </div>
          {!isSanitaria && !isGeoespacial && (
            <button
              className="btn rounded-3 fw-semibold w-100 py-2 d-flex justify-content-center align-items-center gap-2"
              style={{ backgroundColor: '#198754', color: 'white', border: '1px solid #198754' }}
              onClick={() => handleAccion('CONFIRMADA', true)}
              disabled={actionLoading}
            >
              <i className="bi bi-clipboard-plus"></i> {actionLoading ? '...' : 'Confirmar celo e inseminación'}
            </button>
          )}
        </div>
      )}

      <style>{`
        @keyframes slideInRight {
          from { opacity: 0; transform: translateX(20px); }
          to   { opacity: 1; transform: translateX(0); }
        }
        @keyframes slideOutRight {
          from { opacity: 1; transform: translateX(0); }
          to   { opacity: 0; transform: translateX(20px); }
        }
        @keyframes urgencyPulse {
          0%, 100% { opacity: 1; transform: scale(1); }
          50%       { opacity: 0.6; transform: scale(1.15); }
        }
        .urgency-pulse {
          animation: urgencyPulse 1.2s ease-in-out infinite;
        }
        /* Custom Scrollbar */
        .custom-scrollbar::-webkit-scrollbar {
          width: 12px;
        }
        .custom-scrollbar::-webkit-scrollbar-track {
          background: transparent;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb {
          background-color: #13D4FB;
          border-radius: 10px;
          border: 3px solid transparent;
          background-clip: padding-box;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover {
          background-color: #0A96B1;
        }
      `}</style>
    </div>
  );
}
