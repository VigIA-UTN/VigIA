import { useEffect, useState } from 'react';
import Form from 'react-bootstrap/Form';
import { getAlertasCelo, type AlertaResponse } from '../api/alertaApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';

interface AlertSidebarProps {
  onClose: () => void;
}

export default function AlertSidebar({ onClose }: AlertSidebarProps) {
  const { selectedId } = useEstablecimientoStore();
  const [alertas, setAlertas] = useState<AlertaResponse[]>([]);
  const [loading, setLoading] = useState(false);

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
    return () => { isMounted = false; };
  }, [selectedId]);

  const formatTime = (ts: string) => {
    const date = new Date(ts);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
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
          
          return (
            <div key={alerta.id} className="card shadow-sm border-0 rounded-4" style={{ overflow: 'hidden' }}>
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
          );
        })}
      </div>
    </div>
  );
}
