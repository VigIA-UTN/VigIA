import { useEffect, useRef, useState } from 'react';
import Form from 'react-bootstrap/Form';
import { getAlertasCelo, updateAlertaEstado, type AlertaResponse } from '../api/alertaApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { useAuthStore } from '../store/authStore';
import type { Client, IMessage } from '@stomp/stompjs';

interface AlertSidebarProps {
  onClose: () => void;
}

export default function AlertSidebar({ onClose }: AlertSidebarProps) {
  const { selectedId } = useEstablecimientoStore();
  const { rol } = useAuthStore();
  const [alertas, setAlertas] = useState<AlertaResponse[]>([]);
  const [loading, setLoading] = useState(false);

  // Menú contextual
  const [menuAlertaId, setMenuAlertaId] = useState<number | null>(null);
  const [menuPos, setMenuPos] = useState<{ top: number; left: number } | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const canActOnAlerts = rol === 'VETERINARIO' || rol === 'PRODUCTOR';

  useEffect(() => {
    if (!selectedId) return;
    let isMounted = true;
    const fetchAlertas = async () => {
      setLoading(true);
      try {
        const data = await getAlertasCelo(selectedId);
        if (isMounted) {
          setAlertas(data);
        }
      } catch (error) {
        console.error("Error fetching alertas:", error);
      } finally {
        if (isMounted) setLoading(false);
      }
    };
    fetchAlertas();

    let stompClient: Client | null = null;

    // STOMP WebSocket Connection
    import('@stomp/stompjs').then(({ Client }) => {
      const token = useAuthStore.getState().token;
      
      // Pasamos el token como parámetro en la URL porque la API nativa
      // de WebSockets en los navegadores no permite enviar headers HTTP custom (como Authorization).
      const wsUrl = token 
        ? `ws://localhost:8080/ws-alertas?token=${token}` 
        : 'ws://localhost:8080/ws-alertas';

      stompClient = new Client({
        brokerURL: wsUrl,
        onConnect: () => {
          console.log('Connected to WebSockets');
          stompClient?.subscribe('/topic/alertas', (message: IMessage) => {
            console.log("Mensaje WS recibido:", message.body);
            if (message.body && isMounted) {
              const newAlerta = JSON.parse(message.body) as AlertaResponse;
              setAlertas((prev) => [newAlerta, ...prev]);
            }
          });
        },
        onStompError: (frame) => {
          console.error('Broker reported error: ' + frame.headers['message']);
        },
      });
      stompClient.activate();
    });

    return () => {
      isMounted = false;
      if (stompClient) {
        stompClient.deactivate();
      }
    };
  }, [selectedId]);

  // Cerrar menú al hacer click fuera
  useEffect(() => {
    if (menuAlertaId === null) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuAlertaId(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [menuAlertaId]);

  const formatTime = (ts: string) => {
    const date = new Date(ts);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const handleAlertClick = (alerta: AlertaResponse, e: React.MouseEvent<HTMLDivElement>) => {
    if (!canActOnAlerts || alerta.estado !== 'PENDIENTE') return;
    if (menuAlertaId === alerta.id) {
      setMenuAlertaId(null);
      setMenuPos(null);
      return;
    }
    const rect = (e.currentTarget as HTMLDivElement).getBoundingClientRect();
    setMenuPos({ top: rect.top - 8, left: rect.left + rect.width / 2 });
    setMenuAlertaId(alerta.id);
  };

  const handleAccion = async (alertaId: number, nuevoEstado: 'CONFIRMADA' | 'DESCARTADA') => {
    setActionLoading(true);
    try {
      const updated = await updateAlertaEstado(alertaId, nuevoEstado);
      setAlertas(prev =>
        prev.map(a => a.id === alertaId ? { ...a, estado: updated.estado } : a)
      );
      setMenuAlertaId(null);
    } catch (error) {
      console.error('Error actualizando alerta:', error);
    } finally {
      setActionLoading(false);
    }
  };

  return (
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
        borderLeft: 'none'
      }}
    >
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h4 className="m-0 fw-bold" style={{ color: '#1E3A5F' }}>Alertas</h4>
        <button className="btn btn-link text-secondary p-0" onClick={onClose}>
          <i className="bi bi-chevron-double-left fs-5"></i>
        </button>
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

      <div className="flex-grow-1 overflow-auto pe-2 d-flex flex-column gap-3">
        {loading && <div className="text-center text-muted mt-4">Cargando alertas...</div>}
        
        {!loading && alertas.length === 0 && (
          <div className="text-center text-muted mt-4">No hay alertas registradas.</div>
        )}

        {!loading && alertas.map((alerta) => {
          const isWarning = alerta.estado === 'PENDIENTE';
          const bgColor = isWarning ? 'bg-warning' : 'bg-danger';
          const iconColor = isWarning ? 'text-dark' : 'text-white';
          const iconClass = isWarning ? 'bi-exclamation-triangle-fill' : 'bi-exclamation-octagon-fill';
          const isClickable = canActOnAlerts && isWarning;
          const isMenuOpen = menuAlertaId === alerta.id;

          return (
            <div key={alerta.id}>
              <div
                className={`card shadow-sm border-0 rounded-4 ${isClickable ? 'alerta-clickable' : ''}`}
                style={{
                  overflow: 'hidden',
                  cursor: isClickable ? 'pointer' : 'default',
                  transition: 'transform 0.15s ease, box-shadow 0.15s ease',
                  outline: isMenuOpen ? '2px solid #1E3A5F' : 'none',
                }}
                onClick={(e) => handleAlertClick(alerta, e)}
              >
                <div className="d-flex h-100">
                  <div className={`${bgColor}`} style={{ width: '8px' }}></div>
                  <div className="card-body p-3 d-flex gap-3 w-100">
                    <div 
                      className={`${bgColor} ${iconColor} rounded-3 d-flex align-items-center justify-content-center`} 
                      style={{ width: '45px', height: '45px', flexShrink: 0 }}
                    >
                      <i className={`bi ${iconClass} fs-4`}></i>
                    </div>
                    <div className="w-100">
                      <div className="d-flex justify-content-between align-items-start mb-1">
                        <h6 className="m-0 fw-bold lh-sm">
                          #{alerta.animalId} en {alerta.tipo || 'CELO'}
                        </h6>
                        <small className="text-muted" style={{ fontSize: '0.75rem', whiteSpace: 'nowrap', marginLeft: '8px' }}>
                          {formatTime(alerta.tsGeneracion)}
                        </small>
                      </div>
                      <small className="text-muted lh-sm d-block">
                        Estado de la alerta: {alerta.estado}
                      </small>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Menú de acción con position:fixed para no ser cortado por overflow:auto */}
      {menuAlertaId !== null && menuPos && (
        <div
          ref={menuRef}
          style={{
            position: 'fixed',
            top: menuPos.top,
            left: menuPos.left,
            transform: 'translate(-50%, -100%)',
            backgroundColor: 'white',
            borderRadius: '12px',
            boxShadow: '0 8px 24px rgba(0,0,0,0.22)',
            border: '1.5px solid #e0e0e0',
            padding: '10px',
            zIndex: 9999,
            display: 'flex',
            flexDirection: 'column',
            gap: '8px',
            minWidth: '230px',
            animation: 'fadeInUp 0.15s ease',
          }}
        >
          <p className="m-0 text-center fw-semibold" style={{ fontSize: '0.8rem', color: '#1E3A5F' }}>
            ¿Qué deseas hacer con esta alerta?
          </p>
          <div className="d-flex gap-2">
            <button
              className="btn btn-sm fw-semibold d-flex align-items-center justify-content-center gap-1 flex-grow-1"
              style={{
                backgroundColor: '#198754',
                color: 'white',
                borderRadius: '8px',
                border: 'none',
                padding: '7px 10px',
                fontSize: '0.82rem',
              }}
              disabled={actionLoading}
              onClick={(e) => { e.stopPropagation(); handleAccion(menuAlertaId, 'CONFIRMADA'); }}
            >
              <i className="bi bi-check-circle-fill"></i>
              {actionLoading ? '...' : 'Confirmar'}
            </button>
            <button
              className="btn btn-sm fw-semibold d-flex align-items-center justify-content-center gap-1 flex-grow-1"
              style={{
                backgroundColor: '#dc3545',
                color: 'white',
                borderRadius: '8px',
                border: 'none',
                padding: '7px 10px',
                fontSize: '0.82rem',
              }}
              disabled={actionLoading}
              onClick={(e) => { e.stopPropagation(); handleAccion(menuAlertaId, 'DESCARTADA'); }}
            >
              <i className="bi bi-x-circle-fill"></i>
              {actionLoading ? '...' : 'Descartar'}
            </button>
          </div>
        </div>
      )}

      <style>{`
        .alerta-clickable:hover {
          transform: translateY(-1px);
          box-shadow: 0 6px 16px rgba(0,0,0,0.15) !important;
        }
        @keyframes fadeInUp {
          from { opacity: 0; transform: translate(-50%, calc(-100% + 6px)); }
          to   { opacity: 1; transform: translate(-50%, -100%); }
        }
      `}</style>
    </div>
  );
}
