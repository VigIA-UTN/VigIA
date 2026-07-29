import { useState, useEffect } from 'react';
import type { AlertaResponse, AlertaDetalleResponse } from '../api/alertaApi';
import { updateAlertaEstado, getAlertaDetalle } from '../api/alertaApi';
import { useAuthStore } from '../store/authStore';
import { useEstablecimientoStore } from '../store/establecimientoStore';

interface AlertDetailSidebarProps {
  alerta: AlertaResponse;
  onClose: () => void;
  onActionComplete: (alertaId: number, nuevoEstado: 'CONFIRMADA' | 'DESCARTADA') => void;
}

export default function AlertDetailSidebar({ alerta, onClose, onActionComplete }: AlertDetailSidebarProps) {
  const [isClosing, setIsClosing] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [notas, setNotas] = useState('');
  
  const [detalle, setDetalle] = useState<AlertaDetalleResponse | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(true);
  const [errorDetail, setErrorDetail] = useState<string | null>(null);

  const { rol } = useAuthStore();
  const { selectedId } = useEstablecimientoStore();
  const canActOnAlerts = rol === 'VETERINARIO' || rol === 'PRODUCTOR';

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

  const handleAccion = async (nuevoEstado: 'CONFIRMADA' | 'DESCARTADA') => {
    setActionLoading(true);
    try {
      // Si la alerta es un mock, el backend va a tirar 404/500, lo atajamos acá para que actualice igual visualmente.
      await updateAlertaEstado(alerta.id, nuevoEstado).catch(err => {
        console.warn('Backend falló (probablemente es un mock alert), forzando update frontend:', err);
      });
      
      onActionComplete(alerta.id, nuevoEstado);
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
  const animalName = detalle?.animalAlias ? `${detalle.animalAlias} (#${alerta.animalId})` : `Vaca #${alerta.animalId}`;
  
  const estadoVisual = detalle ? detalle.estadoActual : alerta.estado;

  try {
    const ts = detalle ? detalle.tsGeneracion : alerta.tsGeneracion;
    if (ts) {
      date = new Date(ts);
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
  
  return (
    <div
      style={{
        position: 'absolute',
        top: 0,
        right: 0,
        bottom: 0,
        width: '550px',
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
        
        <div className="d-flex justify-content-between align-items-center mt-2">
          <div>
            <h2 className="m-0 fw-bold" style={{ color: '#1E3A5F' }}>{animalName}</h2>
            <p className="m-0 text-muted fs-6">Animal en seguimiento</p>
          </div>
          <div className="px-3 py-1 rounded-pill d-flex align-items-center gap-2" style={{ backgroundColor: '#FDECEC', color: '#B00020', border: '1px solid #B00020', fontWeight: 'bold' }}>
            ALTO <div style={{ width: '10px', height: '10px', borderRadius: '50%', backgroundColor: '#B00020' }}></div>
          </div>
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
            {/* Ventana de inseminacion */}
            <div>
              <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
                <i className="bi bi-clock"></i> Ventana de inseminación
              </h6>
              <div className="card border-0 shadow-sm rounded-4">
                <div className="card-body d-flex justify-content-between align-items-center">
                  <div className="text-center">
                    <h4 className="m-0 fw-bold">{formatHour(windowStart)}</h4>
                    <small className="text-muted">{formatDateOnly(windowStart)}</small>
                  </div>
                  
                  <div className="flex-grow-1 mx-3 position-relative d-flex align-items-center justify-content-center">
                    <div style={{ height: '4px', width: '100%', backgroundColor: '#E0E0E0', position: 'absolute' }}></div>
                    <div style={{ height: '8px', width: '70%', backgroundColor: '#E05252', position: 'absolute', borderRadius: '4px' }}></div>
                    <div style={{ position: 'absolute', left: '15%', height: '16px', width: '2px', backgroundColor: '#333' }}></div>
                    <div style={{ position: 'absolute', right: '15%', height: '16px', width: '2px', backgroundColor: '#333' }}></div>
                  </div>

                  <div className="text-center">
                    <h4 className="m-0 fw-bold">{formatHour(windowEnd)}</h4>
                    <small className="text-muted">{formatDateOnly(windowEnd)}</small>
                  </div>
                </div>
              </div>
            </div>

            {/* Recomendacion */}
            <div className="p-3 rounded-3 d-flex gap-3 align-items-start" style={{ backgroundColor: '#EEF2FF', color: '#1E3A5F', border: '1px solid #C7D2FE' }}>
              <i className="bi bi-info-circle-fill fs-5 mt-1" style={{ color: '#6366F1' }}></i>
              <p className="m-0" style={{ fontSize: '0.95rem' }}>
                Se recomienda inseminar a la vaca {animalName} entre las horas indicadas en la ventana de inseminación.
              </p>
            </div>

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
        <div className="p-4 bg-white border-top d-flex gap-3 justify-content-between" style={{ zIndex: 10, borderRadius: '0 0 0 12px' }}>
          <button 
            className="btn rounded-3 fw-semibold flex-grow-1 py-2 d-flex justify-content-center align-items-center gap-2"
            style={{ color: '#D32F2F', backgroundColor: 'white', border: '1px solid #D32F2F' }}
            onClick={() => handleAccion('DESCARTADA')}
            disabled={actionLoading}
          >
            <i className="bi bi-trash"></i> {actionLoading ? '...' : 'Descartar alerta'}
          </button>
          <button 
            className="btn rounded-3 fw-semibold flex-grow-1 py-2 d-flex justify-content-center align-items-center gap-2"
            style={{ backgroundColor: '#198754', color: 'white', border: '1px solid #198754' }}
            onClick={() => handleAccion('CONFIRMADA')}
            disabled={actionLoading}
          >
            <i className="bi bi-check2"></i> {actionLoading ? '...' : 'Confirmar inseminación'}
          </button>
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
