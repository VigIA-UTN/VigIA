import type { PerimetroResponse } from '../api/perimetrosApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import SidebarCard from './SidebarCard';

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
    <SidebarCard
      severidad="PERIMETRO"
      icono={perimetro.activo ? 'bi-bounding-box-circles' : 'bi-bounding-box'}
      titulo={perimetro.nombre}
      cuerpo={perimetro.descripcion || undefined}
      detalle={nombresRodeos}
      acento={perimetro.activo}
      apagada={!perimetro.activo}
      seleccionada={isEditing}
      onClick={() => onEdit(perimetro)}
      onMouseEnter={() => onHoverStart(perimetro.id)}
      onMouseLeave={onHoverEnd}
      acciones={
        <div className="d-flex align-items-center gap-2">
          <button
            onClick={(e) => { e.stopPropagation(); onEdit(perimetro); }}
            className="btn btn-link p-1 text-secondary"
            title="Editar perímetro"
            style={{ textDecoration: 'none' }}
          >
            <i className="bi bi-pencil fs-5"></i>
          </button>
          <div
            onClick={(e) => { e.stopPropagation(); onToggleActivo(perimetro); }}
            style={{
              width: 24, height: 24, borderRadius: 6, cursor: 'pointer', transition: 'all 0.15s ease',
              border: `2px solid ${perimetro.activo ? '#13D4FB' : '#CBD5E1'}`,
              backgroundColor: perimetro.activo ? '#13D4FB' : '#FFFFFF',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
            title={perimetro.activo ? 'Desactivar perímetro' : 'Activar perímetro'}
          >
            {perimetro.activo && <i className="bi bi-check-lg" style={{ color: '#FFFFFF', fontSize: '1.1rem' }}></i>}
          </div>
        </div>
      }
    />
  );
}
