import { useState, useEffect } from 'react';
import type { AnimalMapa } from '../api/animalesApi';
import { getAnimalById, type AnimalResponse } from '../api/animalesApi';
import type { AlertaResponse } from '../api/alertaApi';
import AlertNotificationCard from './AlertNotificationCard';

interface AnimalDetailSidebarProps {
  animalMapa: AnimalMapa;
  onClose: () => void;
  onAlertClick: (alerta: AlertaResponse) => void;
  onLocateClick?: () => void;
}

export default function AnimalDetailSidebar({ animalMapa, onClose, onAlertClick, onLocateClick }: AnimalDetailSidebarProps) {
  const [isClosing, setIsClosing] = useState(false);
  const [animalDetail, setAnimalDetail] = useState<AnimalResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;
    const fetchAnimal = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await getAnimalById(animalMapa.id);
        if (isMounted) setAnimalDetail(data);
      } catch (err) {
        if (isMounted) setError('Error al cargar la información del animal.');
      } finally {
        if (isMounted) setLoading(false);
      }
    };
    fetchAnimal();

    return () => {
      isMounted = false;
    };
  }, [animalMapa.id]);

  const handleClose = () => {
    setIsClosing(true);
    setTimeout(() => {
      onClose();
    }, 280);
  };

  const animalName = animalMapa.identificador || `Animal #${animalMapa.id}`;
  const hasAlertas = animalMapa.alertasAnimal.length > 0;
  
  // Status dot color
  let statusColor = '#38A169'; // Green (ACTIVO)
  if (animalMapa.sinDatosRecientes) {
    statusColor = '#718096'; // Gray
  } else if (hasAlertas) {
    // If it has alerts, pick color based on max severity
    if (animalMapa.alertasAnimal.some(a => a.severidad === 'ALTA' || a.tipo !== 'CELO')) {
      statusColor = '#E53E3E'; // Red
    } else {
      statusColor = '#D69E2E'; // Yellow/Orange
    }
  }

  const formatFecha = (iso: string | undefined | null) => {
    if (!iso) return '--';
    const date = new Date(iso);
    return date.toLocaleDateString();
  };

  const formatHora = (iso: string | null) => {
    if (!iso) return '--:--';
    const date = new Date(iso.endsWith('Z') ? iso : iso + 'Z');
    return date.toLocaleString();
  };

  return (
    <div
      style={{
        position: 'absolute',
        top: 0,
        right: 0,
        bottom: 0,
        width: '400px',
        backgroundColor: '#F7FAFC',
        zIndex: 1000,
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
      <div className="p-4 bg-white border-bottom" style={{ zIndex: 10, borderRadius: '12px 0 0 0' }}>
        <div className="d-flex justify-content-between align-items-start mb-2">
          <button className="btn btn-link text-secondary p-0 border-0" onClick={handleClose}>
            <i className="bi bi-x-lg fs-5"></i>
          </button>
        </div>
        
        <div className="d-flex justify-content-between align-items-center mt-2">
          <div>
            <h2 className="m-0 fw-bold" style={{ color: '#1E3A5F' }}>{animalName}</h2>
            <p className="m-0 text-muted fs-6">#{animalMapa.id}</p>
          </div>
          <div style={{ width: '16px', height: '16px', borderRadius: '50%', backgroundColor: statusColor, border: '2px solid white', boxShadow: '0 0 0 1px #CBD5E0' }}></div>
        </div>
      </div>

      <div className="p-4 d-flex flex-column gap-3 flex-grow-1 custom-scrollbar" style={{ overflowY: 'auto' }}>
        
        {/* Información del animal */}
        <div className="card border-0 rounded-4 shadow-sm" style={{ backgroundColor: '#EDF2F7' }}>
          <div className="card-body p-3">
            <h6 className="fw-bold mb-3 d-flex align-items-center gap-2 text-secondary" style={{ fontSize: '0.85rem', textTransform: 'uppercase' }}>
              Información del animal
            </h6>
            
            {loading ? (
              <div className="text-center text-muted py-3">Cargando detalles...</div>
            ) : error ? (
              <div className="text-danger text-center py-3">{error}</div>
            ) : (
              <div className="d-flex flex-column gap-2">
                <div className="d-flex justify-content-between">
                  <span className="text-muted">Raza</span>
                  <span className="fw-semibold">{animalDetail?.raza || '--'}</span>
                </div>
                <div className="d-flex justify-content-between">
                  <span className="text-muted">Lote/Rodeo</span>
                  <span className="fw-semibold">{animalDetail?.rodeoLoteNombre || '--'}</span>
                </div>
                <div className="d-flex justify-content-between">
                  <span className="text-muted">Nacimiento</span>
                  <span className="fw-semibold">{formatFecha(animalDetail?.fechaNacimiento)}</span>
                </div>
                <div className="d-flex justify-content-between">
                  <span className="text-muted">Estado Reproductivo</span>
                  <span className="fw-semibold">{animalDetail?.estadoReproductivo?.replace(/_/g, ' ') || '--'}</span>
                </div>
              </div>
            )}
            
            <hr className="my-3 text-secondary" />
            
            <div 
              className="d-flex justify-content-between align-items-center p-2 rounded-3" 
              style={{ cursor: 'pointer', transition: 'background-color 0.2s' }}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#E2E8F0'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
              onClick={onLocateClick}
            >
              <span className="text-muted d-flex align-items-center gap-1">
                <i className="bi bi-geo-alt-fill text-primary"></i> Última posición
              </span>
              <span className="fw-bold text-dark" style={{ fontSize: '0.9rem' }}>
                {formatHora(animalMapa.tsUltimaPosicion)}
              </span>
            </div>
            {animalMapa.sinDatosRecientes && (
              <div className="mt-2 text-end">
                <span className="badge bg-danger">Sin datos recientes</span>
              </div>
            )}
          </div>
        </div>

        {/* Alertas */}
        <div>
          <h6 className="fw-bold mt-2 mb-3 text-secondary" style={{ fontSize: '0.85rem', textTransform: 'uppercase' }}>
            Alertas recientes
          </h6>
          
          <div className="d-flex flex-column gap-2">
            {animalMapa.alertasAnimal.length > 0 ? (
              animalMapa.alertasAnimal.map((alerta) => (
                <AlertNotificationCard
                  key={alerta.id}
                  alerta={alerta}
                  onClick={onAlertClick}
                  isUnread={alerta.estado === 'PENDIENTE'}
                />
              ))
            ) : (
              <div className="text-center text-muted py-4 bg-white rounded-4 border">
                <i className="bi bi-check-circle text-success fs-2 mb-2 d-block"></i>
                Sin alertas activas
              </div>
            )}
          </div>
        </div>

      </div>

      <style>{`
        @keyframes slideInRight {
          from { opacity: 0; transform: translateX(20px); }
          to   { opacity: 1; transform: translateX(0); }
        }
        @keyframes slideOutRight {
          from { opacity: 1; transform: translateX(0); }
          to   { opacity: 0; transform: translateX(20px); }
        }
        .custom-scrollbar::-webkit-scrollbar {
          width: 8px;
        }
        .custom-scrollbar::-webkit-scrollbar-track {
          background: transparent;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb {
          background-color: #CBD5E0;
          border-radius: 4px;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover {
          background-color: #A0AEC0;
        }
      `}</style>
    </div>
  );
}
