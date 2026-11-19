import { HEAT_GRADIENT } from './types';

// sorted numerically: the integer-like key "1" would otherwise come first and collapse the gradient to one color
const stops = Object.entries(HEAT_GRADIENT)
    .sort(([a], [b]) => Number(a) - Number(b))
    .map(([offset, color]) => `${color} ${Math.round(Number(offset) * 100)}%`)
    .join(', ');

/** `perimetro` adds the key for the highlighted rodeo perimeter. */
export default function HeatmapLegend({ dark = false, perimetro = false }: { dark?: boolean; perimetro?: boolean }) {
    const text = dark ? '#7A9CC0' : '#718096';
    return (
        // 160px si hay lugar; en mobile se achica para que "Restablecer" (al lado) no se salga del panel
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, flex: '0 1 160px', minWidth: 0 }}>
            <div style={{ height: 8, borderRadius: 999, background: `linear-gradient(90deg, ${stops})` }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, fontWeight: 500, color: text }}>
                <span>Baja concentración</span>
                <span>Alta</span>
            </div>
            {perimetro && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 10, fontWeight: 500, color: dark ? '#A8C8E8' : '#718096' }}>
                    <span className="hm-peri-swatch sm" />
                    <span>Perímetro del rodeo</span>
                </div>
            )}
        </div>
    );
}
