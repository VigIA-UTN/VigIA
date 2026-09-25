import { useEffect, useState, useRef } from 'react';
import Form from 'react-bootstrap/Form';
import { getAlertas, type AlertaResponse } from '../api/alertaApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { useAuthStore } from '../store/authStore';
import AlertDetailSidebar from './AlertDetailSidebar';
import AlertNotificationCard from './AlertNotificationCard';
import IntervencionFormPanel from './IntervencionFormPanel';

interface AlertSidebarProps {
  onClose: () => void;
  externalAlert: AlertaResponse | null
}

export default function AlertSidebar({ onClose, externalAlert }: AlertSidebarProps) {
  const { selectedId } = useEstablecimientoStore();

  const [alertas, setAlertas] = useState<AlertaResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [isClosing, setIsClosing] = useState(false);

  const [selectedAlerta, setSelectedAlerta] = useState<AlertaResponse | null>(null);
  const [isIntervencionOpen, setIsIntervencionOpen] = useState(false);
  const [intervencionAlerta, setIntervencionAlerta] = useState<AlertaResponse | null>(null);
  const [viewedIds, setViewedIds] = useState<Set<number>>(new Set());
  const [prevExternalAlert, setPrevExternalAlert] = useState<AlertaResponse | null>(null);

  // Filters state
  const [filtroTipo, setFiltroTipo] = useState<string | null>(null);
  const [filtroSeveridad, setFiltroSeveridad] = useState<string>('');

  // Ref to track the highest received ID for Last-Event-ID recovery
  const maxAlertaIdRef = useRef<number>(0);

  useEffect(() => {
    if (!selectedId) return;
    let isMounted = true;
    let eventSource: EventSource | null = null;

    const init = async () => {
      setLoading(true);
      try {
        const data = await getAlertas(selectedId, undefined, undefined, 'PENDIENTE');
        if (!isMounted) return;
        setAlertas(data);
        if (data.length > 0) {
          maxAlertaIdRef.current = Math.max(...data.map(a => a.id));
        }
      } catch (error) {
        console.error("Error fetching alertas:", error);
      } finally {
        if (isMounted) setLoading(false);
      }

      if (!isMounted) return;

      // Setup SSE connection AFTER fetching initial data
      const token = useAuthStore.getState().token;
      const API_URL = import.meta.env.VITE_API_URL as string;
      let sseUrl = `${API_URL}/alertas/stream?establecimientoId=${selectedId}`;
      if (token) {
        sseUrl += `&token=${token}`;
      }
      if (maxAlertaIdRef.current > 0) {
        sseUrl += `&lastEventId=${maxAlertaIdRef.current}`;
      }

      eventSource = new EventSource(sseUrl);

      eventSource.onmessage = (event) => {
        if (!isMounted) return;
        const newAlerta = JSON.parse(event.data) as AlertaResponse;
        console.log("Alerta SSE recibida en sidebar:", newAlerta);

        // Update max ID
        if (newAlerta.id > maxAlertaIdRef.current) {
          maxAlertaIdRef.current = newAlerta.id;
        }

        // Si la alerta ya existe, la actualizamos
        setAlertas((prev) => {
          if (prev.some(a => a.id === newAlerta.id)) {
            return prev.map(a => a.id === newAlerta.id ? newAlerta : a);
          }
          // Solo guardamos si es PENDIENTE (al menos como alerta nueva)
          if (newAlerta.estado !== 'PENDIENTE') return prev;
          return [newAlerta, ...prev];
        });

        // Si la alerta actualizada es la que estamos viendo en detalle, actualizamos el estado
        setSelectedAlerta((prev) => (prev?.id === newAlerta.id ? newAlerta : prev));
      };

      eventSource.onerror = (error) => {
        console.error("SSE connection error in sidebar:", error);
      };
    };

    init();

    return () => {
      isMounted = false;
      if (eventSource) {
        eventSource.close();
      }
    };
  }, [selectedId]);


  const handleAlertClick = (alerta: AlertaResponse) => {
    setViewedIds(prev => new Set(prev).add(alerta.id));
    setSelectedAlerta(alerta);
  };

  const handleActionComplete = (alertaId: number, nuevoEstado: 'CONFIRMADA' | 'DESCARTADA') => {
    setAlertas(prev =>
      prev.map(a => a.id === alertaId ? { ...a, estado: nuevoEstado } : a)
    );
  };



  const handleClose = () => {
    setIsClosing(true);
    setTimeout(() => {
      onClose();
    }, 280); // Ligeramente menor a 300ms para evitar parpadeos
  };

  if (externalAlert !== prevExternalAlert) {
    setPrevExternalAlert(externalAlert);
    if (externalAlert) {
      setSelectedAlerta(externalAlert);
    }
  }

  // Derived filtered alerts
  const alertasFiltradas = alertas.filter(a => {
    if (filtroTipo) {
      // 'GEO' es el valor interno que agrupa ABIGEATO y FUERA_DE_PERIMETRO
      if (filtroTipo === 'GEO') {
        const t = (a.tipo ?? '').toUpperCase();
        if (!t.includes('FUERA') && !t.includes('PERIMETRO') && t !== 'ABIGEATO' && t !== 'GEOESPACIAL') return false;
      } else if (a.tipo !== filtroTipo) {
        return false;
      }
    }
    if (filtroSeveridad && a.severidad !== filtroSeveridad) return false;
    return true;
  }).sort((a, b) => {
    // Ordenar por severidad descendente
    const sevValue = (sev: string | undefined) => {
      if (sev === 'ALTA') return 3;
      if (sev === 'MEDIA') return 2;
      if (sev === 'BAJA') return 1;
      return 0;
    };
    const sevDiff = sevValue(b.severidad) - sevValue(a.severidad);
    if (sevDiff !== 0) return sevDiff;
    // Si tienen la misma severidad, ordenar por fecha descendente para que las cercanas queden juntas
    return new Date(b.tsGeneracion).getTime() - new Date(a.tsGeneracion).getTime();
  });

  // Agrupar geoespaciales cercanas en tiempo (<= 15 min)
  const groupedAlertas: Array<{ type: 'single', alerta: AlertaResponse } | { type: 'group', alertas: AlertaResponse[] }> = [];
  let currentGroup: AlertaResponse[] = [];

  alertasFiltradas.forEach(alerta => {
    const isGeo = (alerta.tipo ?? '').toUpperCase().includes('FUERA') || (alerta.tipo ?? '').toUpperCase() === 'GEOESPACIAL';
    
    if (!isGeo) {
      if (currentGroup.length > 0) {
        if (currentGroup.length === 1) groupedAlertas.push({ type: 'single', alerta: currentGroup[0] });
        else groupedAlertas.push({ type: 'group', alertas: currentGroup });
        currentGroup = [];
      }
      groupedAlertas.push({ type: 'single', alerta });
      return;
    }
    
    if (currentGroup.length === 0) {
      currentGroup.push(alerta);
    } else {
      const lastTs = new Date(currentGroup[currentGroup.length - 1].tsGeneracion).getTime();
      const currTs = new Date(alerta.tsGeneracion).getTime();
      // Si la diferencia es <= 15 minutos, agrupamos
      if (Math.abs(lastTs - currTs) <= 15 * 60 * 1000) {
        currentGroup.push(alerta);
      } else {
        if (currentGroup.length === 1) groupedAlertas.push({ type: 'single', alerta: currentGroup[0] });
        else groupedAlertas.push({ type: 'group', alertas: currentGroup });
        currentGroup = [alerta];
      }
    }
  });
  if (currentGroup.length === 1) groupedAlertas.push({ type: 'single', alerta: currentGroup[0] });
  else if (currentGroup.length > 1) groupedAlertas.push({ type: 'group', alertas: currentGroup });

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

            <button className="btn btn-link text-secondary p-0" onClick={handleClose}>
              <i className="bi bi-chevron-double-left fs-5"></i>
            </button>
          </div>
        </div>

        <div className="d-flex flex-wrap gap-2 mb-3 justify-content-center">
          <button
            className="btn rounded-pill px-2 py-1 fw-semibold d-flex align-items-center gap-1"
            style={{
              backgroundColor: filtroTipo === 'CELO' ? '#13D4FB' : 'transparent',
              color: '#1E3A5F',
              border: '2px solid #1E3A5F',
              fontSize: '0.9rem'
            }}
            onClick={() => setFiltroTipo(prev => prev === 'CELO' ? null : 'CELO')}
          >
            <i className="bi bi-search"></i> Celo
          </button>
          <button
            className="btn rounded-pill px-2 py-1 fw-semibold d-flex align-items-center gap-1"
            style={{
              backgroundColor: filtroTipo === 'SALUD' ? '#13D4FB' : 'transparent',
              color: '#1E3A5F',
              border: '2px solid #1E3A5F',
              fontSize: '0.9rem'
            }}
            onClick={() => setFiltroTipo(prev => prev === 'SALUD' ? null : 'SALUD')}
          >
            <i className="bi bi-plus-circle"></i> Salud
          </button>
          <button
            className="btn rounded-pill px-2 py-1 fw-semibold d-flex align-items-center gap-1"
            style={{
              backgroundColor: filtroTipo === 'GEO' ? '#13D4FB' : 'transparent',
              color: '#1E3A5F',
              border: '2px solid #1E3A5F',
              fontSize: '0.9rem'
            }}
            onClick={() => setFiltroTipo(prev => prev === 'GEO' ? null : 'GEO')}
          >
            <i className="bi bi-geo-alt-fill"></i> Geoespacial
          </button>
        </div>

        <Form.Select
          className="mb-4 rounded-pill"
          style={{ borderWidth: '2px' }}
          value={filtroSeveridad}
          onChange={(e) => setFiltroSeveridad(e.target.value)}
        >
          <option value="">Filtrar por Severidad (Todas)</option>
          <option value="ALTA">Alta</option>
          <option value="MEDIA">Media</option>
          <option value="BAJA">Baja</option>
        </Form.Select>

        <div className="flex-grow-1 overflow-auto pe-2 d-flex flex-column gap-3 custom-scrollbar">
          {loading && <div className="text-center text-muted mt-4">Cargando alertas...</div>}

          {!loading && groupedAlertas.length === 0 && (
            <div className="text-center text-muted mt-4">No hay alertas registradas.</div>
          )}

          {!loading && groupedAlertas.map((item, idx) => {
            if (item.type === 'single') {
              const alerta = item.alerta;
              const isPendiente = alerta.estado === 'PENDIENTE';
              const isMenuOpen = selectedAlerta?.id === alerta.id;
              const isUnread = isPendiente && !viewedIds.has(alerta.id);

              return (
                <AlertNotificationCard
                  key={alerta.id}
                  alerta={alerta}
                  onClick={handleAlertClick}
                  isMenuOpen={isMenuOpen}
                  isUnread={isUnread}
                />
              );
            } else {
              return (
                <div key={`group-${idx}`} className="p-2 rounded-4" style={{ backgroundColor: '#FFF0F0', border: '1px dashed #dc3545' }}>
                  <div className="text-danger fw-bold mb-2 ms-2 d-flex align-items-center gap-1" style={{ fontSize: '0.85rem' }}>
                    <i className="bi bi-exclamation-diamond-fill"></i> {item.alertas.length} animales salieron del perímetro en horario cercano
                  </div>
                  <div className="d-flex flex-column gap-2">
                    {item.alertas.map(alerta => {
                      const isPendiente = alerta.estado === 'PENDIENTE';
                      const isMenuOpen = selectedAlerta?.id === alerta.id;
                      const isUnread = isPendiente && !viewedIds.has(alerta.id);
                      return (
                        <AlertNotificationCard
                          key={alerta.id}
                          alerta={alerta}
                          onClick={handleAlertClick}
                          isMenuOpen={isMenuOpen}
                          isUnread={isUnread}
                        />
                      );
                    })}
                  </div>
                </div>
              );
            }
          })}
        </div>

        <style>{`
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
          onOpenIntervencion={() => {
            setIntervencionAlerta(selectedAlerta);
            setIsIntervencionOpen(true);
          }}
        />
      )}
      {isIntervencionOpen && intervencionAlerta && (
        <IntervencionFormPanel
          alerta={intervencionAlerta}
          onClose={() => setIsIntervencionOpen(false)}
          onSuccess={() => setIsIntervencionOpen(false)}
        />
      )}
    </>
  );
}
