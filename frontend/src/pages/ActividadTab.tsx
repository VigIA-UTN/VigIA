import axios from 'axios';
import { useEffect, useRef, useState, useMemo } from 'react';
import {
    BarChart, Bar,
    LineChart, Line, ReferenceLine,
    XAxis, YAxis, CartesianGrid, Tooltip,
    ResponsiveContainer, Cell
} from 'recharts';
import { MapContainer, TileLayer, ZoomControl, useMap } from 'react-leaflet';
import { Modal, Button, Form, Spinner, Alert } from 'react-bootstrap';
import {
    getMetricasAnimal,
    type AnimalMetricaResponse,
    getHistorialPesos,
    registrarPeso,
    type RegistroPesoResponse
} from '../api/animalesApi';
import 'leaflet/dist/leaflet.css';
import HeatmapLayer from '../components/heatmap/HeatmapLayer';
import MapInvalidator from '../components/MapInvalidator';
import HeatmapPanel from '../components/heatmap/HeatmapPanel';
import { useHeatPoints } from '../components/heatmap/useHeatPoints';
import { lastDaysRange } from '../components/heatmap/heatmapFilters';
import type { HeatFilter, HeatLatLngTuple } from '../components/heatmap/types';

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

const PANEL_MAX_WIDTH = 462;
const PANEL_INSET = 16;
/** Horizontal space the overlaid panel covers on the left of the map. */
const panelCover = () => Math.min(PANEL_MAX_WIDTH, window.innerWidth - 40) + PANEL_INSET * 2;

/**
 * The map is always full width and the panel floats over its left side, so the map never resizes.
 * Zooms to the heat inside the visible (uncovered) area, and re-centres it with one cheap pan when the panel toggles.
 */
function MapInset({ points, expanded }: { points: HeatLatLngTuple[]; expanded: boolean }) {
    const map = useMap();
    const expandedRef = useRef(expanded);
    const first = useRef(true);

    useEffect(() => {
        expandedRef.current = expanded;
        if (first.current) { first.current = false; return; }
        map.panBy([expanded ? panelCover() / 2 : -panelCover() / 2, 0], { duration: 0.25 });
    }, [map, expanded]);

    useEffect(() => {
        if (points.length === 0) return;
        map.fitBounds(points.map(p => [p[0], p[1]] as [number, number]), {
            maxZoom: 17,
            paddingTopLeft: [(expandedRef.current ? 0 : panelCover()) + 24, 24],
            paddingBottomRight: [24, 24],
        });
    }, [map, points]);
    return null;
}

const NO_ITEMS: never[] = [];
const noop = () => undefined;
const DEFAULT_CENTER: [number, number] = [-36.622, -64.292];

// ─── Section wrapper ──────────────────────────────────────────────────────────


function ChartCard({
    title,
    subtitle,
    action,
    children
}: {
    title: string;
    subtitle?: string;
    action?: React.ReactNode;
    children: React.ReactNode;
}) {
    return (
        <div style={{
            background: '#fff',
            borderRadius: 12,
            boxShadow: '0 1px 4px rgba(0,0,0,0.07)',
            padding: '1.5rem 1.5rem 1.25rem',
            marginBottom: '1.5rem',
        }}>
            <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'flex-start',
                marginBottom: '1rem',
                flexWrap: 'wrap',
                gap: 8,
            }}>
                <div>
                    <h5 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600, color: '#1E3A5F' }}>{title}</h5>
                    {subtitle && <p style={{ margin: '2px 0 0', fontSize: '0.8rem', color: '#A0AEC0' }}>{subtitle}</p>}
                </div>
                {action && <div>{action}</div>}
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
    establecimientoId: number;
}

