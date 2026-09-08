import type { PerimetroResponse } from '../api/perimetrosApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';

interface PerimetroCardProps {
  perimetro: PerimetroResponse;
  rodeos: RodeoLoteResponse[];
  onToggleActivo: (perimetro: PerimetroResponse) => void;
  onEdit: (perimetro: PerimetroResponse) => void;
  onHoverStart: (perimetroId: number) => void;
  onHoverEnd: () => void;
  isEditing?: boolean;
}

export default function PerimetroCard({
  perimetro,
  rodeos,
  onToggleActivo,
  onEdit,
  onHoverStart,
  onHoverEnd,
  isEditing = false,
}: PerimetroCardProps) {
  // Obtener nombres de los rodeos asignados
  const nombresRodeos = perimetro.loteIds && perimetro.loteIds.length > 0
    ? perimetro.loteIds
        .map(id => rodeos.find(r => r.id === id)?.nombre)
        .filter(Boolean)
        .join(', ')
    : 'Sin rodeos asignados';

  return (
    <div
      onClick={() => onEdit(perimetro)}
      onMouseEnter={() => onHoverStart(perimetro.id)}
      onMouseLeave={onHoverEnd}
      className="card border-0 rounded-4 perimetro-card-clickable"
      style={{
        overflow: 'hidden',
        cursor: 'pointer',
        transition: 'transform 0.15s ease, box-shadow 0.15s ease, background-color 0.15s ease',
        boxShadow: isEditing ? '0 0 0 2px #13D4FB' : !perimetro.activo ? 'none' : '0 1px 3px rgba(0,0,0,0.08)',
        backgroundColor: isEditing ? '#E2E8F0' : !perimetro.activo ? '#F7FAFC' : 'white',
        opacity: !perimetro.activo ? 0.75 : 1,
      }}
    >
      <div className="d-flex h-100">
        {/* Tira lateral indicadora de estado activo */}
        {perimetro.activo && (
          <div style={{ width: '6px', backgroundColor: '#13D4FB', flexShrink: 0 }}></div>
        )}

        <div className="card-body p-3 d-flex gap-3 w-100 align-items-center">
          {/* Ícono de perímetro */}
          <div
            className="rounded-3 d-flex align-items-center justify-content-center"
            style={{
              width: '45px',
              height: '45px',
              flexShrink: 0,
              backgroundColor: perimetro.activo ? '#E0F7FE' : '#EDF2F7',
            }}
          >
            <i
              className={`bi ${perimetro.activo ? 'bi-bounding-box-circles' : 'bi-bounding-box'} fs-4`}
              style={{ color: perimetro.activo ? '#0A96B1' : '#A0AEC0' }}
            ></i>
          </div>

          {/* Información del Perímetro */}
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="d-flex justify-content-between align-items-start mb-1">
              <h6
                className={`m-0 fw-bold lh-sm ${!perimetro.activo ? 'text-muted' : ''}`}
                style={{
                  fontSize: '0.9rem',
                  color: perimetro.activo ? '#1D2939' : undefined,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {perimetro.nombre}
              </h6>
            </div>
            {perimetro.descripcion && (
              <small
                className="text-muted lh-sm d-block mb-1"
                style={{
                  fontSize: '0.75rem',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
                title={perimetro.descripcion}
              >
                {perimetro.descripcion}
              </small>
            )}
            <small
              className="text-muted lh-sm d-block"
              style={{
                fontSize: '0.8rem',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {nombresRodeos}
            </small>
          </div>

          {/* Botón de Editar y Checkbox de Activación */}
          <div className="d-flex align-items-center gap-2" style={{ flexShrink: 0 }}>
            {/* Lápiz de edición */}
            <button
              onClick={(e) => {
                e.stopPropagation();
                onEdit(perimetro);
              }}
              className="btn btn-link p-1 text-secondary"
              title="Editar perímetro"
              style={{
                borderRadius: '6px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                textDecoration: 'none',
              }}
            >
              <i className="bi bi-pencil fs-5"></i>
            </button>

            {/* Checkbox de activación */}
            <div
              onClick={(e) => {
                e.stopPropagation();
                onToggleActivo(perimetro);
              }}
              style={{
                width: '24px',
                height: '24px',
                borderRadius: '6px',
                border: perimetro.activo ? '2px solid #13D4FB' : '2px solid #CBD5E1',
                backgroundColor: perimetro.activo ? '#13D4FB' : '#FFFFFF',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
              title={perimetro.activo ? 'Desactivar perímetro' : 'Activar perímetro'}
            >
              {perimetro.activo && (
                <i className="bi bi-check-lg" style={{ color: '#FFFFFF', fontSize: '1.1rem', strokeWidth: 2 }}></i>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
