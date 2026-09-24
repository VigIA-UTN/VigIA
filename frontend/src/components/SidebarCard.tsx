import type { ReactNode, HTMLAttributes } from 'react';

export type Severidad = 'ALTA' | 'MEDIA' | 'BAJA';

// Rampa VigIA: barra / disco / glifo. Perímetro usa la rampa cian.
const SEVERIDAD = {
  BAJA: { bar: '#00BA4A', bg: '#E0F6EA', fg: '#00753B' },
  MEDIA: { bar: '#FFC700', bg: '#FFF4D1', fg: '#8A6A00' },
  ALTA: { bar: '#D50000', bg: '#FDE6E6', fg: '#B00000' },
  PERIMETRO: { bar: '#13D4FB', bg: '#E0F7FE', fg: '#0A96B1' },
};
const APAGADO = { bg: '#EDF2F7', fg: '#A0AEC0' };

export interface Categoria {
  /** Color de categoría, consistente con las tarjetas de resumen del mapa. */
  color: string;
  /** Clase bootstrap-icons para el badge de categoría. */
  icono: string;
  /** Nombre de categoría, usado como accesible/title del badge. */
  label: string;
}

interface SidebarCardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  severidad?: Severidad | 'PERIMETRO';
  icono: string; // clase bootstrap-icons
  titulo: string;
  cuerpo?: string | ReactNode;
  detalle?: string;
  timestamp?: string;
  /** Muestra la barra de acento (alerta no leída / perímetro activo). */
  acento?: boolean;
  /** Estado resuelto/inactivo: tinte neutro y opacidad reducida. */
  apagada?: boolean;
  seleccionada?: boolean;
  acciones?: ReactNode;
  /** Badge de categoría (reproductivo/sanitario/geoespacial) superpuesto al ícono. */
  categoria?: Categoria;
}

const ellipsis = { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } as const;

export default function SidebarCard({
  severidad = 'MEDIA',
  icono,
  titulo,
  cuerpo,
  detalle,
  timestamp,
  acento = false,
  apagada = false,
  seleccionada = false,
  acciones,
  categoria,
  ...rest
}: SidebarCardProps) {
  const c = SEVERIDAD[severidad] ?? SEVERIDAD.MEDIA;
  const disco = apagada ? APAGADO : c;

  return (
    <>
      <div
        {...rest}
        className={`sidebar-card${seleccionada ? ' is-selected' : ''}`}
        style={{
          display: 'flex',
          flexShrink: 0,
          borderRadius: 12,
          overflow: 'hidden',
          cursor: 'pointer',
          transition: 'box-shadow 0.15s ease, background-color 0.15s ease',
          backgroundColor: apagada ? '#F7FAFC' : '#fff',
          opacity: apagada ? 0.75 : 1,
          boxShadow: seleccionada
            ? 'inset 0 0 0 2px #1E3A5F'
            : apagada ? 'none' : '0 1px 3px rgba(0,0,0,.08)',
        }}
      >
        {acento && <div style={{ width: 6, flexShrink: 0, backgroundColor: c.bar }} />}
        <div style={{ display: 'flex', gap: 12, alignItems: 'center', padding: 12, flex: 1, minWidth: 0 }}>
          <div style={{ position: 'relative', flexShrink: 0 }}>
            <div
              style={{
                width: 45, height: 45, borderRadius: 12,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                backgroundColor: disco.bg, color: disco.fg,
              }}
            >
              <i className={`bi ${icono} fs-4`} />
            </div>
            {categoria && (
              <div
                title={categoria.label}
                aria-label={categoria.label}
                style={{
                  position: 'absolute', bottom: -4, right: -4,
                  width: 18, height: 18, borderRadius: '50%',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  backgroundColor: apagada ? '#A0AEC0' : categoria.color,
                  border: '2px solid #fff',
                }}
              >
                <i className={`bi ${categoria.icono}`} style={{ fontSize: 9, color: '#fff' }} />
              </div>
            )}
          </div>
          <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <div
              style={{
                fontFamily: "'Outfit', sans-serif", fontWeight: 600, fontSize: 14, lineHeight: 1.15,
                color: apagada ? '#667085' : '#1D2939', ...ellipsis,
              }}
              title={titulo}
            >
              {titulo}
            </div>
            {(cuerpo || timestamp) && (
              <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', fontFamily: "'Inter', sans-serif" }}>
                <div style={{ flex: 1, minWidth: 0, fontSize: 12.5, lineHeight: 1.35, color: '#667085', ...(detalle ? ellipsis : {}) }}>
                  {cuerpo}
                </div>
                {timestamp && (
                  <div style={{ fontSize: 11, color: '#98A2B3', whiteSpace: 'nowrap', flexShrink: 0 }}>{timestamp}</div>
                )}
              </div>
            )}
            {detalle && (
              <div style={{ fontFamily: "'Inter', sans-serif", fontSize: 12.5, color: '#667085', ...ellipsis }}>{detalle}</div>
            )}
          </div>
          {acciones && <div style={{ flexShrink: 0, display: 'flex', alignItems: 'center' }}>{acciones}</div>}
        </div>
      </div>
      <style>{`.sidebar-card:hover { background-color: #F8F9FC !important; }
.sidebar-card:not(.is-selected):hover { box-shadow: 0 4px 10px rgba(0,0,0,.10) !important; }`}</style>
    </>
  );
}
