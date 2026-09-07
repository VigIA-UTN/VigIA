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
      onMouseEnter={() => onHoverStart(perimetro.id)}
      onMouseLeave={onHoverEnd}
      className="d-flex align-items-center justify-content-between"
      style={{
        backgroundColor: '#E0E0E0',
        borderRadius: '12px',
        padding: '14px 16px',
        boxShadow: '0 2px 5px rgba(0, 0, 0, 0.08)',
        transition: 'all 0.2s ease',
        border: isEditing ? '2px solid #13D4FB' : '2px solid transparent',
      }}
    >
      {/* Información del Perímetro */}
      <div style={{ flex: 1, minWidth: 0, paddingRight: '12px' }}>
        <h5
          style={{
            margin: 0,
            fontSize: '1.05rem',
            fontWeight: 700,
            color: '#1D2939',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {perimetro.nombre}
        </h5>
        <div
          style={{
            fontSize: '0.85rem',
            color: '#475467',
            marginTop: '4px',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {nombresRodeos}
        </div>
      </div>

      {/* Botón de Editar y Checkbox de Activación */}
      <div className="d-flex align-items-center gap-3">
        {/* Lápiz de edición */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            onEdit(perimetro);
          }}
          className="btn btn-link p-1 text-dark"
          title="Editar perímetro"
          style={{
            color: '#1D2939',
            borderRadius: '6px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            textDecoration: 'none',
          }}
        >
          <i className="bi bi-pencil-fill fs-5"></i>
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
            borderRadius: '4px',
            border: perimetro.activo ? '2px solid #13D4FB' : '2px solid #667085',
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
  );
}
