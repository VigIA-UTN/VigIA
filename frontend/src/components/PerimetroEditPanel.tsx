import { useState } from 'react';
import axios from 'axios';
import type { PerimetroResponse, CoordenadaDTO } from '../api/perimetrosApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import MultiSelectRodeos from './MultiSelectRodeos';

interface PerimetroEditPanelProps {
  perimetro: PerimetroResponse;
  rodeos: RodeoLoteResponse[];
  coordenadasEditadas: CoordenadaDTO[];
  onSave: (nombre: string, loteIds: number[]) => Promise<void>;
  onCancel: () => void;
  isSaving?: boolean;
}

export default function PerimetroEditPanel({
  perimetro,
  rodeos,
  coordenadasEditadas,
  onSave,
  onCancel,
  isSaving = false,
}: PerimetroEditPanelProps) {
  const [nombre, setNombre] = useState(perimetro.nombre);
  const [selectedRodeoIds, setSelectedRodeoIds] = useState<number[]>(perimetro.loteIds || []);
  const [error, setError] = useState<string | null>(null);

  const handleFinish = async () => {
    if (!nombre.trim()) {
      setError('El nombre del perímetro no puede estar vacío');
      return;
    }
    if (coordenadasEditadas.length < 3) {
      setError('El perímetro debe tener al menos 3 puntos');
      return;
    }
    setError(null);
    try {
      await onSave(nombre, selectedRodeoIds);
    } catch (e: unknown) {
      if (axios.isAxiosError(e) && e.response?.data) {
        setError(typeof e.response.data === 'string' ? e.response.data : 'Error guardando perímetro');
      } else if (e instanceof Error) {
        setError(e.message);
      } else {
        setError('Error guardando perímetro');
      }
    }
  };

  return (
    <div
      style={{
        backgroundColor: '#E8ECEF',
        borderRadius: '16px',
        padding: '20px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.12)',
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
      }}
    >
      {/* Título de Edición */}
      <div>
        <h4
          style={{
            margin: 0,
            fontSize: '1.25rem',
            fontWeight: 800,
            color: '#1E293B',
          }}
        >
          Editando {perimetro.nombre}
        </h4>
      </div>

      {/* Edición de Nombre */}
      <div>
        <label
          style={{
            display: 'block',
            fontSize: '0.85rem',
            fontWeight: 600,
            color: '#475467',
            marginBottom: '6px',
          }}
        >
          Nombre del Perímetro
        </label>
        <input
          type="text"
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
          className="form-control"
          style={{
            borderRadius: '8px',
            border: '1px solid #D0D5DD',
            padding: '8px 12px',
            fontSize: '0.95rem',
          }}
        />
      </div>

      {/* Sección Rodeos Asignados */}
      <div>
        <label
          style={{
            display: 'block',
            fontSize: '0.85rem',
            fontWeight: 600,
            color: '#475467',
            marginBottom: '6px',
          }}
        >
          Rodeos asignados
        </label>
        <MultiSelectRodeos
          rodeos={rodeos}
          selectedIds={selectedRodeoIds}
          onChange={setSelectedRodeoIds}
        />
      </div>

      {/* Tips / Instrucciones de Edición en el Mapa */}
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: '10px',
          padding: '12px 14px',
          fontSize: '0.82rem',
          color: '#334155',
          border: '1px dashed #CBD5E1',
          display: 'flex',
          flexDirection: 'column',
          gap: '6px',
        }}
      >
        <div className="d-flex align-items-center gap-2">
          <i className="bi bi-arrows-move text-primary"></i>
          <span>Arrastra los puntos circulares para reubicarlos.</span>
        </div>
        <div className="d-flex align-items-center gap-2">
          <i className="bi bi-plus-circle-fill text-success"></i>
          <span>Haz click en el mapa para agregar un nuevo punto.</span>
        </div>
        <div className="d-flex align-items-center gap-2">
          <i className="bi bi-trash3-fill text-danger"></i>
          <span>Click derecho o click en un punto para eliminarlo.</span>
        </div>
        <div className="text-muted mt-1" style={{ fontSize: '0.78rem' }}>
          Vértices actuales: <strong>{coordenadasEditadas.length}</strong> (mínimo 3)
        </div>
      </div>

      {error && (
        <div
          className="alert alert-danger p-2 mb-0"
          style={{ fontSize: '0.85rem', borderRadius: '8px' }}
        >
          {error}
        </div>
      )}

      {/* Botones Terminado y Cancelar */}
      <div className="d-flex flex-column gap-2 mt-2">
        <button
          onClick={handleFinish}
          disabled={isSaving}
          className="btn fw-bold w-100 py-2"
          style={{
            backgroundColor: '#94C11E',
            color: '#FFFFFF',
            border: 'none',
            borderRadius: '10px',
            fontSize: '1rem',
            boxShadow: '0 2px 6px rgba(148, 193, 30, 0.4)',
            transition: 'all 0.15s ease',
          }}
        >
          {isSaving ? 'Guardando...' : 'Terminado'}
        </button>
        <button
          onClick={onCancel}
          disabled={isSaving}
          className="btn btn-outline-secondary w-100 py-2 fw-semibold"
          style={{
            borderRadius: '10px',
            fontSize: '1rem',
            borderColor: '#94A3B8',
            color: '#334155',
          }}
        >
          Cancelar
        </button>
      </div>
    </div>
  );
}
