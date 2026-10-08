import { useEffect, useState } from 'react';
import { Card, OverlayTrigger, Spinner, Tooltip } from 'react-bootstrap';
import { getIndicadoresSalud, type EstadoIndicador, type IndicadorSaludResponse } from '../api/animalesApi';

const POLL_INTERVAL_MS = 30_000;

const ESTADO_STYLE: Record<EstadoIndicador, { bg: string; fg: string; dot: string | null; label: string }> = {
  VERDE: { bg: '#C6F6D5', fg: '#276749', dot: '#276749', label: 'Normal' },
  AMARILLO: { bg: '#FEFCBF', fg: '#B7791F', dot: '#B7791F', label: 'Atención' },
  ROJO: { bg: '#FED7D7', fg: '#C53030', dot: '#C53030', label: 'Alerta' },
  'SIN DATOS': { bg: '#EDF2F7', fg: '#4A5568', dot: null, label: 'Sin datos' },
};

function IndicadorChip({ indicador }: { indicador: IndicadorSaludResponse }) {
  const s = ESTADO_STYLE[indicador.estado] ?? ESTADO_STYLE['SIN DATOS'];

  return (
    <OverlayTrigger
      placement="top"
      trigger={['hover', 'focus']}
      overlay={<Tooltip>{indicador.descripcion}</Tooltip>}
    >
      <div
        tabIndex={0}
        aria-label={`${indicador.nombre}: ${s.label}. ${indicador.descripcion}`}
        className="d-inline-flex align-items-center gap-2 p-2 ps-3 rounded-3"
        style={{ border: `1px solid ${s.fg}`, background: '#fff', cursor: 'default' }}
      >
        <span className="fw-semibold" style={{ color: '#1E3A5F', fontSize: '0.9rem' }}>
          {indicador.nombre}
        </span>
        <span
          className="d-inline-flex align-items-center gap-2"
          style={{ padding: '2px 10px', borderRadius: 999, background: s.bg, color: s.fg, fontWeight: 700, fontSize: '0.78rem' }}
        >
          {s.label}
          {s.dot && <span style={{ width: 8, height: 8, borderRadius: '50%', background: s.dot }} />}
        </span>
      </div>
    </OverlayTrigger>
  );
}

interface Props {
  animalId: number;
}

/**
 * Sección "Indicadores de salud" de la ficha del animal: consume
 * GET /animales/{id}/indicadores-salud y hace polling para reflejar
 * nuevos datos de caravana sin recargar la página.
 */
export default function AnimalIndicadoresSalud({ animalId }: Props) {
  const [indicadores, setIndicadores] = useState<IndicadorSaludResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    const loadingRef = { current: true };

    const fetchIndicadores = async () => {
      if (loadingRef.current) setLoading(true);
      try {
        const data = await getIndicadoresSalud(animalId);
        if (mounted) setIndicadores(data.indicadores);
      } catch {
        if (mounted) setIndicadores([]);
      } finally {
        if (mounted) setLoading(false);
        loadingRef.current = false;
      }
    };

    fetchIndicadores();
    const interval = setInterval(fetchIndicadores, POLL_INTERVAL_MS);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, [animalId]);

  return (
    <Card className="shadow-sm border-0 animal-card mb-4">
      <Card.Body>
        <h4 className="section-title mb-3">Indicadores de Salud</h4>

        {loading ? (
          <div className="text-center py-3">
            <Spinner animation="border" size="sm" style={{ color: '#1E3A5F' }} />
          </div>
        ) : indicadores.length === 0 ? (
          <p className="text-muted text-center py-3 mb-0">Sin indicadores de salud disponibles.</p>
        ) : (
          <div className="d-flex flex-wrap gap-2">
            {indicadores.map((ind) => (
              <IndicadorChip key={ind.nombre} indicador={ind} />
            ))}
          </div>
        )}
      </Card.Body>
    </Card>
  );
}
