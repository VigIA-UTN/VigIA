import { HEAT_GRADIENT } from './types';

const stops = Object.entries(HEAT_GRADIENT)
    .map(([offset, color]) => `${color} ${Math.round(Number(offset) * 100)}%`)
    .join(', ');

export default function HeatmapLegend({ dark = false }: { dark?: boolean }) {
    const text = dark ? '#7A9CC0' : '#718096';
    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 160 }}>
            <div style={{ height: 8, borderRadius: 999, background: `linear-gradient(90deg, ${stops})` }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, fontWeight: 500, color: text }}>
                <span>Baja concentración</span>
                <span>Alta</span>
            </div>
        </div>
    );
}