export default function ActividadTab({ animalId, establecimientoId }: ActividadTabProps) {
    const [metricas, setMetricas] = useState<AnimalMetricaResponse[]>([]);
    const [loading, setLoading] = useState(true);

    // Historial de pesos
    const [pesos, setPesos] = useState<RegistroPesoResponse[]>([]);
    const [loadingPesos, setLoadingPesos] = useState(true);
    const [unidad, setUnidad] = useState<'kg' | 'lbs'>('kg');

    // Modal Registrar Peso
    const [showPesoModal, setShowPesoModal] = useState(false);
    const [submittingPeso, setSubmittingPeso] = useState(false);
    const [pesoFormError, setPesoFormError] = useState<string | null>(null);
    const [pesoSuccess, setPesoSuccess] = useState<string | null>(null);
    const [pesoForm, setPesoForm] = useState({
        valor: '',
        unidad: 'kg' as 'kg' | 'lbs',
        fecha: new Date().toISOString().split('T')[0],
    });

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

    useEffect(() => {
        let mounted = true;
        const fetchPesos = async () => {
            try {
                setLoadingPesos(true);
                const data = await getHistorialPesos(animalId);
                if (mounted) setPesos(data);
            } catch (err) {
                console.error('Error al cargar historial de pesos:', err);
            } finally {
                if (mounted) setLoadingPesos(false);
            }
        };
        fetchPesos();
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

    // Mapa de calor: posiciones reales de este animal; solo el período es editable
    const [heatRange, setHeatRange] = useState(() => lastDaysRange(7));
    // mapa ampliado: oculta el panel (calendario) y el mapa ocupa todo el ancho
    const [mapExpanded, setMapExpanded] = useState(() => window.innerWidth < 900); // en pantallas angostas el panel taparía el mapa
    const heatFilter = useMemo<HeatFilter>(
        () => ({ scope: 'animal', rodeoId: null, animalId, perimetro: false, ...heatRange }),
        [animalId, heatRange],
    );
    const { points: heatPoints, loading: heatLoading, error: heatError } = useHeatPoints(heatFilter, { establecimientoId });

    const handleSubmitPeso = async (e: React.FormEvent) => {
        e.preventDefault();
        const val = parseFloat(pesoForm.valor);
        if (isNaN(val) || val <= 0) {
            setPesoFormError('El peso debe ser un número positivo.');
            return;
        }
        if (!pesoForm.fecha) {
            setPesoFormError('La fecha es obligatoria.');
            return;
        }

        try {
            setSubmittingPeso(true);
            setPesoFormError(null);

            // Normalize valorKg if entered in lbs
            const valorKg = pesoForm.unidad === 'lbs'
                ? Math.round((val / 2.20462) * 100) / 100
                : Math.round(val * 100) / 100;

            await registrarPeso(animalId, {
                valorKg,
                unidad: pesoForm.unidad,
                fecha: pesoForm.fecha,
            });

            setPesoSuccess('¡Peso registrado correctamente!');
            const updatedPesos = await getHistorialPesos(animalId);
            setPesos(updatedPesos);

            setTimeout(() => {
                setShowPesoModal(false);
                setPesoSuccess(null);
                setPesoForm({
                    valor: '',
                    unidad,
                    fecha: new Date().toISOString().split('T')[0],
                });
            }, 1000);
        } catch (err) {
            console.error('Error al registrar peso:', err);
            if (axios.isAxiosError(err) && err.response?.data?.message) {
                setPesoFormError(err.response.data.message);
            } else {
                setPesoFormError('Error al registrar el peso. Verifique los datos ingresados.');
            }
        } finally {
            setSubmittingPeso(false);
        }
    };

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
                subtitle="Zonas por las que más tiempo estuvo el animal en el período elegido"
            >
                <div style={{ position: 'relative', height: 480, borderRadius: 10, overflow: 'hidden', border: '1px solid #E2E8F0' }}>
                    <MapContainer
                        center={DEFAULT_CENTER}
                        zoom={15}
                        style={{ height: '100%', width: '100%', background: '#1E3A5F' }}
                        zoomControl={false}
                        attributionControl={false}
                        scrollWheelZoom={false}
                    >
                        <ZoomControl position="bottomright" />
                        <TileLayer
                            attribution='Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community'
                            url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                        />
                        <HeatmapLayer points={heatPoints} />
                        <MapInset points={heatPoints} expanded={mapExpanded} />
                        <MapInvalidator />
                    </MapContainer>

                    {/* panel over the map; only opacity/transform animate, so toggling never resizes or redraws the map */}
                    <div
                        aria-hidden={mapExpanded}
                        style={{
                            position: 'absolute', top: PANEL_INSET, bottom: PANEL_INSET, left: PANEL_INSET, zIndex: 1000,
                            display: 'flex', pointerEvents: 'none',
                            opacity: mapExpanded ? 0 : 1,
                            transform: mapExpanded ? 'translateX(-16px)' : 'translateX(0)',
                            visibility: mapExpanded ? 'hidden' : 'visible',
                            transition: `opacity 200ms ease, transform 200ms ease, visibility 0s linear ${mapExpanded ? '200ms' : '0s'}`,
                        }}
                    >
                        <HeatmapPanel
                            fixedAnimal
                            enabled
                            onEnabledChange={noop}
                            filter={heatFilter}
                            onFilterChange={f => setHeatRange({ from: f.from, to: f.to })}
                            rodeos={NO_ITEMS}
                            animals={NO_ITEMS}
                            loading={heatLoading}
                            error={heatError}
                            pointCount={heatPoints.length}
                        />
                    </div>

                    <button
                        type="button"
                        onClick={() => setMapExpanded(v => !v)}
                        aria-label={mapExpanded ? 'Mostrar filtros' : 'Ampliar mapa'}
                        aria-pressed={mapExpanded}
                        title={mapExpanded ? 'Mostrar filtros' : 'Ampliar mapa'}
                        style={{
                            position: 'absolute', top: 10, right: 10, zIndex: 1001, width: 34, height: 34,
                            border: 0, borderRadius: 8, background: '#1E3A5F', color: '#fff', cursor: 'pointer',
                            boxShadow: '0 1px 5px rgba(0,0,0,0.4)',
                        }}
                    >
                        <i className={`bi ${mapExpanded ? 'bi-fullscreen-exit' : 'bi-arrows-fullscreen'}`} />
                    </button>
                </div>
                <p style={{ margin: '8px 0 0', fontSize: '0.7rem', color: '#A0AEC0' }}>
                    Imágenes: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP y la comunidad de usuarios de GIS.
                </p>
            </ChartCard>

            {/* ── Gráfico 3: Evolución de Peso ── */}
            <ChartCard
                title="Evolución de Peso"
                action={
                    <button
                        onClick={() => {
                            setPesoForm({
                                valor: '',
                                unidad,
                                fecha: new Date().toISOString().split('T')[0],
                            });
                            setPesoFormError(null);
                            setPesoSuccess(null);
                            setShowPesoModal(true);
                        }}
                        style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 6,
                            background: '#94C11E',
                            color: '#fff',
                            border: 'none',
                            borderRadius: 8,
                            padding: '6px 14px',
                            fontSize: '0.82rem',
                            fontWeight: 600,
                            cursor: 'pointer',
                            transition: 'background 0.2s',
                        }}
                        onMouseOver={(e) => (e.currentTarget.style.background = '#82B70C')}
                        onMouseOut={(e) => (e.currentTarget.style.background = '#94C11E')}
                    >
                        <i className="bi bi-plus-circle" /> Registrar Peso
                    </button>
                }
            >
                {/* Unit toggle */}
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem', flexWrap: 'wrap', gap: 8 }}>
                    <LegendDot color="#1E3A5F" label={unidad === 'kg' ? 'Peso (kg)' : 'Peso (lbs)'} />
                    <div style={{
                        display: 'inline-flex',
                        borderRadius: 8,
                        overflow: 'hidden',
                        border: '1px solid #E2E8F0',
                        fontSize: '0.8rem',
                        fontWeight: 600,
                    }}>
                        {(['kg', 'lbs'] as const).map(u => (
                            <button
                                key={u}
                                onClick={() => setUnidad(u)}
                                style={{
                                    padding: '4px 14px',
                                    border: 'none',
                                    cursor: 'pointer',
                                    background: unidad === u ? '#1E3A5F' : '#F7FAFC',
                                    color: unidad === u ? '#fff' : '#718096',
                                    transition: 'background 0.15s, color 0.15s',
                                    fontWeight: unidad === u ? 700 : 400,
                                }}
                            >
                                {u}
                            </button>
                        ))}
                    </div>
                </div>

                {loadingPesos ? (
                    <div style={{ textAlign: 'center', color: '#A0AEC0', padding: '1.5rem 0', fontSize: '0.9rem' }}>
                        Cargando historial de peso...
                    </div>
                ) : pesos.length === 0 ? (
                    <div style={{
                        textAlign: 'center',
                        color: '#A0AEC0',
                        padding: '2rem 0',
                        fontSize: '0.88rem',
                        fontStyle: 'italic',
                    }}>
                        Sin registros de peso aún.
                    </div>
                ) : (() => {
                    const KG_TO_LBS = 2.20462;
                    const convert = (kg: number) => unidad === 'lbs'
                        ? Math.round(kg * KG_TO_LBS * 10) / 10
                        : Math.round(kg * 10) / 10;

                    // Sort ascending by date for the line chart
                    const chartData = [...pesos]
                        .sort((a, b) => a.fecha.localeCompare(b.fecha))
                        .map(p => {
                            const [y, m, d] = p.fecha.split('-');
                            return {
                                label: `${d}/${m}/${y?.slice(2)}`,
                                value: convert(p.valorKg),
                                rawKg: p.valorKg,
                            };
                        });

                    const values = chartData.map(d => d.value);
                    const minVal = Math.min(...values);
                    const maxVal = Math.max(...values);
                    const avg = Math.round((values.reduce((a, b) => a + b, 0) / values.length) * 10) / 10;
                    const yPad = (maxVal - minVal) * 0.15 || 5;

                    return (
                        <>
                            {/* Summary pills */}
                            <div style={{ display: 'flex', gap: 12, marginBottom: '1rem', flexWrap: 'wrap', alignContent: "center", justifyContent: "center" }}>
                                {[
                                    { label: 'Mínimo', value: `${Math.min(...values)} ${unidad}`, color: '#38A169' },
                                    { label: 'Máximo', value: `${Math.max(...values)} ${unidad}`, color: '#E53E3E' },
                                    { label: 'Promedio', value: `${avg} ${unidad}`, color: '#1B344D' },
                                ].map(pill => (
                                    <div key={pill.label} style={{
                                        background: '#F7FAFC',
                                        borderRadius: 8,
                                        padding: '5px 14px',
                                        fontSize: '0.78rem',
                                        color: '#4A5568',
                                        border: `1px solid ${pill.color}22`,
                                    }}>
                                        <span style={{ fontWeight: 700, color: pill.color }}>{pill.value}</span>
                                        <span style={{ marginLeft: 5, color: '#A0AEC0' }}>{pill.label}</span>
                                    </div>
                                ))}
                            </div>

                            <ResponsiveContainer width="100%" height={220}>
                                <LineChart data={chartData} margin={{ top: 8, right: 16, left: -10, bottom: 0 }}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#F0F4F8" vertical={false} />
                                    <XAxis
                                        dataKey="label"
                                        tick={{ fontSize: 11, fill: '#718096' }}
                                        axisLine={false}
                                        tickLine={false}
                                    />
                                    <YAxis
                                        domain={[Math.max(0, minVal - yPad), maxVal + yPad]}
                                        tick={{ fontSize: 11, fill: '#A0AEC0' }}
                                        axisLine={false}
                                        tickLine={false}
                                        tickFormatter={v => `${v}`}
                                    />
                                    <Tooltip
                                        formatter={(value) => [`${value} ${unidad}`, 'Peso']}
                                        contentStyle={{
                                            background: '#fff',
                                            border: '1px solid #E2E8F0',
                                            borderRadius: 8,
                                            fontSize: '0.85rem',
                                            boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
                                        }}
                                        labelStyle={{ fontWeight: 700, color: '#1E3A5F' }}
                                    />
                                    <ReferenceLine
                                        y={avg}
                                        stroke="#1B344D"
                                        strokeDasharray="5 3"
                                        strokeWidth={1.5}
                                        label={{ value: `Prom. ${avg}`, position: 'insideTopRight', fontSize: 10, fill: '#1B344D' }}
                                    />
                                    <Line
                                        type="monotone"
                                        dataKey="value"
                                        stroke="#1B344D"
                                        strokeWidth={2.5}
                                        dot={{ r: 4, fill: '#1B344D', strokeWidth: 2, stroke: '#fff' }}
                                        activeDot={{ r: 6, fill: '#1B344D', stroke: '#fff', strokeWidth: 2 }}
                                    />
                                </LineChart>
                            </ResponsiveContainer>
                        </>
                    );
                })()}
            </ChartCard>

            {/* ── Modal Registrar Peso ── */}
            <Modal
                show={showPesoModal}
                onHide={() => setShowPesoModal(false)}
                centered
                dialogClassName="modal-nuevo-animal"
            >
                <Modal.Header closeButton>
                    <Modal.Title>Registrar Peso</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {pesoFormError && <Alert variant="danger">{pesoFormError}</Alert>}
                    {pesoSuccess && <Alert variant="success">{pesoSuccess}</Alert>}
                    <Form onSubmit={handleSubmitPeso} id="form-registro-peso">
                        <Form.Group className="mb-3">
                            <Form.Label>Fecha de pesaje*</Form.Label>
                            <Form.Control
                                type="date"
                                name="fecha"
                                value={pesoForm.fecha}
                                onChange={(e) => setPesoForm(prev => ({ ...prev, fecha: e.target.value }))}
                                max={new Date().toISOString().split('T')[0]}
                                required
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Peso*</Form.Label>
                            <div style={{ display: 'flex', gap: 10 }}>
                                <Form.Control
                                    type="number"
                                    step="0.1"
                                    min="0.1"
                                    name="valor"
                                    placeholder='0.00'
                                    value={pesoForm.valor}
                                    onChange={(e) => setPesoForm(prev => ({ ...prev, valor: e.target.value }))}
                                    required
                                />
                                <Form.Select
                                    name="unidad"
                                    value={pesoForm.unidad}
                                    onChange={(e) => setPesoForm(prev => ({ ...prev, unidad: e.target.value as 'kg' | 'lbs' }))}
                                    style={{ width: '110px' }}
                                >
                                    <option value="kg">kg</option>
                                    <option value="lbs">lbs</option>
                                </Form.Select>
                            </div>
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button
                        type="submit"
                        form="form-registro-peso"
                        disabled={submittingPeso || !!pesoSuccess || !pesoForm.valor}
                        className="btn-guardar-animal"
                    >
                        {submittingPeso
                            ? <Spinner as="span" animation="border" size="sm" />
                            : 'Registrar'
                        }
                    </Button>
                </Modal.Footer>
            </Modal>
        </div>
    );
}
