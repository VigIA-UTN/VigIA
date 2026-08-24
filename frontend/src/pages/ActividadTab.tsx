import * as L from 'leaflet';
import { useEffect, useRef, useMemo } from 'react';
import {
    AreaChart, Area, BarChart, Bar,
    XAxis, YAxis, CartesianGrid, Tooltip,
    ResponsiveContainer, Cell, ReferenceLine,
} from 'recharts';
import { MapContainer, TileLayer, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import 'leaflet.heat';

// leaflet.heat patches L globally — no named exports
type HeatLatLngTuple = [number, number, number?];
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const createHeatLayer = (points: HeatLatLngTuple[], opts: object) => (L as any).heatLayer(points, opts);

// ─── Helpers ────────────────────────────────────────────────────────────────

function getDayLabels(): { label: string; date: string }[] {
    const days = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
    const result = [];
    for (let i = 6; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i);
        const dd = String(d.getDate()).padStart(2, '0');
        const mm = String(d.getMonth() + 1).padStart(2, '0');
        result.push({ label: days[d.getDay()], date: `${dd}/${mm}` });
    }
    return result;
}

function seededRand(seed: number, min: number, max: number): number {
    const x = Math.sin(seed) * 10000;
    return min + (x - Math.floor(x)) * (max - min);
}

// ─── Mock data generators ────────────────────────────────────────────────────

function generateRumiaData(animalId: number) {
    const dayLabels = getDayLabels();
    return dayLabels.map(({ label, date }, i) => {
        const hasData = i !== 2; // día 3 sin datos (D-4)
        return {
            label,
            date,
            value: hasData ? Math.round(seededRand(animalId + i * 7, 200, 480)) : null,
            noData: !hasData,
        };
    });
}

function generateActividadData(animalId: number) {
    const dayLabels = getDayLabels();
    return dayLabels.map(({ label, date }, i) => {
        const hasData = i !== 5; // día 6 sin datos (ayer)
        return {
            label,
            date,
            value: hasData ? Math.round(seededRand(animalId + i * 13, 3000, 12000)) : null,
            noData: !hasData,
        };
    });
}

// ─── Custom Tooltip ──────────────────────────────────────────────────────────

interface TooltipProps {
    active?: boolean;
    payload?: { value: number | null; payload: { date: string; noData?: boolean } }[];
    label?: string;
    unit?: string;
}

function CustomTooltip({ active, payload, label, unit }: TooltipProps) {
    if (!active || !payload || !payload.length) return null;
    const item = payload[0];
    const isNoData = item?.payload?.noData;
    return (
        <div style={{
            background: '#fff',
            border: '1px solid #E2E8F0',
            borderRadius: 8,
            padding: '8px 14px',
            fontSize: '0.85rem',
            boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
        }}>
            <p style={{ margin: 0, fontWeight: 700, color: '#1E3A5F' }}>{label} — {item?.payload?.date}</p>
            {isNoData
                ? <p style={{ margin: 0, color: '#A0AEC0', fontStyle: 'italic' }}>Sin datos</p>
                : <p style={{ margin: 0, color: '#4A5568' }}>{item?.value} {unit}</p>
            }
        </div>
    );
}

// ─── Heatmap Layer Component ─────────────────────────────────────────────────

function HeatmapLayer({ points }: { points: HeatLatLngTuple[] }) {
    const map = useMap();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const layerRef = useRef<any>(null);

    useEffect(() => {
        if (!map) return;
        if (layerRef.current) {
            map.removeLayer(layerRef.current);
        }
        layerRef.current = createHeatLayer(points, {
            radius: 28,
            blur: 20,
            maxZoom: 17,
            gradient: { 0.2: '#66C6EB', 0.5: '#F6C343', 0.8: '#F28C38', 1.0: '#E53E3E' },
        }).addTo(map);

        return () => {
            if (layerRef.current) map.removeLayer(layerRef.current);
        };
    }, [map, points]);

    return null;
}

// invalidate size when tab becomes visible
function MapInvalidator() {
    const map = useMap();
    useEffect(() => {
        setTimeout(() => map.invalidateSize(), 200);
    }, [map]);
    return null;
}

// ─── Mock heatmap coordinates ─────────────────────────────────────────────────

