import { useEffect, useMemo, useState } from 'react';
import {
    LineChart, Line, ReferenceLine, ReferenceArea, ReferenceDot,
    XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import { OverlayTrigger, Popover } from 'react-bootstrap';
import {
    getCohesionAnimal, estadoCohesion, UMBRAL_NORMAL, UMBRAL_CRITICO,
    type CohesionResponse,
} from '../api/cohesionApi';
import { SEVERIDAD } from '../utils/severidadColores';
import DateRangeCalendar from './heatmap/DateRangeCalendar';
import { lastDaysRange } from './heatmap/heatmapFilters';
import './heatmap/heatmap.css';

type Periodo = '24h' | '7d' | '30d' | '90d' | 'custom';

const DIA = 86400_000;
const PERIODOS: { key: Periodo; label: string; dias: number }[] = [
    { key: '24h', label: '24 h', dias: 1 },
    { key: '7d', label: '7 d', dias: 7 },
    { key: '30d', label: '30 d', dias: 30 },
    { key: '90d', label: '90 d', dias: 90 },
    { key: 'custom', label: 'Personalizado', dias: 0 },
];

const NAVY = '#1E3A5F';
const GRIS = '#949494';
const SIN_DATOS = '#EDF2F7';

const fmtFecha = (t: number) => new Date(t).toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' });

interface Fila { i: number; label: string; full: string; valor: number | null; rodeo: number | null }

function CohesionTooltip({ active, payload }: { active?: boolean; payload?: { payload: Fila }[] }) {
    const f = payload?.[0]?.payload;
    if (!active || !f) return null;
    return (
        <div style={{ background: NAVY, color: '#fff', borderRadius: 8, padding: '8px 12px', fontSize: 13 }}>
            <div style={{ color: '#C5D8EE', fontSize: 12 }}>{f.full}</div>
            {f.valor == null
                ? <div style={{ fontStyle: 'italic', color: '#A8C8E8' }}>Sin datos</div>
                : <div style={{ fontWeight: 600 }}>{f.valor} <span style={{ fontWeight: 400, color: '#A8C8E8' }}>· rodeo {f.rodeo ?? '—'}</span></div>}
        </div>
    );
}

function Leyenda({ label, linea, swatch }: { label: string; linea?: string; swatch?: string }) {
    return (
        <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
            {swatch
                ? <span style={{ width: 14, height: 10, background: swatch, borderRadius: 2 }} />
                : <span style={{ width: 18, borderTop: linea }} />}
            {label}
        </span>
    );
}

interface Props {
    animalId: number;
    enCalibracion?: boolean;
}

/** Índice de cohesión social del animal: valor actual con su interpretación y evolución vs. el rodeo. */
export default function CohesionIndiceCard({ animalId, enCalibracion }: Props) {
    const [periodo, setPeriodo] = useState<Periodo>('7d');
    const [custom, setCustom] = useState(() => lastDaysRange(30));
    const [data, setData] = useState<CohesionResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    useEffect(() => {
        if (enCalibracion) return;
        let mounted = true;
        const fetchCohesion = async () => {
            const dias = PERIODOS.find(p => p.key === periodo)!.dias;
            const [desde, hasta] = periodo === 'custom'
                ? [custom.from, custom.to + DIA - 1]
                : [Date.now() - dias * DIA, Date.now()];
            setLoading(true);
            setError(false);
            try {
                const r = await getCohesionAnimal(animalId, desde, hasta);
                if (mounted) setData(r);
            } catch (err) {
                console.error('Error al cargar índice de cohesión:', err);
                if (mounted) setError(true);
            } finally {
                if (mounted) setLoading(false);
            }
        };
        fetchCohesion();
        return () => { mounted = false; };
    }, [animalId, enCalibracion, periodo, custom]);

    const filas = useMemo<Fila[]>(() => {
        const serie = data?.serie ?? [];
        const porHora = serie.length > 1 && Date.parse(serie[1].ts) - Date.parse(serie[0].ts) < DIA;
        return serie.map((p, i) => {
            const d = new Date(p.ts);
            const dia = d.toLocaleDateString('es-AR', { day: 'numeric', month: 'short' });
            const hora = d.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' });
            return {
                i,
                label: periodo === '24h' ? hora : dia,
                full: porHora ? `${dia} · ${hora}` : d.toLocaleDateString('es-AR', { day: 'numeric', month: 'short', year: 'numeric' }),
                valor: p.valor,
                rodeo: p.promedioRodeo,
            };
        });
    }, [data, periodo]);

    // Tramos consecutivos sin datos → bandas grises sobre el gráfico.
    const huecos = useMemo(() => {
        const out: [number, number][] = [];
        filas.forEach(f => {
            if (f.valor != null) return;
            const last = out.at(-1);
            if (last && last[1] === f.i - 1) last[1] = f.i;
            else out.push([f.i, f.i]);
        });
        return out;
    }, [filas]);

    const n = filas.length;
    const ticks = useMemo(() => {
        const cada = Math.max(1, Math.ceil(n / 6));
        return filas.filter(f => f.i % cada === 0 && n - 1 - f.i >= cada / 2).map(f => f.i).concat(n ? [n - 1] : []);
    }, [filas, n]);

    const conValor = filas.flatMap(f => (f.valor == null ? [] : [f.valor]));
    const promedio = conValor.length ? Math.round(conValor.reduce((a, b) => a + b, 0) / conValor.length) : null;
    const estado = data ? estadoCohesion(data.actual) : null;
    const delta = data ? data.actual - data.promedioPeriodoAnterior : 0;

    const calendario = (
        <Popover style={{ maxWidth: 'none', border: 0, background: 'transparent' }}>
            <div className="hm-panel">
                <DateRangeCalendar from={custom.from} to={custom.to} onChange={(from, to) => setCustom({ from, to })} />
            </div>
        </Popover>
    );

    return (
        <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 4px rgba(0,0,0,0.07)', padding: '1.5rem', marginBottom: '1.5rem' }}>
            <div className="d-flex justify-content-between align-items-end flex-wrap gap-3 pb-2 mb-3" style={{ borderBottom: '2px solid #EBF1F5' }}>
                <h5 className="m-0 d-flex align-items-center gap-2" style={{ fontSize: '1.25rem', fontWeight: 600, color: NAVY }}>
                    Índice de cohesión
                </h5>
                {!enCalibracion && (
                    <div role="group" aria-label="Período" className="d-flex flex-wrap gap-1 p-1" style={{ background: '#F3F3F3', borderRadius: 12 }}>
                        {PERIODOS.map(p => {
                            const on = p.key === periodo;
                            return (
                                <button
                                    key={p.key}
                                    type="button"
                                    aria-pressed={on}
                                    onClick={() => setPeriodo(p.key)}
                                    style={{
                                        border: 0, borderRadius: 9, padding: '6px 14px', fontSize: 14, fontWeight: 500,
                                        background: on ? '#fff' : 'transparent', color: on ? NAVY : '#5A5A5A',
                                        boxShadow: on ? '0 2px 4px rgba(0,0,0,.25)' : 'none', transition: 'background .15s ease',
                                    }}
                                >
                                    {p.label}
                                </button>
                            );
                        })}
                    </div>
                )}
            </div>

            {enCalibracion ? (
                <div className="text-center py-4" style={{ color: '#718096' }}>
                    <i className="bi bi-clock-history me-2" />Disponible al finalizar la calibración.
                </div>
            ) : (
                <>
                    {periodo === 'custom' && (
                        <div className="d-flex align-items-center gap-3 mb-3 flex-wrap">
                            <OverlayTrigger trigger="click" rootClose placement="bottom-start" overlay={calendario}>
                                <button
                                    type="button"
                                    className="d-flex align-items-center gap-3"
                                    style={{ border: 0, borderRadius: 12, padding: '8px 14px', boxShadow: `inset 0 0 0 2px ${NAVY}`, background: '#fff', fontSize: 15 }}
                                >
                                    <span><small className="text-muted me-1">Desde</small>{fmtFecha(custom.from)}</span>
                                    <span><small className="text-muted me-1">Hasta</small>{fmtFecha(custom.to)}</span>
                                    <i className="bi bi-calendar3" />
                                </button>
                            </OverlayTrigger>                        </div>
                    )}

                    {loading && !data ? (
                        <div className="text-center py-4" style={{ color: '#718096' }}>Cargando índice de cohesión...</div>
                    ) : error || !data || !estado ? (
                        <div className="text-center py-4 text-danger">No se pudo cargar el índice de cohesión.</div>
                    ) : (
                        <div className="d-flex flex-wrap gap-4" style={{ opacity: loading ? 0.6 : 1 }}>
                            {/* Valor actual + interpretación */}
                            <div className="d-flex flex-column gap-3" style={{ flex: '1 1 220px', maxWidth: 280 }}>
                                <div className="d-flex flex-column gap-2">
                                    <small style={{ color: '#5A5A5A' }}>Actual</small>
                                    <div className="d-flex align-items-baseline gap-1">
                                        <span style={{ fontSize: 64, fontWeight: 600, color: NAVY, lineHeight: 1 }}>{data.actual}</span>
                                        <span style={{ fontSize: 20, color: GRIS }}>/100</span>
                                    </div>
                                    <div className="d-flex align-items-center gap-2 flex-wrap">
                                        <span style={{ width: 14, height: 14, borderRadius: '50%', background: estado.color, boxShadow: '0 0 4px rgba(0,0,0,.25)' }} />
                                        <span style={{ fontSize: 15, fontWeight: 600, color: '#263238' }}>{estado.label}</span>
                                        <small style={{ color: '#5A5A5A' }}>
                                            {delta > 0 ? '+' : delta < 0 ? '−' : '±'}{Math.abs(delta)} pts vs. período anterior
                                        </small>
                                    </div>
                                </div>
                                <p className="m-0" style={{ fontSize: 14, lineHeight: 1.45, color: '#3F3F3F' }}>{estado.explicacion}</p>
                                <div className="d-flex flex-column gap-2 pt-3" style={{ borderTop: '1px dashed #E2E8F0', fontSize: 14 }}>
                                    <div className="d-flex justify-content-between"><span style={{ color: '#5A5A5A' }}>Promedio del período</span><strong>{promedio ?? '—'}</strong></div>
                                    <div className="d-flex justify-content-between"><span style={{ color: '#5A5A5A' }}>Promedio del rodeo</span><strong>{data.promedioRodeo}</strong></div>
                                </div>
                            </div>

                            {/* Evolución */}
                            <div style={{ flex: '3 1 320px', minWidth: 0 }}>
                                <ResponsiveContainer width="100%" height={240}>
                                    <LineChart data={filas} margin={{ top: 10, right: 12, left: -16, bottom: 0 }}>
                                        <CartesianGrid stroke="#F0F4F8" vertical={false} />
                                        <XAxis
                                            dataKey="i" type="number" domain={[0, Math.max(1, n - 1)]} ticks={ticks}
                                            tickFormatter={i => filas[i]?.label ?? ''}
                                            tick={{ fontSize: 11, fill: '#5A5A5A' }} axisLine={false} tickLine={false}
                                        />
                                        <YAxis domain={[0, 100]} ticks={[0, UMBRAL_CRITICO, UMBRAL_NORMAL, 100]} tick={{ fontSize: 11, fill: GRIS }} axisLine={false} tickLine={false} />
                                        {huecos.map(([a, b]) => (
                                            <ReferenceArea
                                                key={a} x1={Math.max(0, a - 0.5)} x2={Math.min(n - 1, b + 0.5)}
                                                fill={SIN_DATOS} fillOpacity={1} ifOverflow="hidden"
                                                label={{ value: 'Sin datos', position: 'insideTop', fontSize: 10, fill: '#A0AEC0' }}
                                            />
                                        ))}
                                        <ReferenceLine y={UMBRAL_NORMAL} stroke={SEVERIDAD.BAJA.bar} strokeDasharray="4 4" strokeOpacity={0.7} />
                                        <ReferenceLine y={UMBRAL_CRITICO} stroke={SEVERIDAD.ALTA.bar} strokeDasharray="4 4" strokeOpacity={0.6} />
                                        <Tooltip content={<CohesionTooltip />} />
                                        <Line dataKey="rodeo" stroke={GRIS} strokeWidth={1.5} strokeDasharray="5 4" dot={false} connectNulls={false} isAnimationActive={false} />
                                        <Line dataKey="valor" stroke={NAVY} strokeWidth={2.5} dot={false} connectNulls={false} activeDot={{ r: 5, fill: '#fff', stroke: NAVY, strokeWidth: 2.5 }} />
                                        <ReferenceDot x={n - 1} y={data.actual} r={5} fill={estado.color} stroke="#fff" strokeWidth={2} />
                                    </LineChart>
                                </ResponsiveContainer>
                                <div className="d-flex flex-wrap gap-3 mt-2" style={{ fontSize: 12, color: '#5A5A5A', paddingLeft: 24 }}>
                                    <Leyenda label="Este animal" linea={`3px solid ${NAVY}`} />
                                    <Leyenda label="Promedio del rodeo" linea={`2px dashed ${GRIS}`} />
                                    <Leyenda label={`Umbral cohesionado (${UMBRAL_NORMAL})`} linea={`2px dashed ${SEVERIDAD.BAJA.bar}`} />
                                    <Leyenda label={`Umbral alejado (${UMBRAL_CRITICO})`} linea={`2px dashed ${SEVERIDAD.ALTA.bar}`} />
                                    <Leyenda label="Sin datos" swatch={SIN_DATOS} />
                                </div>
                            </div>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
