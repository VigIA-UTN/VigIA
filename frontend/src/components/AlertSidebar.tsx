import { useEffect, useState, useRef } from 'react';
import Form from 'react-bootstrap/Form';
import { getAlertasCelo, type AlertaResponse } from '../api/alertaApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { useAuthStore } from '../store/authStore';
import AlertDetailSidebar from './AlertDetailSidebar';

interface AlertSidebarProps {
  onClose: () => void;
}

export default function AlertSidebar({ onClose }: AlertSidebarProps) {
  const { selectedId } = useEstablecimientoStore();

  const [alertas, setAlertas] = useState<AlertaResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [isClosing, setIsClosing] = useState(false);

  const [selectedAlerta, setSelectedAlerta] = useState<AlertaResponse | null>(null);
  const [viewedIds, setViewedIds] = useState<Set<number>>(new Set());

  // Ref to track the highest received ID for Last-Event-ID recovery
  const maxAlertaIdRef = useRef<number>(0);

  useEffect(() => {
    if (!selectedId) return;
    let isMounted = true;
    const fetchAlertas = async () => {
      setLoading(true);
      try {
        const data = await getAlertasCelo(selectedId);
        if (isMounted) {
          setAlertas(data);
          if (data.length > 0) {
            maxAlertaIdRef.current = Math.max(...data.map(a => a.id));
          }
        }
      } catch (error) {
        console.error("Error fetching alertas:", error);
      } finally {
        if (isMounted) setLoading(false);
      }
    };
    fetchAlertas();

    // Setup SSE connection
    const token = useAuthStore.getState().token;
    let sseUrl = `http://localhost:8080/api/v1/alertas/stream?establecimientoId=${selectedId}`;
    if (token) {
      sseUrl += `&token=${token}`;
    }
    if (maxAlertaIdRef.current > 0) {
      sseUrl += `&lastEventId=${maxAlertaIdRef.current}`;
    }

    const eventSource = new EventSource(sseUrl);

    eventSource.onmessage = (event) => {
      if (!isMounted) return;
      const newAlerta = JSON.parse(event.data) as AlertaResponse;
      console.log("Alerta SSE recibida:", newAlerta);

      // Update max ID
      if (newAlerta.id > maxAlertaIdRef.current) {
        maxAlertaIdRef.current = newAlerta.id;
      }

      // Evitamos duplicados si el backend manda perdidas que justo entraron en la request inicial
      setAlertas((prev) => {
        if (prev.some(a => a.id === newAlerta.id)) return prev;
        return [newAlerta, ...prev];
      });
    };

    eventSource.onerror = (error) => {
      console.error("SSE connection error:", error);
      // EventSource tries to reconnect automatically.
    };

    return () => {
      isMounted = false;
      eventSource.close();
    };
  }, [selectedId]);

  const formatTime = (ts: string) => {
    const date = new Date(ts);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const handleAlertClick = (alerta: AlertaResponse) => {
    if (alerta.estado !== 'PENDIENTE') return;
    setViewedIds(prev => new Set(prev).add(alerta.id));
    setSelectedAlerta(alerta);
  };

  const handleActionComplete = (alertaId: number, nuevoEstado: 'CONFIRMADA' | 'DESCARTADA') => {
    setAlertas(prev =>
      prev.map(a => a.id === alertaId ? { ...a, estado: nuevoEstado } : a)
    );
  };

  const handleSimularAlerta = () => {
    const nuevaAlerta: AlertaResponse = {
      id: Math.floor(Math.random() * 1000000),
      animalId: Math.floor(Math.random() * 100) + 1,
      tipo: 'CELO',
      estado: 'PENDIENTE',
      tsGeneracion: new Date().toISOString(),
    };
    setAlertas((prev) => [nuevaAlerta, ...prev]);
  };

  const handleClose = () => {
    setIsClosing(true);
    setTimeout(() => {
      onClose();
    }, 280); // Ligeramente menor a 300ms para evitar parpadeos
  };

  return (
    <>
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          bottom: 0,
          width: '350px',
          backgroundColor: 'white',
          zIndex: 1000,
          boxShadow: '2px 0 10px rgba(0,0,0,0.2)',
          display: 'flex',
          flexDirection: 'column',
          padding: '20px',
          borderTopRightRadius: '12px',
          borderBottomRightRadius: '12px',
          border: '3px solid #13D4FB',
          borderLeft: 'none',
          animation: isClosing ? 'slideOutLeft 0.3s ease-in forwards' : 'slideInLeft 0.3s ease-out forwards'
        }}
      >
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h4 className="m-0 fw-bold" style={{ color: '#1E3A5F' }}>Alertas</h4>
          <div className="d-flex gap-2 align-items-center">
            <button
              className="btn btn-sm btn-outline-primary rounded-pill fw-semibold d-flex align-items-center gap-1"
              onClick={handleSimularAlerta}
              title="Simular nueva alerta"
            >
              <i className="bi bi-bug-fill"></i> Mock
            </button>
            <button className="btn btn-link text-secondary p-0" onClick={handleClose}>
              <i className="bi bi-chevron-double-left fs-5"></i>
            </button>
          </div>
        </div>

        <div className="d-flex gap-2 mb-3">
          <button
            className="btn rounded-pill px-3 py-1 fw-semibold d-flex align-items-center gap-1"
            style={{ backgroundColor: '#13D4FB', color: '#1E3A5F', border: '2px solid #1E3A5F' }}
          >
            <i className="bi bi-search"></i> Celo
          </button>
          <button
            className="btn rounded-pill px-3 py-1 fw-semibold d-flex align-items-center gap-1"
            style={{ backgroundColor: 'transparent', color: '#1E3A5F', border: '2px solid #1E3A5F' }}
          >
            <i className="bi bi-plus-circle"></i> Salud
          </button>
          <button
            className="btn rounded-pill px-3 py-1 fw-semibold d-flex align-items-center gap-1"
            style={{ backgroundColor: 'transparent', color: '#1E3A5F', border: '2px solid #1E3A5F' }}
          >
            <i className="bi bi-globe-americas"></i> Posición
          </button>
        </div>

        <Form.Select className="mb-4 rounded-pill" style={{ borderWidth: '2px' }}>
          <option>Filtrar por Severidad</option>
          <option value="alta">Alta</option>
          <option value="media">Media</option>
          <option value="baja">Baja</option>
        </Form.Select>

        <div className="flex-grow-1 overflow-auto pe-2 d-flex flex-column gap-3 custom-scrollbar">
          {loading && <div className="text-center text-muted mt-4">Cargando alertas...</div>}

          {!loading && alertas.length === 0 && (
            <div className="text-center text-muted mt-4">No hay alertas registradas.</div>
          )}

          {!loading && alertas.map((alerta) => {
            const isPendiente = alerta.estado === 'PENDIENTE';
            const isResolved = alerta.estado === 'CONFIRMADA' || alerta.estado === 'DESCARTADA';
            const isClickable = isPendiente;
            const isMenuOpen = selectedAlerta?.id === alerta.id;
            const isUnread = isPendiente && !viewedIds.has(alerta.id);

            // Severidad: el backend manda ALTA/MEDIA/BAJA. Fallback por tipo mientras no llegue.
            const gravedad: 'ALTA' | 'MEDIA' | 'BAJA' =
              (alerta.gravedad as 'ALTA' | 'MEDIA' | 'BAJA') ??
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
            const sev = severityMap[gravedad];

            // Estado resuelto: icono y colores neutrales
            const resolvedIconClass = alerta.estado === 'CONFIRMADA' ? 'bi-check-circle-fill' : 'bi-x-circle-fill';
            const resolvedIconColor = alerta.estado === 'CONFIRMADA' ? '#38A169' : '#A0AEC0';
            const resolvedIconBg = alerta.estado === 'CONFIRMADA' ? '#C6F6D5' : '#EDF2F7';

            return (
              <div key={alerta.id}>
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
                  onClick={() => handleAlertClick(alerta)}
                >
                  <div className="d-flex h-100">
                    {isUnread && (
                      <div style={{ width: '6px', backgroundColor: sev.stripColor, flexShrink: 0 }}></div>
                    )}
                    <div className="card-body p-3 d-flex gap-3 w-100">
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
                          <h6 className={`m-0 fw-bold lh-sm ${isResolved ? 'text-muted' : ''}`}>
                            #{alerta.animalId} en {alerta.tipo || 'CELO'}
                          </h6>
                          <small className="text-muted" style={{ fontSize: '0.75rem', whiteSpace: 'nowrap', marginLeft: '8px' }}>
                            {formatTime(alerta.tsGeneracion)}
                          </small>
                        </div>
                        <small className="text-muted lh-sm d-block">
                          {alerta.estado}
                        </small>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        <style>{`
        .alerta-clickable:hover {
          transform: translateY(-1px);
          box-shadow: 0 6px 16px rgba(0,0,0,0.15) !important;
        }
        @keyframes fadeInUp {
          from { opacity: 0; transform: translate(-50%, calc(-100% + 6px)); }
          to   { opacity: 1; transform: translate(-50%, -100%); }
        }
        @keyframes slideInLeft {
          from { opacity: 0; transform: translateX(-20px); }
          to   { opacity: 1; transform: translateX(0); }
        }
        @keyframes slideOutLeft {
          from { opacity: 1; transform: translateX(0); }
          to   { opacity: 0; transform: translateX(-20px); }
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
      {selectedAlerta && (
        <AlertDetailSidebar
          alerta={selectedAlerta}
          onClose={() => setSelectedAlerta(null)}
          onActionComplete={handleActionComplete}
        />
      )}
    </>
  );
}
