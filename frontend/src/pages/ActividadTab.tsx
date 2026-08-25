import * as L from 'leaflet';
import { useEffect, useRef, useState, useMemo } from 'react';
import {
    BarChart, Bar,
    XAxis, YAxis, CartesianGrid, Tooltip,
    ResponsiveContainer, Cell
} from 'recharts';
import { MapContainer, TileLayer, useMap } from 'react-leaflet';
import { getMetricasAnimal, type AnimalMetricaResponse } from '../api/animalesApi';
import 'leaflet/dist/leaflet.css';
import 'leaflet.heat';

// leaflet.heat patches L globally — no named exports
type HeatLatLngTuple = [number, number, number?];
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const createHeatLayer = (points: HeatLatLngTuple[], opts: object) => (L as any).heatLayer(points, opts);

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
    const [metricas, setMetricas] = useState<AnimalMetricaResponse[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let mounted = true;
        const fetchMetricas = async () => {
            try {
                setLoading(true);
                const data = await getMetricasAnimal(animalId);
                if (mounted) setMetricas(data);
            } catch (err) {
                console.error("Error al cargar historial de actividad:", err);
            } finally {
                if (mounted) setLoading(false);
            }
        };
        fetchMetricas();
        return () => { mounted = false; };
    }, [animalId]);

    const actividadData = useMemo(() => {
        const days = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
        return metricas.map(m => {
            const dateObj = new Date(m.fecha + 'T00:00:00'); 
            const label = days[dateObj.getDay()];
            const dd = String(dateObj.getDate()).padStart(2, '0');
            const mm = String(dateObj.getMonth() + 1).padStart(2, '0');
            return {
                label,
                date: `${dd}/${mm}`,
                value: m.sinDatos ? null : (m.actividadMotora ? Math.round(m.actividadMotora) : 0),
                noData: m.sinDatos,
            };
        });
    }, [metricas]);

    const heatPoints = useMemo(() => {
        const points: HeatLatLngTuple[] = [];
        metricas.forEach(m => {
            if (!m.sinDatos && m.latitud != null && m.longitud != null) {
                points.push([m.latitud, m.longitud, 0.8]);
            }
        });
        return points;
    }, [metricas]);

    const mapCenter: [number, number] = useMemo(() => {
        if (heatPoints.length > 0) {
            const avgLat = heatPoints.reduce((sum, p) => sum + p[0], 0) / heatPoints.length;
            const avgLng = heatPoints.reduce((sum, p) => sum + p[1], 0) / heatPoints.length;
            return [avgLat, avgLng];
        }
        return [-36.622, -64.292];
    }, [heatPoints]);

    if (loading) {
        return (
            <div style={{ padding: '2rem', textAlign: 'center', color: '#718096' }}>
                Cargando historial de actividad...
            </div>
        );
    }

    return (
        <div style={{ paddingTop: '1rem' }}>
            {/* ── Gráfico 1: Actividad Motora ── */}
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

            {/* ── Gráfico 2: Mapa de Calor de Posición ── */}
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
