import { HEAT_GRADIENT } from './types';

// sorted numerically: the integer-like key "1" would otherwise come first and collapse the gradient to one color
const stops = Object.entries(HEAT_GRADIENT)
    .sort(([a], [b]) => Number(a) - Number(b))
    .map(([offset, color]) => `${color} ${Math.round(Number(offset) * 100)}%`)
    .join(', ');

/** `zones` adds the key for the under-used areas overlay. */
export default function HeatmapLegend({ dark = false, zones = false }: { dark?: boolean; zones?: boolean }) {
    const text = dark ? '#7A9CC0' : '#718096';
    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 160 }}>
            <div style={{ height: 8, borderRadius: 999, background: `linear-gradient(90deg, ${stops})` }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, fontWeight: 500, color: text }}>
                <span>Baja concentración</span>
                <span>Alta</span>
            </div>
            {zones && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 10, fontWeight: 500, color: dark ? '#A8C8E8' : '#718096' }}>
                    <span className="hm-zone-swatch sm" />
                    <span>Sin pisar en el período</span>
                </div>
            )}
        </div>
    );
}