function generateHeatPoints(animalId: number): HeatLatLngTuple[] {
    // Centro: Pampa argentina (La Pampa ~-36.6, -64.3)
    const baseLat = -36.62;
    const baseLng = -64.29;
    const points: HeatLatLngTuple[] = [];

    // Cluster principal (zona de pastoreo habitual)
    for (let i = 0; i < 60; i++) {
        const lat = baseLat + seededRand(animalId + i, -0.002, 0.002);
        const lng = baseLng + seededRand(animalId + i + 100, -0.003, 0.003);
        points.push([lat, lng, seededRand(animalId + i + 200, 0.5, 1.0)]);
    }
    // Cluster secundario (bebedero/sombra)
    for (let i = 0; i < 25; i++) {
        const lat = baseLat + 0.004 + seededRand(animalId + i + 300, -0.001, 0.001);
        const lng = baseLng + 0.005 + seededRand(animalId + i + 400, -0.001, 0.001);
        points.push([lat, lng, seededRand(animalId + i + 500, 0.3, 0.8)]);
    }
    // Puntos dispersos de tránsito
    for (let i = 0; i < 15; i++) {
        const lat = baseLat + seededRand(animalId + i + 600, -0.005, 0.005);
        const lng = baseLng + seededRand(animalId + i + 700, -0.005, 0.005);
        points.push([lat, lng, seededRand(animalId + i + 800, 0.05, 0.25)]);
    }
    return points;
}

// ─── Section wrapper ──────────────────────────────────────────────────────────

function ChartCard({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
    return (
        <div style={{
            background: '#fff',
            borderRadius: 12,
            boxShadow: '0 1px 4px rgba(0,0,0,0.07)',
            padding: '1.5rem 1.5rem 1.25rem',
            marginBottom: '1.5rem',
        }}>
            <div style={{ marginBottom: '1rem' }}>
                <h5 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 700, color: '#1E3A5F' }}>{title}</h5>
                {subtitle && <p style={{ margin: '2px 0 0', fontSize: '0.8rem', color: '#A0AEC0' }}>{subtitle}</p>}
            </div>
            {children}
        </div>
    );
}

// Legend dot helper
function LegendDot({ color, label }: { color: string; label: string }) {
    return (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, marginRight: 16, fontSize: '0.8rem', color: '#718096' }}>
            <span style={{ width: 10, height: 10, borderRadius: '50%', background: color, display: 'inline-block' }} />
            {label}
        </span>
    );
}

// ─── Main component ───────────────────────────────────────────────────────────

interface ActividadTabProps {
    animalId: number;
}

