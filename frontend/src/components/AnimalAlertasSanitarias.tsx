import { useEffect, useState } from 'react';
import { Spinner, Button } from 'react-bootstrap';
import { getAlertasSanitariasAnimal } from '../api/alertaApi';
import type { AlertaResponse } from '../api/alertaApi';
import AlertDetailSidebar from './AlertDetailSidebar';
import { SeveridadBadge } from './SanitarioAlertDetail';

function formatFecha(iso: string) {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '—';
  return d.toLocaleString('es-AR', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

interface Props {
  animalId: number;
  establecimientoId: number;
  onOpenIntervencion: (alerta: AlertaResponse) => void;
}

/**
 * Sección "Alertas sanitarias" de la ficha del animal: lista los desvíos
 * sanitarios detectados para el animal y abre la vista de detalle
 * ({@link AlertDetailSidebar}, que ramifica a la maqueta sanitaria).
 */
export default function AnimalAlertasSanitarias({ animalId, establecimientoId, onOpenIntervencion }: Props) {
  const [alertas, setAlertas] = useState<AlertaResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<AlertaResponse | null>(null);

  useEffect(() => {
    let mounted = true;
    const fetchAlertas = async () => {
      setLoading(true);
      try {
        const d = await getAlertasSanitariasAnimal(establecimientoId, animalId);
        if (mounted) setAlertas(d);
      } catch {
        if (mounted) setAlertas([]);
      } finally {
        if (mounted) setLoading(false);
      }
    };
    fetchAlertas();
    return () => { mounted = false; };
  }, [animalId, establecimientoId]);

  const handleActionComplete = (alertaId: number, nuevoEstado: 'CONFIRMADA' | 'DESCARTADA') => {
    setAlertas((prev) => prev.map((a) => (a.id === alertaId ? { ...a, estado: nuevoEstado } : a)));
    setSelected(null);
  };

  return (
    <>
      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 4px rgba(0,0,0,0.07)', padding: '1.5rem', marginBottom: '1.5rem' }}>
        <h5 className="d-flex align-items-center gap-2" style={{ margin: 0, fontSize: '1.05rem', fontWeight: 700, color: '#1E3A5F' }}>
          <i className="bi bi-heart-pulse" /> Alertas sanitarias
        </h5>
        <p style={{ margin: '2px 0 1rem', fontSize: '0.8rem', color: '#A0AEC0' }}>
          Desvíos de comportamiento detectados para este animal
        </p>

        {loading ? (
          <div className="text-center py-3">
            <Spinner animation="border" size="sm" style={{ color: '#1E3A5F' }} />
          </div>
        ) : alertas.length === 0 ? (
          <p className="text-muted mb-0" style={{ fontSize: '0.9rem' }}>
            Sin alertas sanitarias registradas para este animal.
          </p>
        ) : (
          <div className="d-flex flex-column gap-2">
            {alertas.map((a) => (
              <div
                key={a.id}
                className="d-flex justify-content-between align-items-center flex-wrap gap-2 p-2 ps-3 rounded-3"
                style={{ border: '1px solid #E2E8F0' }}
              >
                <div className="d-flex align-items-center gap-3" style={{ minWidth: 0 }}>
                  <SeveridadBadge severidad={a.severidad ?? null} size="sm" />
                  <div style={{ minWidth: 0 }}>
                    <span className="fw-semibold d-block text-break" style={{ color: '#1E3A5F', fontSize: '0.9rem' }}>
                      {formatFecha(a.tsGeneracion)}
                    </span>
                    <small className="text-muted">Estado: {a.estado}</small>
                  </div>
                </div>
                <Button variant="outline-primary" size="sm" className="flex-shrink-0 ms-auto" onClick={() => setSelected(a)}>
                  <i className="bi bi-eye" /> Ver detalle
                </Button>
              </div>
            ))}
          </div>
        )}
      </div>

      {selected && (
        <AlertDetailSidebar
          alerta={selected}
          mode="fixed"
          onClose={() => setSelected(null)}
          onActionComplete={handleActionComplete}
          onOpenIntervencion={() => {
            onOpenIntervencion(selected);
          }}
        />
      )}
    </>
  );
}
