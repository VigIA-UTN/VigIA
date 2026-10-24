import { useState } from 'react';
import type { PerimetroResponse, CoordenadaDTO } from '../api/perimetrosApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import PerimetroCard from './PerimetroCard';
import PerimetroEditPanel from './PerimetroEditPanel';

interface PerimetroSidebarProps {
  onClose: () => void;
  perimetros: PerimetroResponse[];
  rodeos: RodeoLoteResponse[];
  loading: boolean;
  onToggleActivo: (perimetro: PerimetroResponse) => void;
  onToggleAll: () => void;
  onStartEdit: (perimetro: PerimetroResponse) => void;
  onCancelEdit: () => void;
  onSaveEdit: (nombre: string, loteIds: number[], zonaEstricta: boolean, distanciaToleranciaMetros: number, descripcion?: string) => Promise<void>;
  onDelete: (perimetro: PerimetroResponse) => void;
  onCreateNew: () => void;
  editingPerimetro: PerimetroResponse | null;
  coordenadasEditadas: CoordenadaDTO[];
  onHoverStart: (id: number) => void;
  onHoverEnd: () => void;
  isSaving?: boolean;
}

export default function PerimetroSidebar({
  onClose,
  perimetros,
  rodeos,
  loading,
  onToggleActivo,
  onToggleAll,
  onStartEdit,
  onCancelEdit,
  onSaveEdit,
  onDelete,
  onCreateNew,
  editingPerimetro,
  coordenadasEditadas,
  onHoverStart,
  onHoverEnd,
  isSaving = false,
}: PerimetroSidebarProps) {
  const [isClosing, setIsClosing] = useState(false);
  const [isExiting, setIsExiting] = useState(false);

  const handleCancelWithAnimation = () => {
    setIsExiting(true);
    setTimeout(() => {
      setIsExiting(false);
      onCancelEdit();
    }, 200);
  };

  const handleClose = () => {
    setIsClosing(true);
    setTimeout(() => {
      onClose();
    }, 280);
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
        borderLeft: 'none',
        animation: isClosing ? 'slideOutLeft 0.3s ease-in forwards' : 'slideInLeft 0.3s ease-out forwards',
      }}
    >
      {/* Header del Sidebar */}
      <div className="d-flex justify-content-between align-items-center mb-3">
        <div className="d-flex align-items-center gap-2">
          <h4 className="m-0 fw-bold" style={{ color: '#1E3A5F' }}>Perímetros</h4>
          {/* Switch para activar/desactivar todos los perímetros */}
          <div className="form-check form-switch m-0 p-0 d-flex align-items-center">
            <input
              className="form-check-input m-0"
              type="checkbox"
              role="switch"
              id="flexSwitchCheckDefault"
              checked={perimetros.every((p) => p.activo)}
              onChange={onToggleAll}
            />
          </div>
          {!editingPerimetro && (
            <button
              onClick={onCreateNew}
              className="btn btn-sm py-1 px-2 fw-semibold d-flex align-items-center gap-1"
              style={{
                backgroundColor: '#13D4FB',
                color: '#1E3A5F',
                border: 'none',
                borderRadius: '8px',
                fontSize: '0.8rem',
              }}
              title="Crear un nuevo perímetro"
            >
              <i className="bi bi-plus-lg"></i> Nuevo
            </button>
          )}
        </div>
        <button
          className="btn btn-link text-secondary p-0"
          onClick={editingPerimetro ? handleCancelWithAnimation : handleClose}
          title={editingPerimetro ? 'Volver a perímetros' : 'Cerrar'}
        >
          <i className={`bi ${editingPerimetro ? 'bi-arrow-left' : 'bi-chevron-double-left'} fs-5`}></i>
        </button>
      </div>

      {/* Contenido: Si está en edición muestra el EditPanel, sino el Listado */}
      <div className="flex-grow-1 overflow-auto pe-1 d-flex flex-column gap-3 custom-scrollbar">
        {editingPerimetro ? (
          <div
            style={{
              animation: isExiting ? 'editPanelOut 0.2s ease-in both' : undefined,
            }}
          >
            <PerimetroEditPanel
              perimetro={editingPerimetro}
              rodeos={rodeos}
              coordenadasEditadas={coordenadasEditadas}
              onSave={onSaveEdit}
              onDelete={onDelete}
              onCancel={handleCancelWithAnimation}
              isSaving={isSaving}
            />
          </div>
        ) : (
          <>
            {loading && (
              <div className="text-center text-muted mt-4">
                Cargando perímetros...
              </div>
            )}

            {!loading && perimetros.length === 0 && (
              <div className="text-center text-muted mt-4">
                No hay perímetros configurados en este establecimiento.
              </div>
            )}

            {!loading && perimetros.map((perimetro) => (
              <PerimetroCard
                key={perimetro.id}
                perimetro={perimetro}
                rodeos={rodeos}
                onToggleActivo={onToggleActivo}
                onEdit={onStartEdit}
                onDelete={onDelete}
                onHoverStart={onHoverStart}
                onHoverEnd={onHoverEnd}
                isEditing={false}
              />
            ))}
          </>
        )}
      </div>

      <style>{`
        @keyframes slideInLeft {
          from { opacity: 0; transform: translateX(-20px); }
          to   { opacity: 1; transform: translateX(0); }
        }
        @keyframes editPanelOut {
          from { opacity: 1; transform: translateY(0); }
          to   { opacity: 0; transform: translateY(12px); }
        }
        @keyframes slideOutLeft {
          from { opacity: 1; transform: translateX(0); }
          to   { opacity: 0; transform: translateX(-20px); }
        }
        /* Custom Scrollbar */
        .custom-scrollbar::-webkit-scrollbar {
          width: 10px;
        }
        .custom-scrollbar::-webkit-scrollbar-track {
          background: transparent;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb {
          background-color: #13D4FB;
          border-radius: 10px;
          border: 2px solid transparent;
          background-clip: padding-box;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover {
          background-color: #0A96B1;
        }
      `}</style>
    </div>
  );
}