export default function ActividadTab({ animalId }: ActividadTabProps) {
    const rumiaData = useMemo(() => generateRumiaData(animalId), [animalId]);
    const actividadData = useMemo(() => generateActividadData(animalId), [animalId]);
    const heatPoints = useMemo(() => generateHeatPoints(animalId), [animalId]);

    // Center of the mock heatmap data
    const mapCenter: [number, number] = [-36.622, -64.292];

    return (
        <div style={{ paddingTop: '1rem' }}>
            {/* ── Gráfico 1: Rumia ── */}
            <ChartCard
                title="Rumia"
                subtitle="Minutos de rumia registrados por día · últimos 7 días"
            >
                <div style={{ marginBottom: '0.5rem' }}>
                    <LegendDot color="#66C6EB" label="Minutos de rumia" />
                    <LegendDot color="#E2E8F0" label="Sin datos" />
                </div>
                <ResponsiveContainer width="100%" height={200}>
                    <AreaChart data={rumiaData} margin={{ top: 5, right: 10, left: -10, bottom: 0 }}>
                        <defs>
                            <linearGradient id="rumiaGrad" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="#66C6EB" stopOpacity={0.25} />
                                <stop offset="95%" stopColor="#66C6EB" stopOpacity={0} />
                            </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" stroke="#F0F4F8" />
                        <XAxis
                            dataKey="label"
                            tick={{ fontSize: 12, fill: '#718096' }}
                            axisLine={false}
                            tickLine={false}
                        />
                        <YAxis
                            tick={{ fontSize: 11, fill: '#A0AEC0' }}
                            axisLine={false}
                            tickLine={false}
                            domain={[0, 600]}
                        />
                        <Tooltip content={<CustomTooltip unit="min" />} />
                        <ReferenceLine y={350} stroke="#66C6EB" strokeDasharray="4 4" strokeOpacity={0.4} label={{ value: 'Ref.', fontSize: 10, fill: '#A0AEC0' }} />
                        <Area
                            type="monotone"
                            dataKey="value"
                            stroke="#66C6EB"
                            strokeWidth={2.5}
                            fill="url(#rumiaGrad)"
                            dot={(props) => {
                                const { cx, cy, payload } = props as { cx: number; cy: number; payload: { noData: boolean; value: number | null } };
                                if (payload.noData || payload.value === null) {
                                    return <circle key={`dot-${cx}`} cx={cx} cy={cy ?? 100} r={5} fill="#E2E8F0" stroke="#CBD5E0" strokeWidth={1.5} />;
                                }
                                return <circle key={`dot-${cx}`} cx={cx} cy={cy} r={4} fill="#66C6EB" stroke="#fff" strokeWidth={2} />;
                            }}
                            activeDot={{ r: 6, fill: '#66C6EB', stroke: '#fff', strokeWidth: 2 }}
                            connectNulls={false}
                        />
                    </AreaChart>
                </ResponsiveContainer>
            </ChartCard>

            {/* ── Gráfico 2: Actividad Motora ── */}
            <ChartCard
                title="Actividad Motora"
                subtitle="Impulsos de movimiento registrados por día · últimos 7 días - Aca un moveState promedio diario"
            >
                <div style={{ marginBottom: '0.5rem' }}>
                    <LegendDot color="#8CC63F" label="Impulsos de movimiento" />
                    <LegendDot color="#E2E8F0" label="Sin datos" />
                </div>
                <ResponsiveContainer width="100%" height={200}>
                    <BarChart data={actividadData} margin={{ top: 5, right: 10, left: -10, bottom: 0 }} barCategoryGap="30%">
                        <CartesianGrid strokeDasharray="3 3" stroke="#F0F4F8" vertical={false} />
                        <XAxis
                            dataKey="label"
                            tick={{ fontSize: 12, fill: '#718096' }}
                            axisLine={false}
                            tickLine={false}
                        />
                        <YAxis
                            tick={{ fontSize: 11, fill: '#A0AEC0' }}
                            axisLine={false}
                            tickLine={false}
                            tickFormatter={(v) => v >= 1000 ? `${(v / 1000).toFixed(0)}k` : v}
                        />
                        <Tooltip content={<CustomTooltip unit="impulsos" />} />
                        <Bar dataKey="value" radius={[6, 6, 0, 0]}>
                            {actividadData.map((entry, index) => (
                                <Cell
                                    key={`cell-${index}`}
                                    fill={entry.noData ? '#E2E8F0' : '#8CC63F'}
                                    opacity={entry.noData ? 0.6 : 1}
                                />
                            ))}
                        </Bar>
                    </BarChart>
                </ResponsiveContainer>
            </ChartCard>

            {/* ── Gráfico 3: Mapa de Calor de Posición ── */}
            <ChartCard
                title="Mapa de Calor — Posición"
                subtitle="Zonas por las que más tiempo estuvo el animal · últimos 7 días"
            >
                <div style={{ marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: '0.8rem', color: '#718096' }}>Intensidad:</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        {[
                            { color: '#66C6EB', label: 'Baja' },
                            { color: '#F6C343', label: 'Media' },
                            { color: '#F28C38', label: 'Alta' },
                            { color: '#E53E3E', label: 'Muy alta' },
                        ].map(({ color, label }) => (
                            <span key={label} style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginRight: 10, fontSize: '0.79rem', color: '#718096' }}>
                                <span style={{ width: 12, height: 12, borderRadius: 2, background: color, display: 'inline-block' }} />
                                {label}
                            </span>
                        ))}
                    </div>
                </div>
                <div style={{ borderRadius: 10, overflow: 'hidden', height: 340, border: '1px solid #E2E8F0' }}>
                    <MapContainer
                        center={mapCenter}
                        zoom={15}
                        style={{ height: '100%', width: '100%' }}
                        zoomControl={true}
                        scrollWheelZoom={false}
                    >
                        <TileLayer
                            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                        />
                        <HeatmapLayer points={heatPoints} />
                        <MapInvalidator />
                    </MapContainer>
                </div>
            </ChartCard>
        </div>
    );
}
