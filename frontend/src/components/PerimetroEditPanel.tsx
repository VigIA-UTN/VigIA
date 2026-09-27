import { useState } from 'react';
import axios from 'axios';
import type { PerimetroResponse, CoordenadaDTO } from '../api/perimetrosApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import MultiSelectRodeos from './MultiSelectRodeos';

interface PerimetroEditPanelProps {
  perimetro: PerimetroResponse;
  rodeos: RodeoLoteResponse[];
  coordenadasEditadas: CoordenadaDTO[];
  onSave: (nombre: string, loteIds: number[], zonaEstricta: boolean, distanciaToleranciaMetros: number, descripcion?: string) => Promise<void>;
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
  const [descripcion, setDescripcion] = useState(perimetro.descripcion || '');
  const [selectedRodeoIds, setSelectedRodeoIds] = useState<number[]>(perimetro.loteIds || []);
  const [isPerimetroEstricto, setIsPerimetroEstricto] = useState(perimetro.zonaEstricta || false);
  const [tolerancia, setTolerancia] = useState(perimetro.distanciaToleranciaMetros || 0);
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
    if (!isPerimetroEstricto && (!Number.isFinite(tolerancia) || tolerancia < 0)) {
      setError('La distancia de tolerancia debe ser un número mayor o igual a 0');
      return;
    }
    setError(null);
    try {
      await onSave(nombre.trim(), selectedRodeoIds, isPerimetroEstricto, tolerancia, descripcion.trim() || undefined);
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
        animation: 'editPanelIn 0.22s ease-out both',
      }}
    >
      <style>{`
        @keyframes editPanelIn {
          from { opacity: 0; transform: translateY(12px); }
          to   { opacity: 1; transform: translateY(0); }
        }
      `}</style>
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
          {perimetro.id === 0 ? 'Nuevo Perímetro' : `Editando ${perimetro.nombre}`}
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

      {/* Edición de Descripción */}
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
          Descripción
        </label>
        <textarea
          value={descripcion}
          onChange={(e) => setDescripcion(e.target.value)}
          rows={2}
          maxLength={500}
          placeholder="Descripción"
          className="form-control"
          style={{
            borderRadius: '8px',
            border: '1px solid #D0D5DD',
            padding: '8px 12px',
            fontSize: '0.9rem',
            resize: 'vertical',
          }}
        />
        <div className="d-flex justify-content-end mt-1">
          <small className="text-muted" style={{ fontSize: '0.75rem' }}>
            {descripcion.length}/500
          </small>
        </div>
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
      {/* Sección Zona estricta */}
      <div className="form-check form-switch m-0">
        <input
          id="perimetro-zona-estricta"
          type="checkbox"
          role="switch"
          className="form-check-input"
          checked={isPerimetroEstricto}
          onChange={(e) => setIsPerimetroEstricto(e.target.checked)}
        />
        <label
          htmlFor="perimetro-zona-estricta"
          className="form-check-label"
          style={{
            fontSize: '0.85rem',
            fontWeight: 600,
            color: '#475467',
          }}
        >
          Zona estricta (sin tolerancia)
        </label>
      </div>
      {/* Sección Distancia de Tolerancia */}
      {!isPerimetroEstricto && (<div>
        <label
          style={{
            display: 'block',
            fontSize: '0.85rem',
            fontWeight: 600,
            color: '#475467',
            marginBottom: '6px',
          }}
        >
          Distancia de tolerancia (metros)
        </label>
        <input
          type="number"
          value={tolerancia}
          onChange={(e) => setTolerancia(Number(e.target.value))}
          className="form-control"
          min={0}
          step="any"
          style={{
            borderRadius: '8px',
            border: '1px solid #D0D5DD',
            padding: '8px 12px',
            fontSize: '0.95rem',
          }}
        />
      </div>)}
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
