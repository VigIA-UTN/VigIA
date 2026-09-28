import { useMemo, useState } from 'react';
import { crearDiagnostico, getDiagnosticosPorAnimal } from '../api/diagnosticoApi';
import { useAuthStore } from '../store/authStore';

interface DiagnosticoSectionProps {
  alertaId: number;
  animalId: number;
  animalAlias?: string;
  animalCaravanaOficial?: string;
  establecimientoNombre?: string;
  /** Mostrar el formulario de carga (solo rol VETERINARIO). El historial siempre se muestra. */
  showForm: boolean;
}

function formatFecha(iso: string) {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '—';
  return d.toLocaleString([], { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export default function DiagnosticoSection({
  alertaId,
  animalId,
  animalAlias,
  animalCaravanaOficial,
  establecimientoNombre,
  showForm,
}: DiagnosticoSectionProps) {
  const email = useAuthStore((s) => s.email);
  const [diag, setDiag] = useState('');
  const [trat, setTrat] = useState('');
  const [error, setError] = useState(false);
  const [version, setVersion] = useState(0);

  const historial = useMemo(
    () => getDiagnosticosPorAnimal(animalId),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `version` forces a re-read of the mock store after a submit
    [animalId, version],
  );

  const handleSubmit = () => {
    if (!diag.trim()) {
      setError(true);
      return;
    }
    crearDiagnostico({
      alertaId,
      animalId,
      animalAlias: animalAlias ?? `Vaca #${animalId}`,
      animalCaravanaOficial: animalCaravanaOficial ?? '',
      establecimientoNombre: establecimientoNombre ?? '',
      diagnostico: diag.trim(),
      tratamiento: trat.trim() || '—',
      veterinarioNombre: email ?? 'Veterinario',
    });
    setVersion((v) => v + 1);
    setDiag('');
    setTrat('');
    setError(false);
  };

  return (
    <>
      {showForm && (
        <div>
          <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
            Cargar diagnóstico
          </h6>
          <div className="card border-0 shadow-sm rounded-4">
            <div className="card-body d-flex flex-column gap-3">
              <label className="d-flex flex-column gap-2">
                <span style={{ fontSize: '0.85rem', color: '#5A6670' }}>Diagnóstico</span>
                <textarea
                  className="form-control rounded-4 diag-input"
                  rows={2}
                  maxLength={200}
                  value={diag}
                  onChange={(e) => { setDiag(e.target.value); setError(false); }}
                  style={{ resize: 'none', borderColor: error ? '#FB5151' : undefined, borderWidth: error ? 2 : undefined }}
                />
                {error && <span style={{ color: '#D50000', fontSize: '0.85rem' }}>Ingresá el diagnóstico.</span>}
              </label>
              <label className="d-flex flex-column gap-2">
                <span className="d-flex justify-content-between" style={{ fontSize: '0.85rem', color: '#5A6670' }}>
                  <span>Indicación o tratamiento</span>
                  <span>{trat.length}/500</span>
                </span>
                <textarea
                  className="form-control rounded-4 diag-input"
                  rows={3}
                  maxLength={500}
                  value={trat}
                  onChange={(e) => setTrat(e.target.value)}
                  style={{ resize: 'none' }}
                />
              </label>
              <button
                className="btn rounded-3 fw-semibold py-2"
                style={{ backgroundColor: '#94C11E', color: 'white', border: '1px solid #94C11E' }}
                onClick={handleSubmit}
              >
                Registrar diagnóstico
              </button>
            </div>
          </div>
        </div>
      )}

      <div>
        <div className="d-flex justify-content-between align-items-baseline mb-3">
          <h6 className="fw-bold m-0 d-flex align-items-center gap-2" style={{ color: '#1E3A5F' }}>
            Historial clínico
          </h6>
        </div>
        {historial.length === 0 ? (
          <div className="card border-0 shadow-sm rounded-4">
            <div className="card-body">
              <p className="m-0" style={{ fontSize: '0.95rem', color: '#5A6670' }}>
                Todavía no hay diagnósticos registrados para este animal.
              </p>
            </div>
          </div>
        ) : (
          <div className="d-flex flex-column gap-2">
            {historial.map((h) => (
              <div key={h.id} className="card border-0 shadow-sm rounded-4">
                <div className="card-body d-flex flex-column gap-1">
                  <small style={{ color: '#5A6670' }}>{formatFecha(h.fechaRegistro)} · {h.veterinarioNombre}</small>
                  <span style={{ fontSize: '1.05rem', fontWeight: 500, color: '#1E3A5F' }}>{h.diagnostico}</span>
                  <p className="m-0" style={{ fontSize: '0.95rem', color: '#263238' }}>{h.tratamiento}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <style>{`
        .diag-input {
          border: 1px solid #1E3A5F;
          box-shadow: none;
        }
        .diag-input:focus {
          outline: none;
          box-shadow: none;
          border-color: #94C11E;
          border-width: 2px;
        }
      `}</style>
    </>
  );
}
