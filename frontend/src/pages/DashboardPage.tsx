/* eslint-disable react-hooks/set-state-in-effect */
import { useState, useEffect, useMemo, useCallback } from 'react';
import { Spinner, Alert, OverlayTrigger, Tooltip } from 'react-bootstrap';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

import { getAnimales, getPesosEstablecimiento, ESTADO_REPRODUCTIVO_LABEL } from '../api/animalesApi';
import type { AnimalResponse, IntervencionResponse, RegistroPesoResponse } from '../api/animalesApi';
import {
    calcularGananciaPeso, ADPV_PIERDE, COBERTURA_BAJA_PCT, DIAS_COBERTURA, DIAS_COMPARACION, DIAS_PESADA_VIGENTE,
} from '../utils/gananciaPeso';
import { esAnestro, tieneDatoDesactualizado, textoAccionRecomendada } from '../api/alertaApi';
import type { AlertaResponse } from '../api/alertaApi';
import { getRodeoLotes } from '../api/rodeoLotesApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import { getAnimalesMapa } from '../api/animalesApi';
import { getCentroMando, getResumenEjecutivo, getSaludReproduccion } from '../api/dashboardApi';
import type {
    AnimalResumen, CentroMandoResponse, ResumenEjecutivoResponse, SaludReproduccionResponse,
} from '../api/dashboardApi';
import type { AnimalMapa } from '../api/animalesApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { determinarIcono } from '../utils/animalPins';
import AlertDetailSidebar from '../components/AlertDetailSidebar';
import IntervencionFormPanel from '../components/IntervencionFormPanel';
import './DashboardPage.css';

// ── Helpers ──────────────────────────────────────────────────────────────────

const PERIODOS_DIAS = [7, 30, 90];  // opciones del selector de período (Salud y Resumen ejecutivo)
const PERIODO_DEFAULT_DIAS = 30;

// Card "Ganancia de peso"
const ADPV_AYUDA = 'ADPV: aumento diario de peso vivo, en kg por animal por día.';
const GRUPOS_PESO = [
    { key: 'gana', label: 'Gana' },
    { key: 'estable', label: 'Estable' },
    { key: 'pierde', label: 'Pierde' },
] as const;
const REVISAR_MAX = 5;              // filas por sub-sección en "Animales a revisar"
const CELOS_MAX = 3;                // filas de celos con ventana abierta en "Embudo reproductivo"

const TIPO_INTERVENCION_LABEL: Record<IntervencionResponse['tipo'], string> = {
    TRATAMIENTO: 'Tratamiento',
    VACUNACION: 'Vacunación',
    REVISION_VETERINARIA: 'Revisión veterinaria',
    INSEMINACION: 'Inseminación',
    OTRO: 'Otro',
};

function horasTranscurridas(ts: string): number {
    return (Date.now() - new Date(ts).getTime()) / (1000 * 60 * 60);
}

function nombreAnimal(a: { id: number; alias?: string | null; idCaravanaOficial?: string | null } | null | undefined): string {
    if (!a) return '—';
    return a.alias || a.idCaravanaOficial || `#${a.id}`;
}

// Tooltip de partos: encabezado + hasta 5 nombres
function tooltipPartos(header: string, animales: AnimalResumen[]): string {
    const MAX_SHOW = 5;
    const lineas = animales.slice(0, MAX_SHOW).map(a => `• ${nombreAnimal(a)}`);
    if (animales.length > MAX_SHOW) lineas.push(`• …y ${animales.length - MAX_SHOW} más`);
    return [header, ...lineas].join('\n');
}

function formatHoras(horas: number): string {
    if (horas < 1) return `${Math.round(horas * 60)}min`;
    if (horas < 24) return `${Math.floor(horas)}h`;
    return `${Math.floor(horas / 24)}d ${Math.floor(horas % 24)}h`;
}

function formatHorasRestantes(seg: number): string {
    const h = Math.floor(seg / 3600);
    const m = Math.floor((seg % 3600) / 60);
    if (h > 0) return `${h}h ${m}min restantes`;
    return `${m}min restantes`;
}

// ── Colores ventana ───────────────────────────────────────────────────────────
function ventanaClass(seg: number): 'critica' | 'venciendo' | 'optima' {
    const h = seg / 3600;
    if (h <= 3) return 'critica';
    if (h <= 6) return 'venciendo';
    return 'optima';
}

// ── MapFitter ─────────────────────────────────────────────────────────────────
function MapFitter({ animales }: { animales: AnimalMapa[] }) {
    const map = useMap();
    useEffect(() => {
        const puntos = animales.filter(a => a.latitud && a.longitud);
        if (puntos.length === 0) return;
        const bounds = L.latLngBounds(puntos.map(a => [a.latitud!, a.longitud!] as [number, number]));
        map.fitBounds(bounds, { padding: [20, 20], maxZoom: 14 });
    }, [animales, map]);
    return null;
}

// ── Types ─────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────

// ── Colores del trend chart ──────────────────────────────────────────────
const TREND_COLORS = {
    CELO: '#1A3558',
    SALUD: '#3B8D99',
    FUERA_DE_PERIMETRO: '#13D4FB',
} as const;

// ── SVG line chart helper ─────────────────────────────────────────────
function TrendChart({ datos, semanas, tipos }: {
    datos: Record<string, number[]>;
    semanas: string[];
    tipos: string[];
}) {
    const W = 480; const H = 130; const PAD = { t: 10, r: 10, b: 28, l: 36 };
    const allVals = tipos.flatMap(t => datos[t] as number[]);
    const maxVal = Math.max(...allVals, 1);
    const minVal = 0;
    const xStep = (W - PAD.l - PAD.r) / Math.max(semanas.length - 1, 1);
    const yRange = H - PAD.t - PAD.b;

    function toX(i: number) { return PAD.l + i * xStep; }
    function toY(v: number) { return PAD.t + yRange - ((v - minVal) / (maxVal - minVal)) * yRange; }
    function polyline(vals: number[]) {
        return vals.map((v, i) => `${toX(i)},${toY(v)}`).join(' ');
    }

    const COLORS: Record<string, string> = {
        CELO: TREND_COLORS.CELO,
        SALUD: TREND_COLORS.SALUD,
        FUERA_DE_PERIMETRO: TREND_COLORS.FUERA_DE_PERIMETRO,
    };
    const LABELS: Record<string, string> = {
        CELO: 'Celo', SALUD: 'Salud', FUERA_DE_PERIMETRO: 'Perímetro',
    };

    // Y grid
    const gridVals = [0, Math.round(maxVal / 2), maxVal];

    return (
        <>
            <div className="dash-trend-svg-wrap">
                <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: 'auto', display: 'block' }}>
                    {/* Grid */}
                    {gridVals.map(v => (
                        <g key={v}>
                            <line
                                x1={PAD.l} x2={W - PAD.r}
                                y1={toY(v)} y2={toY(v)}
                                stroke="#E2E8F0" strokeWidth={1}
                            />
                            <text x={PAD.l - 6} y={toY(v) + 4}
                                textAnchor="end" fontSize={9} fill="#94A3B8">{v}</text>
                        </g>
                    ))}
                    {/* X axis labels (abbreviated week) */}
                    {semanas.map((s, i) => (
                        <text key={s}
                            x={toX(i)} y={H - 4}
                            textAnchor="middle" fontSize={9} fill="#94A3B8">
                            S{s.split('-W')[1]}
                        </text>
                    ))}
                    {/* Lines */}
                    {tipos.map(tipo => (
                        <polyline
                            key={tipo}
                            points={polyline(datos[tipo] as number[])}
                            fill="none"
                            stroke={COLORS[tipo] ?? '#CBD5E1'}
                            strokeWidth={2}
                            strokeLinejoin="round"
                            strokeLinecap="round"
                        />
                    ))}
                    {/* Dots */}
                    {tipos.map(tipo =>
                        (datos[tipo] as number[]).map((v, i) => (
                            <circle key={`${tipo}-${i}`}
                                cx={toX(i)} cy={toY(v)} r={3}
                                fill={COLORS[tipo] ?? '#CBD5E1'}
                            />
                        ))
                    )}
                </svg>
            </div>
            <div className="dash-trend-legend">
                {tipos.map(tipo => (
                    <span key={tipo} className="dash-trend-legend-item">
                        <span className="dash-trend-legend-dot" style={{ background: COLORS[tipo] ?? '#CBD5E1' }} />
                        {LABELS[tipo] ?? tipo}
                    </span>
                ))}
            </div>
        </>
    );
}

type DashTab = 'mando' | 'ejecutivo' | 'salud';

export default function DashboardPage() {
    const selectedId = useEstablecimientoStore(s => s.selectedId);
    const navigate = useNavigate();

    // La pestaña vive en la URL (?tab=salud): al volver con "atrás" desde otra página se restaura.
    // `replace` para que cambiar de pestaña no agregue entradas al historial.
    const [searchParams, setSearchParams] = useSearchParams();
    const tabParam = searchParams.get('tab');
    const activeTab: DashTab = tabParam === 'salud' || tabParam === 'ejecutivo' ? tabParam : 'mando';
    const setActiveTab = (tab: DashTab) => setSearchParams(prev => {
        if (tab === 'mando') prev.delete('tab'); else prev.set('tab', tab);
        return prev;
    }, { replace: true });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [loteFilter, setLoteFilter] = useState<number | 'todos'>('todos');
    const [periodoDias, setPeriodoDias] = useState(PERIODO_DEFAULT_DIAS);
    const [partosVista, setPartosVista] = useState<'semanas' | 'anio'>('semanas');

    // Raw data
    const [animales, setAnimales] = useState<AnimalResponse[]>([]);
    const [rodeos, setRodeos] = useState<RodeoLoteResponse[]>([]);
    const [animalesMapa, setAnimalesMapa] = useState<AnimalMapa[]>([]);

    // Pesadas del establecimiento (null si no se pudieron cargar)
    const [pesos, setPesos] = useState<RegistroPesoResponse[] | null>([]);

    // Alerta abierta en el panel de detalle, y la que se está interviniendo
    const [alertaAbierta, setAlertaAbierta] = useState<AlertaResponse | null>(null);
    const [intervencionAlerta, setIntervencionAlerta] = useState<AlertaResponse | null>(null);

    // Centro de mando, calculado en el backend
    const [centroMando, setCentroMando] = useState<CentroMandoResponse | null>(null);
    const [loadingMando, setLoadingMando] = useState(true);
    const [errorMando, setErrorMando] = useState<string | null>(null);

    // Salud y reproducción, calculado en el backend
    const [saludRepro, setSaludRepro] = useState<SaludReproduccionResponse | null>(null);
    const [loadingSalud, setLoadingSalud] = useState(true);
    const [errorSalud, setErrorSalud] = useState<string | null>(null);

    // Resumen ejecutivo, calculado en el backend
    const [resumen, setResumen] = useState<ResumenEjecutivoResponse | null>(null);
    const [loadingResumen, setLoadingResumen] = useState(true);
    const [errorResumen, setErrorResumen] = useState<string | null>(null);

    const fetchDashboard = useCallback(async () => {
        if (!selectedId) return;
        setLoading(true);
        setError(null);
        try {
            const [anim, rodeosData, mapaData, pesosData] = await Promise.all([
                getAnimales(selectedId),
                getRodeoLotes(selectedId),
                getAnimalesMapa(selectedId),
                // Si falla, el resto del dashboard sigue funcionando.
                getPesosEstablecimiento(selectedId).catch(() => null),
            ]);
            setAnimales(anim);
            setPesos(pesosData);
            setRodeos(rodeosData.filter(r => r.activo));
            setAnimalesMapa(mapaData);
        } catch (e) {
            const err = e as { response?: { data?: { message?: string } } };
            setError(err.response?.data?.message || 'Error al cargar el dashboard.');
        } finally {
            setLoading(false);
        }
    }, [selectedId]);

    useEffect(() => { fetchDashboard(); }, [fetchDashboard]);

    const fetchCentroMando = useCallback(async () => {
        if (!selectedId) return;
        setLoadingMando(true);
        setErrorMando(null);
        try {
            setCentroMando(await getCentroMando(selectedId, loteFilter === 'todos' ? undefined : loteFilter));
        } catch (e) {
            const err = e as { response?: { data?: string | { message?: string } } };
            const data = err.response?.data;
            setErrorMando((typeof data === 'string' ? data : data?.message) || 'Error al cargar el centro de mando.');
        } finally {
            setLoadingMando(false);
        }
    }, [selectedId, loteFilter]);

    useEffect(() => { fetchCentroMando(); }, [fetchCentroMando]);

    const fetchSaludRepro = useCallback(async () => {
        if (!selectedId) return;
        setLoadingSalud(true);
        setErrorSalud(null);
        try {
            setSaludRepro(await getSaludReproduccion(selectedId, periodoDias,
                loteFilter === 'todos' ? undefined : loteFilter));
        } catch (e) {
            const err = e as { response?: { data?: string | { message?: string } } };
            const data = err.response?.data;
            setErrorSalud((typeof data === 'string' ? data : data?.message) || 'Error al cargar salud y reproducción.');
        } finally {
            setLoadingSalud(false);
        }
    }, [selectedId, loteFilter, periodoDias]);

    useEffect(() => { fetchSaludRepro(); }, [fetchSaludRepro]);

    const fetchResumen = useCallback(async () => {
        if (!selectedId) return;
        setLoadingResumen(true);
        setErrorResumen(null);
        try {
            setResumen(await getResumenEjecutivo(selectedId, periodoDias));
        } catch (e) {
            const err = e as { response?: { data?: string | { message?: string } } };
            const data = err.response?.data;
            setErrorResumen((typeof data === 'string' ? data : data?.message) || 'Error al cargar el resumen ejecutivo.');
        } finally {
            setLoadingResumen(false);
        }
    }, [selectedId, periodoDias]);

    useEffect(() => { fetchResumen(); }, [fetchResumen]);

    const refrescar = () => {
        fetchDashboard();
        fetchCentroMando();
        fetchSaludRepro();
        fetchResumen();
    };

    // ── Filtrado por lote ────────────────────────────────────────────────────
    const animalesFiltrados = useMemo(() =>
        loteFilter === 'todos' ? animales : animales.filter(a => a.rodeoLoteId === loteFilter),
        [animales, loteFilter]);

    // ── Salud tab: Partos esperados — vista anual (estilo GitHub) ────────────
    const partosAnio = useMemo(() => {
        const porDia = new Map((saludRepro?.partos.porDia ?? []).map(d => [d.fecha, d.animales]));
        const clave = (f: Date) =>
            `${f.getFullYear()}-${String(f.getMonth() + 1).padStart(2, '0')}-${String(f.getDate()).padStart(2, '0')}`;

        // 53 columnas (semanas) × 7 filas (lun..dom), arrancando el lunes de esta semana
        const hoy = new Date();
        hoy.setHours(0, 0, 0, 0);
        const lunes = hoy.getDate() - ((hoy.getDay() + 6) % 7);
        const dias = Array.from({ length: 53 * 7 }, (_, i) => {
            const fecha = new Date(hoy.getFullYear(), hoy.getMonth(), lunes + i);
            return { fecha, animales: porDia.get(clave(fecha)) ?? [], pasado: fecha < hoy };
        });
        const maxDia = Math.max(...dias.map(d => d.animales.length), 1);

        return { dias, maxDia };
    }, [saludRepro]);

    // ── Salud tab: Ganancia de peso (null si no se pudieron cargar las pesadas) ──
    const gananciaPeso = useMemo(() =>
        pesos && calcularGananciaPeso(animalesFiltrados, pesos),
        [animalesFiltrados, pesos]);

    // Abre el listado de animales filtrado por estos ids; `motivo` se muestra en el chip del filtro
    const verListaAnimales = (lista: { id: number }[], motivo: string) =>
        navigate(`/animales?${new URLSearchParams({ ids: lista.map(a => a.id).join(','), motivo })}`);

    // ── Render ────────────────────────────────────────────────────────────────
    const cm = centroMando;

    // ── Resumen ejecutivo view ────────────────────────────────────────────
    function ResumenEjecutivoView() {
        const re = resumen;
        if (!re) {
            return errorResumen
                ? <Alert variant="danger" style={{ borderRadius: 10, maxWidth: 1280, margin: '0 auto 16px' }}>
                    <i className="bi bi-exclamation-triangle me-2" />{errorResumen}
                </Alert>
                : <div className="dash-loading-center" style={{ padding: 60 }}><Spinner animation="border" /></div>;
        }

        // Umbrales para colorear celdas
        const COB_UMBRAL_BAJO = 80; // < 80% cobertura → rojo
        const ALERTAS100_UMBRAL = re.promedio.alertasCada100 * 1.4; // >40% sobre la media → rojo
        const VACIAS90_UMBRAL = 15; // >15% vacías >90d → amarillo

        function cobCell(pct: number) {
            const cls = pct < COB_UMBRAL_BAJO ? 'dash-exec-badge-red' : '';
            if (!cls) return <span>{pct}%</span>;
            return <span className={`dash-exec-cell-badge ${cls}`}>{pct}%</span>;
        }

        function alertas100Cell(val: number, pend: number) {
            const cls = val > ALERTAS100_UMBRAL ? 'dash-exec-badge-red' : '';
            if (!cls) return <span>{pend} <small style={{ color: '#94A3B8', fontSize: '0.75rem' }}>({val}/100)</small></span>;
            return (
                <span className={`dash-exec-cell-badge ${cls}`}>
                    {pend} <small style={{ fontSize: '0.75rem', marginLeft: 2 }}>▲ {val}/100</small>
                </span>
            );
        }

        function vacias90Cell(pct: number) {
            const cls = pct > VACIAS90_UMBRAL ? 'dash-exec-badge-yellow' : '';
            if (!cls) return <span>{pct}%</span>;
            return <span className={`dash-exec-cell-badge ${cls}`}>{pct}%</span>;
        }

        const maxHist = Math.max(...re.equipo.tiempoAtencion.map(b => b.alertas), 1);

        return (
            <>
                {/* ── CARD HERO: tabla por rodeo ─────────────────────── */}
                <div className="dash-exec-hero">
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-table" />
                                Información por rodeo
                            </p>
                        </div>

                        {re.rodeos.length === 0 ? (
                            <p className="dash-no-items">
                                <i className="bi bi-info-circle" style={{ color: '#94A3B8' }} />
                                No hay rodeos activos registrados
                            </p>
                        ) : (
                            <>
                                <table className="dash-exec-table">
                                    <thead>
                                        <tr>
                                            <th>Rodeo</th>
                                            <th>Animales</th>
                                            <th>Cobertura</th>
                                            <th>Alertas pend.</th>
                                            <th>Alertas /100</th>
                                            <th>Vent. perdidas</th>
                                            <th>% vacías &gt; {re.umbrales.diasVaciaLarga} d</th>
                                            <th></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {re.rodeos.map(row => (
                                            <tr key={row.id}>
                                                <td>
                                                    <span className="dash-exec-rodeo-name">{row.nombre}</span>
                                                </td>
                                                <td className="dash-exec-cell-normal">{row.animales}</td>
                                                <td>{cobCell(row.coberturaPorcentaje)}</td>
                                                <td className="dash-exec-cell-normal">{row.alertasPendientes}</td>
                                                <td>{alertas100Cell(row.alertasCada100, row.alertasPendientes)}</td>
                                                <td className="dash-exec-cell-normal">{row.ventanasPerdidas}</td>
                                                <td>{vacias90Cell(row.vaciasLargasPorcentaje)}</td>
                                                <td>
                                                    <button
                                                        className="dash-exec-ver-btn"
                                                        onClick={() => navigate(`/animales?rodeo=${row.id}`)}
                                                    >
                                                        Ver
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </>
                        )}
                    </div>
                </div>

                {/* ── GRID INFERIOR: tendencias + equipo ───────────────── */}
                <div className="dash-exec-bottom-grid">

                    {/* ── IDEA 8: Tendencia 8 semanas ──────────────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-graph-up" />
                                Tendencia de alertas · 8 semanas
                            </p>
                        </div>

                        {re.tendencia.tipos.every(t => re.tendencia.datos[t].every(v => v === 0)) ? (
                            <p className="dash-no-items">
                                <i className="bi bi-bar-chart" style={{ color: '#94A3B8' }} />
                                Sin datos de alertas en las últimas 8 semanas
                            </p>
                        ) : (
                            <TrendChart
                                datos={re.tendencia.datos}
                                semanas={re.tendencia.semanas}
                                tipos={re.tendencia.tipos}
                            />
                        )}
                    </div>

                    {/* ── IDEA 9: ¿Está actuando el equipo? ────────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-people-fill" />
                                ¿Está actuando el equipo?
                            </p>
                        </div>

                        <div className="dash-team-kpis">
                            <div className="dash-team-kpi">
                                <span className="dash-team-kpi-label">En cola</span>
                                <span className="dash-team-kpi-val">{re.equipo.enCola}</span>
                                <span className="dash-team-kpi-sub">pendientes ahora</span>
                            </div>
                            <div className="dash-team-kpi">
                                <span className="dash-team-kpi-label">Atendidas</span>
                                <span className="dash-team-kpi-val">{re.equipo.porcentajeAtendidas}%</span>
                                <span className="dash-team-kpi-sub">de las generadas</span>
                            </div>
                            <div className="dash-team-kpi">
                                <span className="dash-team-kpi-label">Descartadas</span>
                                <span className="dash-team-kpi-val">{re.equipo.porcentajeDescartadas}%</span>
                                <span className="dash-team-kpi-sub">de las atendidas</span>
                            </div>
                        </div>

                        <div className="dash-team-hist-label">
                            Tiempo de atención
                        </div>
                        {re.equipo.totalAtendidas === 0 ? (
                            <p className="dash-no-items">
                                <i className="bi bi-hourglass" style={{ color: '#94A3B8' }} />
                                Sin alertas atendidas en los últimos {periodoDias} días
                            </p>
                        ) : (
                            <div className="dash-team-hist">
                                {re.equipo.tiempoAtencion.map(bucket => (
                                    <div key={bucket.rango} className="dash-team-hist-bar-wrap">
                                        <span className="dash-team-hist-count">{bucket.alertas}</span>
                                        <div
                                            className="dash-team-hist-bar"
                                            style={{ height: `${Math.round((bucket.alertas / maxHist) * 52)}px` }}
                                            title={`${bucket.alertas} alertas`}
                                        />
                                        <span className="dash-team-hist-bar-label">{bucket.rango}</span>
                                    </div>
                                ))}
                            </div>
                        )}
                        <span className="dash-team-hist-sub">
                            Desde que se generó la alerta hasta que se confirmó o descartó · últimos {periodoDias} días
                        </span>
                    </div>
                </div>

                {/* ══ DEBAJO DEL PLIEGUE ══════════════════════════════════════ */}
                <div className="dash-below-grid">

                    {/* ── Idea 12: Actividad del rodeo (Placeholder) ──────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-activity" />
                                Actividad del rodeo
                            </p>
                        </div>
                        <div className="dash-salud-peso-empty" style={{ padding: '40px 16px' }}>
                            <i className="bi bi-graph-down" style={{ fontSize: '1.6rem', color: '#CBD5E1' }} />
                            <span className="dash-salud-peso-empty-text">
                                Disponible próximamente
                            </span>
                            <span className="dash-salud-peso-empty-sub">
                                Módulo de actividad y desvíos en desarrollo
                            </span>
                        </div>
                    </div>

                    {/* ── Idea 15: Dónde ocurren (Placeholder) ────────────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-pin-map" />
                                Dónde ocurren
                            </p>
                        </div>
                        <div className="dash-salud-peso-empty" style={{ padding: '40px 16px' }}>
                            <i className="bi bi-map" style={{ fontSize: '1.6rem', color: '#CBD5E1' }} />
                            <span className="dash-salud-peso-empty-text">
                                Disponible próximamente
                            </span>
                            <span className="dash-salud-peso-empty-sub">
                                Mapa de calor espacial en desarrollo
                            </span>
                        </div>
                    </div>

                    {/* ── Idea 16: Carga animal (Placeholder) ─────────────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-pie-chart" />
                                Carga animal
                            </p>
                        </div>
                        <div className="dash-salud-peso-empty" style={{ padding: '40px 16px' }}>
                            <i className="bi bi-grid-3x3" style={{ fontSize: '1.6rem', color: '#CBD5E1' }} />
                            <span className="dash-salud-peso-empty-text">
                                Disponible próximamente
                            </span>
                            <span className="dash-salud-peso-empty-sub">
                                Análisis de uso de parcelas en desarrollo
                            </span>
                        </div>
                    </div>
                </div>
            </>
        );
    }

    // ── Salud y reproducción del rodeo view ──────────────────────────────
    function SaludReproduccionView() {
        const sr = saludRepro;
        if (!sr) {
            return errorSalud
                ? <Alert variant="danger" style={{ borderRadius: 10, maxWidth: 1280, margin: '0 auto 16px' }}>
                    <i className="bi bi-exclamation-triangle me-2" />{errorSalud}
                </Alert>
                : <div className="dash-loading-center" style={{ padding: 60 }}><Spinner animation="border" /></div>;
        }

        const d = sr.desempenyo;
        // Semana "dd/mm" desde su primer día ('YYYY-MM-DD')
        const semanasPartos = sr.partos.semanas.map(s => ({
            label: `${s.desde.slice(8, 10)}/${s.desde.slice(5, 7)}`,
            count: s.animales.length,
            animales: s.animales,
        }));
        const maxParto = Math.max(...semanasPartos.map(s => s.count), 1);
        const maxDesempenyo = Math.max(d.celosDetectados, d.celosAtendidos, d.inseminadas, d.gestantes, 1);

        return (
            <>
                {/* ── STRIP DE 4 KPIs ────────────────────────────────────── */}
                <div className="dash-salud-kpi-row">
                    <div className="dash-salud-kpi">
                        <span className="dash-salud-kpi-label">Días abiertos (promedio)</span>
                        <span className="dash-salud-kpi-val">{sr.indicadores.diasAbiertosPromedio}</span>
                        <span className="dash-salud-kpi-sub">
                            {sr.indicadores.vacasAbiertas > 0 ? `${sr.indicadores.vacasAbiertas} vacas abiertas con parto registrado` : 'sin vacas abiertas con parto registrado'}
                        </span>
                    </div>
                    <div className="dash-salud-kpi">
                        <span className="dash-salud-kpi-label">Ventanas perdidas ({periodoDias} d)</span>
                        <span className="dash-salud-kpi-val dash-salud-kpi-warn">
                            {d.celosDetectados > 0
                                ? `${Math.round((d.ventanasPerdidas / d.celosDetectados) * 100)}%`
                                : '0%'
                            }
                        </span>
                        <span className="dash-salud-kpi-sub">{d.ventanasPerdidas} de {d.celosDetectados} celos</span>
                    </div>
                    <div className="dash-salud-kpi">
                        <span className="dash-salud-kpi-label">Salud sin atender</span>
                        <span className={`dash-salud-kpi-val ${sr.indicadores.saludSinAtender > 0 ? 'dash-salud-kpi-danger' : ''}`}>
                            {sr.indicadores.saludSinAtender}
                        </span>
                        <span className="dash-salud-kpi-sub">&gt; {sr.umbrales.horasSaludSinAtender} h desde la alerta</span>
                    </div>
                    <div className="dash-salud-kpi">
                        <span className="dash-salud-kpi-label">Celos sin atender</span>
                        <span className={`dash-salud-kpi-val ${sr.indicadores.celosPorVencer > 0 ? 'dash-salud-kpi-danger' : ''}`}>
                            {sr.indicadores.celosSinAtender}
                        </span>
                        <span className="dash-salud-kpi-sub">
                            {sr.indicadores.celosPorVencer > 0
                                ? `${sr.indicadores.celosPorVencer} por vencer en < ${sr.umbrales.horasVentanaPorVencer}h`
                                : 'sin urgencia inmediata'}
                        </span>
                    </div>
                </div>

                {/* ── GRID PRINCIPAL (2 cols) ─────────────────────────────── */}
                <div className="dash-salud-main-grid">

                    {/* ── IZQUIERDA: Embudo reproductivo ───────────────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-diagram-3-fill" />
                                Embudo reproductivo
                            </p>

                        </div>

                        {/* Celos con ventana abierta: lo más urgente del rodeo reproductivo, primero */}
                        <div className="dash-salud-revisar-section">
                            <span className="dash-salud-revisar-seclabel">Celos con ventana abierta</span>
                            {sr.ventanas.length === 0 ? (
                                <p className="dash-no-items" style={{ margin: '6px 0' }}>
                                    <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                    {' '}Sin celos con ventana abierta
                                </p>
                            ) : (
                                sr.ventanas.slice(0, CELOS_MAX).map(({ alerta, animal, ventana }) => {
                                    const isFutura = ventana.estado === 'FUTURA';
                                    const vc = isFutura ? 'futura' : ventanaClass(ventana.tiempoRestanteSegundos);
                                    const restante = formatHorasRestantes(ventana.tiempoRestanteSegundos).replace(' restantes', '');
                                    return (
                                        <div
                                            key={alerta.id}
                                            className="dash-salud-revisar-row"
                                            onClick={() => setAlertaAbierta(alerta)}
                                        >
                                            <span className={`dash-sev-dot dash-ventana-bar-${vc}`} />
                                            <span className="dash-salud-revisar-nombre">{nombreAnimal(animal)}</span>
                                            <span className={`dash-salud-revisar-tiempo ${vc === 'critica' ? 'dash-salud-revisar-dias-urgente' : ''}`}>
                                                {isFutura ? `abre en ${restante}` : `cierra en ${restante}`}
                                            </span>
                                        </div>
                                    );
                                })
                            )}
                            {sr.ventanas.length > CELOS_MAX && (
                                <button className="dash-ver-todas-btn" onClick={() => setActiveTab('mando')}>
                                    +{sr.ventanas.length - CELOS_MAX} más — ver en Centro de mando
                                </button>
                            )}
                        </div>
                        <div className="dash-salud-revisar-divider" />

                        {sr.embudo.totalReproductivos === 0 ? (
                            <p className="dash-no-items">Sin animales reproductivos registrados</p>
                        ) : (
                            <>
                                {/* Barra horizontal */}
                                <div className="dash-embudo-bar-wrap">
                                    {Object.entries(sr.embudo.distribucion)
                                        .filter(([, v]) => v > 0)
                                        .map(([estado, count]) => (
                                            <div
                                                key={estado}
                                                className={`dash-embudo-seg dash-embudo-${estado.toLowerCase()}`}
                                                style={{ flex: count }}
                                                title={`${ESTADO_REPRODUCTIVO_LABEL[estado] ?? estado.replace('_', ' ')}: ${count}`}
                                            />
                                        ))}
                                </div>
                                <div className="dash-embudo-legend">
                                    {Object.entries(sr.embudo.distribucion).filter(([, v]) => v > 0).map(([estado, count]) => (
                                        <span key={estado} className="dash-embudo-leg-item">
                                            <span
                                                className={`dash-dot dash-embudo-${estado.toLowerCase()}`}
                                                style={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%' }}
                                            />
                                            {ESTADO_REPRODUCTIVO_LABEL[estado] ?? estado.replace('_', ' ')}
                                            <strong>{count}</strong>
                                        </span>
                                    ))}
                                </div>

                                {/* Excepciones */}
                                <div className="dash-salud-excepciones">
                                    <span className="dash-salud-exc-header">Excepciones que necesitan decisión</span>

                                    <div className="dash-salud-exc-row">
                                        <div className="dash-salud-exc-desc">
                                            <span className="dash-salud-exc-state vacia">VACÍA</span>
                                            {' '}con &gt; {sr.umbrales.diasVaciaLarga} días abiertos
                                        </div>
                                        <div className="dash-salud-exc-action">
                                            <strong>{sr.embudo.vaciasLargas.length}</strong>
                                            {sr.embudo.vaciasLargas.length > 0 && (
                                                <button
                                                    className="dash-ver-todas-btn"
                                                    onClick={() => verListaAnimales(sr.embudo.vaciasLargas, `Vacías con > ${sr.umbrales.diasVaciaLarga} días abiertos`)}
                                                >
                                                    — ver lista
                                                </button>
                                            )}
                                        </div>
                                    </div>

                                    <div className="dash-salud-exc-row">
                                        <div className="dash-salud-exc-desc">
                                            <span className="dash-salud-exc-state inseminada">INSEMINADA</span>
                                            {' '}sin diagnóstico &gt; {sr.umbrales.diasDiagnosticoInseminada} d
                                        </div>
                                        <div className="dash-salud-exc-action">
                                            <strong>{sr.embudo.inseminadasSinDiagnostico.length}</strong>
                                            {sr.embudo.inseminadasSinDiagnostico.length > 0 && (
                                                <button
                                                    className="dash-ver-todas-btn"
                                                    onClick={() => verListaAnimales(sr.embudo.inseminadasSinDiagnostico, `Inseminadas sin diagnóstico > ${sr.umbrales.diasDiagnosticoInseminada} d`)}
                                                >
                                                    — ver lista
                                                </button>
                                            )}
                                        </div>
                                    </div>

                                    <div className="dash-salud-exc-row">
                                        <div className="dash-salud-exc-desc">
                                            <span className="dash-salud-exc-state anestro">ANESTRO</span>
                                            {' '}con alerta sin intervención
                                        </div>
                                        <div className="dash-salud-exc-action">
                                            <strong>{sr.embudo.anestroSinIntervencion.length}</strong>
                                            {sr.embudo.anestroSinIntervencion.length > 0 && (
                                                <button
                                                    className="dash-ver-todas-btn"
                                                    onClick={() => verListaAnimales(sr.embudo.anestroSinIntervencion, 'Anestro con alerta sin intervención')}
                                                >
                                                    — ver lista
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                </div>

                                <div className="dash-salud-completitud">
                                    Completitud del dato: {sr.embudo.completitudPorcentaje}% de los animales con estado registrado
                                </div>
                            </>
                        )}
                    </div>

                    {/* ── DERECHA: Animales a revisar ─────────────────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-heart-pulse-fill" />
                                Animales a revisar
                            </p>

                        </div>

                        {/* Sub-sección: Alertas sin atender */}
                        <div className="dash-salud-revisar-section">
                            <span className="dash-salud-revisar-seclabel">Alertas sin atender</span>
                            {sr.alertasSaludSinAtender.length === 0 ? (
                                <p className="dash-no-items" style={{ margin: '6px 0' }}>
                                    <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                    {' '}Sin alertas de salud pendientes
                                </p>
                            ) : (
                                sr.alertasSaludSinAtender.slice(0, REVISAR_MAX).map(({ alerta, animal }) => {
                                    const h = horasTranscurridas(alerta.tsGeneracion);
                                    const dias = Math.floor(h / 24);
                                    const sevDot = alerta.severidad === 'ALTA' ? 'dash-sev-dot-alta'
                                        : alerta.severidad === 'MEDIA' ? 'dash-sev-dot-media'
                                            : 'dash-sev-dot-baja';
                                    return (
                                        <div
                                            key={alerta.id}
                                            className="dash-salud-revisar-row"
                                            onClick={() => setAlertaAbierta(alerta)}
                                        >
                                            <span className={`dash-sev-dot ${sevDot}`} />
                                            <span className="dash-salud-revisar-nombre">{nombreAnimal(animal)}</span>
                                            <span className={`dash-salud-revisar-tiempo ${h > sr.umbrales.horasSaludSinAtender ? 'dash-salud-revisar-dias-urgente' : ''}`}>
                                                {dias > 0 ? `${dias} d` : `${Math.floor(h)}h`}
                                            </span>
                                        </div>
                                    );
                                })
                            )}
                            {sr.alertasSaludSinAtender.length > REVISAR_MAX && (
                                <button className="dash-ver-todas-btn" onClick={() => navigate('/alertas')}>
                                    +{sr.alertasSaludSinAtender.length - REVISAR_MAX} más — ver alertas
                                </button>
                            )}
                        </div>

                        <div className="dash-salud-revisar-divider" />

                        {/* Sub-sección: En observación / enfermos */}
                        <div className="dash-salud-revisar-section">
                            <span className="dash-salud-revisar-seclabel">En observación / enfermos</span>
                            {sr.enObservacion.length === 0 ? (
                                <p className="dash-no-items" style={{ margin: '6px 0' }}>
                                    <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                    {' '}Todos los animales sanos
                                </p>
                            ) : (
                                sr.enObservacion.slice(0, REVISAR_MAX).map(({ animal: a, estadoSalud, ultimaIntervencionTipo, diasSinIntervencion: diasSinRevision }) => {
                                    const esEnfermo = estadoSalud === 'ENFERMO';
                                    const sinRevisionLargo = diasSinRevision === null || diasSinRevision > 14;
                                    return (
                                        <div
                                            key={a.id}
                                            className="dash-salud-revisar-row"
                                            onClick={() => navigate(`/animales/${a.id}`)}
                                        >
                                            <span
                                                className="dash-sev-dot"
                                                style={{ background: esEnfermo ? '#E53E3E' : '#D69E2E' }}
                                            />
                                            <span className="dash-salud-revisar-nombre">{nombreAnimal(a)}</span>
                                            <span className="dash-salud-revisar-lote">{a.rodeoLoteNombre ?? ''}</span>
                                            <span
                                                className={`dash-salud-revisar-dias ${sinRevisionLargo ? 'dash-salud-revisar-dias-urgente' : ''}`}
                                                title={ultimaIntervencionTipo ? `Última intervención: ${TIPO_INTERVENCION_LABEL[ultimaIntervencionTipo]}` : undefined}
                                            >
                                                {diasSinRevision !== null ? `${diasSinRevision} d sin revisión` : 'sin intervenciones'}
                                            </span>
                                        </div>
                                    );
                                })
                            )}
                            {sr.enObservacion.length > REVISAR_MAX && (
                                <button className="dash-ver-todas-btn" onClick={() => navigate('/animales?tab=salud')}>
                                    +{sr.enObservacion.length - REVISAR_MAX} más — ver animales
                                </button>
                            )}
                        </div>
                    </div>
                </div>

                {/* ── GRID MEDIO (2 cols) ─────────────────────────────────── */}
                <div className="dash-salud-mid-grid">

                    {/* ── IZQUIERDA: Partos esperados ──────────────────────── */}
                    <div className="dash-card dash-partos-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-calendar2-event-fill" />
                                Partos esperados
                            </p>
                            <div className="dash-lote-filter">
                                <i className="bi bi-calendar3" style={{ fontSize: '0.7rem' }} />
                                <select
                                    className="dash-lote-select"
                                    value={partosVista}
                                    onChange={e => setPartosVista(e.target.value as 'semanas' | 'anio')}
                                    aria-label="Vista de partos esperados"
                                >
                                    <option value="semanas">Próximas 12 semanas</option>
                                    <option value="anio">Próximo año</option>
                                </select>
                            </div>
                        </div>

                        {sr.partos.totalGestantes === 0 ? (
                            <p className="dash-no-items">Sin animales gestantes registrados</p>
                        ) : (
                            <>
                                {partosVista === 'anio' ? (
                                    // Dos mitades de ~6 meses: lado a lado en PC, apiladas en celular
                                    <div className="dash-partos-anio">
                                        {[0, 27].map(desde => {
                                            const semanas = Array.from({ length: Math.min(27, 53 - desde) }, (_, k) => desde + k);
                                            return (
                                                <div key={desde} className="dash-partos-anio-mitad">
                                                    <div className="dash-partos-anio-meses">
                                                        {semanas.map(w => {
                                                            const primero = partosAnio.dias.slice(w * 7, w * 7 + 7).find(d => d.fecha.getDate() === 1);
                                                            return (
                                                                <span key={w}>
                                                                    {primero?.fecha.toLocaleDateString('es-AR', { month: 'short' })}
                                                                </span>
                                                            );
                                                        })}
                                                    </div>
                                                    <div className="dash-partos-anio-grid">
                                                        {partosAnio.dias.slice(desde * 7, (desde + semanas.length) * 7).map(d => {
                                                            const n = d.animales.length;
                                                            const nivel = n === 0 ? 0 : Math.ceil((n / partosAnio.maxDia) * 4);
                                                            const fecha = d.fecha.toLocaleDateString('es-AR', { weekday: 'short', day: '2-digit', month: '2-digit' });
                                                            return (
                                                                <div
                                                                    key={d.fecha.toDateString()}
                                                                    className={`dash-partos-cell ${d.pasado ? 'dash-partos-cell-off' : `dash-partos-cell-n${nivel}`}`}
                                                                    title={d.pasado ? undefined : n === 0 ? fecha
                                                                        : tooltipPartos(`📅 ${fecha} — ${n} parto${n > 1 ? 's' : ''} esperado${n > 1 ? 's' : ''}`, d.animales)}
                                                                    onClick={n > 0 ? () => verListaAnimales(d.animales, `Parto esperado el ${fecha}`) : undefined}
                                                                />
                                                            );
                                                        })}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                ) : (
                                    <div className="dash-partos-chart">
                                        {semanasPartos.map((sem, i) => (
                                            <div key={i} className={`dash-partos-bar-col ${sem.count > 0 ? 'dash-partos-bar-col-active' : ''}`}
                                                title={sem.count === 0 ? '' : tooltipPartos(
                                                    `📅 Semana ${sem.label} — ${sem.count} parto${sem.count > 1 ? 's' : ''} esperado${sem.count > 1 ? 's' : ''}`,
                                                    sem.animales
                                                )}
                                            >
                                                <div className="dash-partos-bar-track">
                                                    <div
                                                        className="dash-partos-bar-fill"
                                                        style={{ height: `${maxParto > 0 ? Math.round((sem.count / maxParto) * 100) : 0}%` }}
                                                    />
                                                </div>
                                                {sem.count > 0 && (
                                                    <span className="dash-partos-bar-count">{sem.count}</span>
                                                )}
                                                {i % 3 === 0 && (
                                                    <span className="dash-partos-bar-label">{sem.label}</span>
                                                )}
                                            </div>

                                        ))}
                                    </div>
                                )}
                                <div className="dash-partos-sub">
                                    {partosVista === 'anio' ? 'Por día, próximos 12 meses · tocá un día para ver las vacas' : 'Por semana, próximas 12 semanas'}
                                    {sr.partos.gestantesSinFecha > 0 && (
                                        <span className="dash-partos-subfecha">
                                            {' '}· {sr.partos.gestantesSinFecha} sin fecha de inseminación
                                        </span>
                                    )}
                                    {sr.partos.vencidos > 0 && (
                                        <span className="dash-partos-subfecha">
                                            {' '}· {sr.partos.vencidos} con fecha de parto vencida
                                        </span>
                                    )}
                                </div>
                            </>
                        )}
                    </div>

                    {/* ── DERECHA: Desempeño reproductivo ──────────────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-bar-chart-steps" />
                                Desempeño reproductivo
                            </p>

                        </div>

                        {d.reproductivos === 0 ? (
                            <p className="dash-no-items">Sin animales reproductivos registrados</p>
                        ) : (
                            <>
                                <div className="dash-desempenyo-funnel">
                                    {[
                                        { label: 'Celo detectado', val: d.celosDetectados, cls: 'dash-desempenyo-bar-celo' },
                                        { label: 'Atendido', val: d.celosAtendidos, cls: 'dash-desempenyo-bar-atend' },
                                        { label: 'Inseminado', val: d.inseminadas, cls: 'dash-desempenyo-bar-ins' },
                                        { label: 'Gestante', val: d.gestantes, cls: 'dash-desempenyo-bar-gest' },
                                    ].map(row => (
                                        <div key={row.label} className="dash-desempenyo-row">
                                            <span className="dash-desempenyo-label">{row.label}</span>
                                            <div className="dash-desempenyo-bar-track">
                                                <div
                                                    className={`dash-desempenyo-bar-fill ${row.cls}`}
                                                    style={{ width: `${Math.round((row.val / maxDesempenyo) * 100)}%` }}
                                                />
                                            </div>
                                            <span className="dash-desempenyo-val">{row.val}</span>
                                        </div>
                                    ))}
                                </div>
                                <div className="dash-desempenyo-note">
                                    Últimos {periodoDias} días · gestante = inseminada en el período con preñez ya confirmada
                                </div>
                            </>
                        )}
                    </div>
                </div>

                <div className="dash-salud-bottom-grid">

                    {/* ── Reincidencia (Idea 13) ───────────────────────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-arrow-repeat" />
                                Reincidencia
                            </p>

                        </div>

                        {sr.reincidencia.animales.length === 0 && sr.reincidencia.lotes.length === 0 ? (
                            <p className="dash-no-items">
                                <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                {' '}Sin reincidentes en {periodoDias} d
                            </p>
                        ) : (
                            <>
                                {sr.reincidencia.animales.map((item, i) => (
                                    <div
                                        key={i}
                                        className="dash-salud-reincidencia-row"
                                        onClick={() => item.animal && navigate(`/animales/${item.animal.id}`)}
                                    >
                                        <span className="dash-salud-reincidencia-nombre">{nombreAnimal(item.animal)}</span>
                                        <span className="dash-salud-reincidencia-count">{item.alertas} alertas / {periodoDias} d</span>
                                    </div>
                                ))}
                                {sr.reincidencia.lotes.length > 0 && (
                                    <>
                                        <div className="dash-salud-revisar-divider" />
                                        {sr.reincidencia.lotes.map((item, i) => (
                                            <div key={i} className="dash-salud-reincidencia-row">
                                                <span className="dash-salud-reincidencia-nombre">{item.nombre}</span>
                                                <span className="dash-salud-reincidencia-count">{item.salidas} salidas de perímetro</span>
                                            </div>
                                        ))}
                                    </>
                                )}
                            </>
                        )}
                    </div>

                    {/* ── Última intervención (Idea 11) ─────────────────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-clock-history" />
                                Última intervención
                            </p>

                        </div>

                        <>
                                {sr.ultimaIntervencion.masReciente ? (
                                    <div
                                        className="dash-salud-interv-ultima"
                                        onClick={() => sr.ultimaIntervencion.masReciente?.animal && navigate(`/animales/${sr.ultimaIntervencion.masReciente.animal.id}`)}
                                    >
                                        <span className="dash-salud-interv-ultima-tipo">
                                            {TIPO_INTERVENCION_LABEL[sr.ultimaIntervencion.masReciente.tipo]}
                                        </span>
                                        {' · '}{nombreAnimal(sr.ultimaIntervencion.masReciente.animal)}
                                        {' · '}{sr.ultimaIntervencion.masReciente.interventor ?? '—'}
                                        {' · '}hace {formatHoras(horasTranscurridas(sr.ultimaIntervencion.masReciente.fechaHora))}
                                    </div>
                                ) : (
                                    <p className="dash-no-items">Sin intervenciones registradas</p>
                                )}

                                <div className="dash-salud-interv-note">Más tiempo sin intervenciones</div>
                                {sr.ultimaIntervencion.sinIntervencionHaceMas.map(item => (
                                    <div
                                        key={item.animal.id}
                                        className="dash-salud-interv-row"
                                        onClick={() => navigate(`/animales/${item.animal.id}`)}
                                    >
                                        <span className="dash-salud-interv-nombre">{nombreAnimal(item.animal)}</span>
                                        <div className="dash-salud-interv-bar-wrap">
                                            <div
                                                className="dash-salud-interv-bar"
                                                style={{
                                                    width: item.dias !== null
                                                        ? `${Math.min(100, Math.round((item.dias / 60) * 100))}%`
                                                        : '100%',
                                                    opacity: item.dias === null ? 0.35 : 1,
                                                }}
                                            />
                                        </div>
                                        <span className="dash-salud-interv-dias">
                                            {item.dias !== null ? `${item.dias} d` : 'nunca'}
                                        </span>
                                    </div>
                                ))}
                        </>
                    </div>

                    {/* ── Ganancia de peso (Idea 17) ────────────────────────── */}
                    <div className="dash-card dash-peso-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-graph-up-arrow" />
                                Ganancia de peso
                            </p>

                        </div>
                        {gananciaPeso === null ? (
                            <p className="dash-no-items">No se pudieron cargar las pesadas</p>
                        ) : (
                            <>
                                {gananciaPeso.adpvMediana === null ? (
                                    <p className="dash-no-items">
                                        Sin pesadas suficientes (2 por animal, la última en los últimos {DIAS_PESADA_VIGENTE} d)
                                    </p>
                                ) : (
                                    <>
                                        <div className="dash-peso-headline">
                                            <span className="dash-salud-kpi-label">
                                                ADPV{' '}
                                                <OverlayTrigger placement="top" trigger={['hover', 'focus']} overlay={<Tooltip>{ADPV_AYUDA}</Tooltip>}>
                                                    <i className="bi bi-question-circle dash-peso-help" tabIndex={0} aria-label={ADPV_AYUDA} />
                                                </OverlayTrigger>
                                            </span>
                                            <span className="dash-salud-kpi-val">
                                                {gananciaPeso.adpvMediana.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                                <span className="dash-peso-unidad"> kg/día</span>
                                            </span>
                                            {gananciaPeso.variacion !== null && (
                                                <span className={`dash-peso-variacion ${gananciaPeso.variacion >= 0 ? 'dash-peso-variacion-sube' : 'dash-peso-variacion-baja'}`}>
                                                    {gananciaPeso.variacion >= 0 ? '▲' : '▼'}{' '}
                                                    {Math.abs(gananciaPeso.variacion).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                                    {' '}vs. hace {DIAS_COMPARACION} d
                                                </span>
                                            )}
                                            <span className="dash-salud-kpi-sub">
                                                mediana · {gananciaPeso.n} {gananciaPeso.n === 1 ? 'animal' : 'animales'} con ≥ 2 pesadas
                                            </span>
                                        </div>

                                        <div className="dash-embudo-bar-wrap">
                                            {GRUPOS_PESO.map(g => gananciaPeso[g.key].length > 0 && (
                                                <div
                                                    key={g.key}
                                                    className={`dash-embudo-seg dash-peso-${g.key}`}
                                                    style={{ flex: gananciaPeso[g.key].length }}
                                                    title={`${g.label}: ${gananciaPeso[g.key].length}`}
                                                />
                                            ))}
                                        </div>
                                        <div className="dash-embudo-legend">
                                            {GRUPOS_PESO.map(g => (
                                                <span key={g.key} className="dash-embudo-leg-item">
                                                    <span className={`dash-dot dash-peso-${g.key}`} style={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%' }} />
                                                    {g.label} {gananciaPeso[g.key].length}
                                                    {g.key === 'pierde' && gananciaPeso.pierde.length > 0 && (
                                                        <button className="dash-ver-todas-btn" onClick={() => verListaAnimales(gananciaPeso.pierde, `Pierden peso (ADPV < ${ADPV_PIERDE.toLocaleString('es-AR')} kg/día)`)}>
                                                            — ver lista
                                                        </button>
                                                    )}
                                                </span>
                                            ))}
                                        </div>
                                    </>
                                )}
                                <div className={`dash-salud-completitud ${gananciaPeso.cobertura.total > 0 && gananciaPeso.cobertura.pct < COBERTURA_BAJA_PCT ? 'dash-salud-peso-cobertura-baja' : ''}`}>
                                    Cobertura: {gananciaPeso.cobertura.pct} % pesados en los últimos {DIAS_COBERTURA} d
                                    {' '}({gananciaPeso.cobertura.pesados} de {gananciaPeso.cobertura.total})
                                </div>
                            </>
                        )}
                    </div>
                </div>
            </>
        );
    }

    // ── Render ────────────────────────────────────────────────────────────
    if (!selectedId) {
        return (
            <div className="dashboard-page">
                <div className="dash-empty">
                    <i className="bi bi-info-circle me-2" />
                    Seleccioná un establecimiento en la barra de navegación.
                </div>
            </div>
        );
    }

    return (
        <div className="dashboard-page">

            {/* ── Header compartido ───────────────────────────────────────── */}
            <div className="dash-header">
                <div className="dash-header-left">
                    {activeTab === 'mando' ? (
                        <h2>Requiere atención hoy</h2>
                    ) : activeTab === 'salud' ? (
                        <h2>Salud y reproducción del rodeo</h2>
                    ) : (
                        <h2>Resumen del establecimiento</h2>
                    )}
                </div>
                <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                    {/* Filtro por lote — visible en Centro de mando y Salud */}
                    {(activeTab === 'mando' || activeTab === 'salud') && rodeos.length > 0 && (
                        <div className="dash-lote-filter">
                            <i className="bi bi-funnel-fill" style={{ fontSize: '0.7rem' }} />
                            <select
                                className="dash-lote-select"
                                value={loteFilter === 'todos' ? '' : loteFilter}
                                onChange={e => setLoteFilter(e.target.value === '' ? 'todos' : Number(e.target.value))}
                            >
                                <option value="">Lote: todos</option>
                                {rodeos.map(r => (
                                    <option key={r.id} value={r.id}>{r.nombre}</option>
                                ))}
                            </select>
                        </div>
                    )}
                    {/* Período — visible en Salud y Resumen ejecutivo */}
                    {(activeTab === 'salud' || activeTab === 'ejecutivo') && (
                        <div className="dash-lote-filter">
                            <i className="bi bi-calendar3" style={{ fontSize: '0.7rem' }} />
                            <select
                                className="dash-lote-select"
                                value={periodoDias}
                                onChange={e => setPeriodoDias(Number(e.target.value))}
                                aria-label="Período"
                            >
                                {PERIODOS_DIAS.map(d => (
                                    <option key={d} value={d}>Últimos {d} días</option>
                                ))}
                            </select>
                        </div>
                    )}
                    <button
                        className="dash-atender-btn"
                        style={{ fontSize: '0.82rem', padding: '8px 16px' }}
                        onClick={refrescar}
                        disabled={loading || loadingMando || loadingSalud || loadingResumen}
                    >
                        {loading || loadingMando || loadingSalud || loadingResumen
                            ? <Spinner animation="border" size="sm" />
                            : <><i className="bi bi-arrow-clockwise me-1" />Refrescar</>
                        }
                    </button>
                </div>
            </div>

            {/* ── Tab bar ─────────────────────────────────────────────────── */}
            <div className="dash-tabs">
                <button
                    className={`dash-tab${activeTab === 'mando' ? ' dash-tab-active' : ''}`}
                    onClick={() => setActiveTab('mando')}
                    id="dash-tab-mando"
                >
                    <i className="bi bi-lightning-fill me-2" />
                    Centro de mando
                </button>
                <button
                    className={`dash-tab${activeTab === 'salud' ? ' dash-tab-active' : ''}`}
                    onClick={() => setActiveTab('salud')}
                    id="dash-tab-salud"
                >
                    <i className="bi bi-heart-pulse-fill me-2" />
                    Salud y reproducción
                </button>
                <button
                    className={`dash-tab${activeTab === 'ejecutivo' ? ' dash-tab-active' : ''}`}
                    onClick={() => setActiveTab('ejecutivo')}
                    id="dash-tab-ejecutivo"
                >
                    <i className="bi bi-bar-chart-line-fill me-2" />
                    Resumen ejecutivo
                </button>
            </div>

            {error && (
                <Alert variant="danger" style={{ borderRadius: 10, maxWidth: 1280, margin: '0 auto 16px' }}>
                    <i className="bi bi-exclamation-triangle me-2" />{error}
                </Alert>
            )}

            {/* Detalle de alerta (desde "Animales a revisar"), igual que en la página de Alertas */}
            {alertaAbierta && (
                <AlertDetailSidebar
                    alerta={alertaAbierta}
                    mode="fixed"
                    onClose={() => setAlertaAbierta(null)}
                    onActionComplete={() => {
                        fetchCentroMando();
                        fetchSaludRepro();
                        fetchResumen();
                    }}
                    onOpenIntervencion={() => setIntervencionAlerta(alertaAbierta)}
                />
            )}
            {intervencionAlerta && (
                <IntervencionFormPanel
                    alerta={intervencionAlerta}
                    mode="fixed"
                    onClose={() => setIntervencionAlerta(null)}
                    onSuccess={() => {
                        setIntervencionAlerta(null);
                        refrescar();
                    }}
                />
            )}

            {/* ── TAB: Resumen ejecutivo ───────────────────────────────────── */}
            {activeTab === 'ejecutivo' && ResumenEjecutivoView()}

            {/* ── TAB: Salud y reproducción ─────────────────────────────────── */}
            {activeTab === 'salud' && SaludReproduccionView()}

            {/* ── TAB: Centro de mando ──────────────────────────────────────── */}
            {activeTab === 'mando' && !cm && (
                errorMando
                    ? <Alert variant="danger" style={{ borderRadius: 10, maxWidth: 1280, margin: '0 auto 16px' }}>
                        <i className="bi bi-exclamation-triangle me-2" />{errorMando}
                    </Alert>
                    : <div className="dash-loading-center" style={{ padding: 60 }}><Spinner animation="border" /></div>
            )}
            {activeTab === 'mando' && cm && (

                <>
                    {/* ── KPI Chips ──────────────────────────────────────────────── */}

                    <div className="dash-kpi-row">
                        {/* Alertas pendientes */}
                        <div className="dash-kpi-chip">
                            <div className="dash-kpi-chip-label">
                                Alertas pendientes
                            </div>
                            {loadingMando ? <Spinner animation="border" size="sm" /> : (
                                <div className="dash-kpi-chip-val">{cm.alertasPendientes.total}</div>
                            )}
                            <div className="dash-kpi-chip-sub">
                                {cm.alertasPendientes.celo} celo
                                · {cm.alertasPendientes.salud} salud
                                · {cm.alertasPendientes.perimetro} perímetro
                            </div>
                        </div>

                        {/* Ventanas abiertas */}
                        <div className="dash-kpi-chip">
                            <div className="dash-kpi-chip-label">
                                Ventanas abiertas
                            </div>
                            {loadingMando ? <Spinner animation="border" size="sm" /> : (
                                <div className="dash-kpi-chip-val">{cm.ventanasAbiertas.total}</div>
                            )}
                            <div className="dash-kpi-chip-sub">{cm.ventanasAbiertas.porVencer > 0
                                ? `${cm.ventanasAbiertas.porVencer} por vencer en < ${cm.umbrales.horasVentanaPorVencer}h`
                                : 'sin urgencia inmediata'}</div>
                        </div>

                        {/* Bajo observación */}
                        <div className="dash-kpi-chip">
                            <div className="dash-kpi-chip-label">
                                Bajo observación
                            </div>
                            {loadingMando ? <Spinner animation="border" size="sm" /> : (
                                <div className="dash-kpi-chip-val">{cm.bajoObservacion.total}</div>
                            )}
                            <div className="dash-kpi-chip-sub">{cm.bajoObservacion.enfermos > 0 ? `${cm.bajoObservacion.enfermos} enfermos` : 'en observación'}</div>
                        </div>

                        {/* Cobertura */}
                        <div className="dash-kpi-chip">
                            <div className="dash-kpi-chip-label">
                                Cobertura de caravanas
                            </div>
                            {loadingMando ? <Spinner animation="border" size="sm" /> : (
                                <div className="dash-kpi-chip-val">{cm.cobertura.porcentaje}%</div>
                            )}
                            <div className="dash-kpi-chip-sub">
                                {cm.cobertura.sinSenial > 0 ? `${cm.cobertura.sinSenial} sin señal · ` : ''}
                                {cm.cobertura.bateriaBaja > 0 ? `${cm.cobertura.bateriaBaja} batería baja` : 'caravanas ok'}
                            </div>
                        </div>
                    </div>

                    {/* ── Main grid: izquierda + derecha ──────────────────────────── */}
                    <div className="dash-main-grid">

                        {/* ── COLUMNA IZQUIERDA ─────────────────────────────────── */}
                        <div className="dash-left-col">

                            {/* ── Idea 1: Cola de atención ─────────────────────── */}
                            <div className="dash-card">
                                <div className="dash-card-header">
                                    <p className="dash-card-title">
                                        <i className="bi bi-list-task" />
                                        Cola de atención
                                    </p>
                                    <button className="dash-ver-todas-btn" onClick={() => navigate('/alertas')}>
                                        Ver todas ({cm.alertasPendientes.total}) <i className="bi bi-arrow-right" />
                                    </button>
                                </div>

                                {loadingMando ? (
                                    <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                                ) : cm.cola.length === 0 ? (
                                    <p className="dash-no-items">
                                        <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                        Sin alertas pendientes — todo en orden
                                    </p>
                                ) : (
                                    <>
                                        <div className="dash-cola-header-row">
                                            <span /> {/* Spacer para severidad */}
                                            <span>Animal</span>
                                            <span className='d-none d-md-block'>Acción recomendada</span>
                                            <span style={{ textAlign: 'center' }}>Tipo</span>
                                            <span className='d-none d-md-block' style={{ textAlign: 'center' }}>Antigüedad</span>
                                            <span /> {/* Spacer para boton */}
                                        </div>
                                        {cm.cola.map((item, i) => {
                                            const { alerta, animal, ventana } = item;
                                            const horas = horasTranscurridas(alerta.tsGeneracion);
                                            const isCelo = alerta.tipo === 'CELO';
                                            const horasRestantes = ventana ? ventana.tiempoRestanteSegundos / 3600 : null;
                                            const colorTiempoClass = isCelo && horasRestantes !== null
                                                ? horasRestantes <= 3 ? 'dash-cola-tiempo-urgente'
                                                    : horasRestantes <= 6 ? 'dash-cola-tiempo-warning'
                                                        : ''
                                                : horas > 24 ? 'dash-cola-tiempo-urgente'
                                                    : horas > 8 ? 'dash-cola-tiempo-warning'
                                                        : '';
                                            const tiempoClass = `dash-cola-tiempo ${colorTiempoClass}`.trim();

                                            const tiempoLabel = isCelo && horasRestantes !== null
                                                ? `${Math.floor(horasRestantes)}h restantes`
                                                : `Hace ${formatHoras(horas)}`;

                                            // Misma regla que el color de la antigüedad: rojo ⇔ botón destacado.
                                            const isUrgentAtender = colorTiempoClass === 'dash-cola-tiempo-urgente';

                                            const sevDotClass = alerta.severidad === 'ALTA' ? 'dash-sev-dot-alta'
                                                : alerta.severidad === 'MEDIA' ? 'dash-sev-dot-media'
                                                    : alerta.severidad === 'BAJA' ? 'dash-sev-dot-baja'
                                                        : 'dash-sev-dot-none';

                                            const tipoClass = alerta.tipo === 'CELO' ? 'dash-tipo-celo'
                                                : alerta.tipo === 'SALUD' ? 'dash-tipo-salud'
                                                    : alerta.tipo === 'FUERA_DE_PERIMETRO' ? 'dash-tipo-perimetro'
                                                        : 'dash-tipo-caravana';

                                            const tipoLabel = esAnestro(alerta) ? 'ANESTRO'
                                                : alerta.tipo === 'CELO' ? 'CELO'
                                                    : alerta.tipo === 'SALUD' ? 'SALUD'
                                                        : alerta.tipo === 'FUERA_DE_PERIMETRO' ? 'PERÍMETRO'
                                                            : 'CARAVANA';

                                            return (
                                                <div key={`${alerta.id}-${i}`}>
                                                    {i > 0 && <div className="dash-divider" />}
                                                    <div
                                                        className="dash-cola-row"
                                                        onClick={() => animal && navigate(`/animales/${animal.id}`)}
                                                    >
                                                        <span className={`dash-sev-dot ${sevDotClass}`} />
                                                        <div className="dash-cola-nombre">{nombreAnimal(animal)}</div>
                                                        <div className="dash-cola-rec d-none d-md-block" title={textoAccionRecomendada(alerta)}>
                                                            {textoAccionRecomendada(alerta)}
                                                        </div>
                                                        <span className={`dash-tipo-chip ${tipoClass}`}>
                                                            {tipoLabel}
                                                            {tieneDatoDesactualizado(alerta) && (
                                                                <i
                                                                    className="bi bi-wifi-off ms-1"
                                                                    style={{ color: '#B7791F' }}
                                                                    title={`Dato posiblemente desactualizado: la caravana no transmite hace más de ${alerta.horasUmbralDatoDesactualizado ?? 2} h. Verificá el animal en campo antes de inseminar.`}
                                                                />
                                                            )}
                                                        </span>
                                                        <span className={`${tiempoClass} d-none d-md-block`}>{tiempoLabel}</span>
                                                        <button
                                                            className={`dash-atender-btn ${isUrgentAtender ? '' : 'dash-atender-btn-outline'}`}
                                                            onClick={e => {
                                                                e.stopPropagation();
                                                                if (animal) navigate(`/animales/${animal.id}`);
                                                            }}
                                                        >
                                                            Atender
                                                        </button>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </>
                                )}
                            </div>

                            {/* ── Idea 2: Ventanas de inseminación ─────────────── */}
                            <div className="dash-card">
                                <div className="dash-card-header">
                                    <p className="dash-card-title">
                                        <i className="bi bi-clock-history" />
                                        Ventanas de inseminación abiertas
                                    </p>
                                    {!loadingMando && (
                                        <span className="dash-card-tag">
                                            Oportunidades perdidas (30d): {cm.ventanasAbiertas.perdidasUltimos30Dias}
                                        </span>
                                    )}
                                </div>

                                {loadingMando ? (
                                    <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                                ) : cm.ventanas.length === 0 ? (
                                    <p className="dash-no-items">
                                        <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                        Sin ventanas activas
                                    </p>
                                ) : (
                                    cm.ventanas.map(({ alerta, animal, ventana }) => {
                                        const isFutura = ventana.estado === 'FUTURA';
                                        const vc = isFutura ? 'futura' : ventanaClass(ventana.tiempoRestanteSegundos);
                                        const pct = isFutura ? 0 : Math.min(100, Math.max(0, ventana.porcentajeTranscurrido));
                                        const restanteClass = isFutura
                                            ? 'dash-ventana-restante-futura'
                                            : vc === 'critica'
                                                ? 'dash-ventana-restante-urgente'
                                                : vc === 'venciendo'
                                                    ? 'dash-ventana-restante-warning'
                                                    : 'dash-ventana-restante-ok';

                                        const timeLabel = isFutura
                                            ? `Abre en ${formatHorasRestantes(ventana.tiempoRestanteSegundos).replace(' restantes', '')}`
                                            : formatHorasRestantes(ventana.tiempoRestanteSegundos);

                                        return (
                                            <div
                                                key={alerta.id}
                                                className="dash-ventana-row"
                                                style={{ cursor: 'pointer' }}
                                                onClick={() => animal && navigate(`/animales/${animal.id}`)}
                                            >
                                                <div className="dash-ventana-id">{nombreAnimal(animal)}</div>
                                                <div className="dash-ventana-bar-track">
                                                    <div
                                                        className={`dash-ventana-bar-fill dash-ventana-bar-${vc}`}
                                                        style={{ width: `${pct}%` }}
                                                    />
                                                </div>
                                                <div className={`dash-ventana-restante ${restanteClass}`}>
                                                    {timeLabel}
                                                </div>
                                            </div>
                                        );
                                    })
                                )}
                            </div>
                        </div>

                        {/* ── COLUMNA DERECHA ───────────────────────────────────── */}
                        <div className="dash-right-col">

                            {/* ── Idea 4: Cobertura de caravanas ─────────────────── */}
                            <div className="dash-card dash-card-dark">
                                <div className="dash-card-header">
                                    <p className="dash-card-title">
                                        <i className="bi bi-wifi" />
                                        Estado del monitoreo
                                    </p>
                                </div>

                                {loadingMando ? (
                                    <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                                ) : (
                                    <>
                                        <div className="dash-coverage-pct">
                                            {cm.cobertura.porcentaje}%
                                        </div>
                                        <div className="dash-coverage-sub">del rodeo vigilado</div>

                                        <div className="dash-coverage-bar-wrap">
                                            {cm.cobertura.ok > 0 && (
                                                <div className="dash-coverage-seg dash-coverage-ok"
                                                    style={{ flex: cm.cobertura.ok }} title={`Monitoreados: ${cm.cobertura.ok}`} />
                                            )}
                                            {cm.cobertura.calibrando > 0 && (
                                                <div className="dash-coverage-seg dash-coverage-calibrando"
                                                    style={{ flex: cm.cobertura.calibrando }} title={`Calibrando: ${cm.cobertura.calibrando}`} />
                                            )}
                                            {cm.cobertura.bateriaBaja > 0 && (
                                                <div className="dash-coverage-seg dash-coverage-bateria"
                                                    style={{ flex: cm.cobertura.bateriaBaja }} title={`Batería baja: ${cm.cobertura.bateriaBaja}`} />
                                            )}
                                            {cm.cobertura.sinSenial > 0 && (
                                                <div className="dash-coverage-seg dash-coverage-silencio"
                                                    style={{ flex: cm.cobertura.sinSenial }} title={`Sin señal: ${cm.cobertura.sinSenial}`} />
                                            )}
                                            {cm.cobertura.sinCaravana > 0 && (
                                                <div className="dash-coverage-seg dash-coverage-sincaravana"
                                                    style={{ flex: cm.cobertura.sinCaravana }} title={`Sin caravana: ${cm.cobertura.sinCaravana}`} />
                                            )}
                                        </div>

                                        <div className="dash-coverage-legend">
                                            {[
                                                { label: 'Monitoreados', count: cm.cobertura.ok, cls: 'dash-coverage-ok' },
                                                { label: 'En calibración', count: cm.cobertura.calibrando, cls: 'dash-coverage-calibrando' },
                                                { label: `Sin señal > ${cm.umbrales.horasSinSenial}h`, count: cm.cobertura.sinSenial, cls: 'dash-coverage-silencio' },
                                                { label: `Batería ≤ ${cm.umbrales.bateriaBajaPorcentaje}%`, count: cm.cobertura.bateriaBaja, cls: 'dash-coverage-bateria' },
                                                { label: 'Sin caravana', count: cm.cobertura.sinCaravana, cls: 'dash-coverage-sincaravana' },
                                            ].filter(r => r.count > 0).map(row => (
                                                <div key={row.label} className="dash-coverage-leg-row">
                                                    <div className="dash-coverage-leg-left">
                                                        <span className={`dash-dot ${row.cls}`} style={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%' }} />
                                                        {row.label}
                                                    </div>
                                                    <span className="dash-coverage-leg-count">{row.count}</span>
                                                </div>
                                            ))}
                                        </div>
                                    </>
                                )}
                            </div>

                            {/* ── Idea 5: Mapa vivo ──────────────────────────────── */}
                            <div className="dash-card" style={{ padding: '18px 18px 14px' }}>
                                <div className="dash-card-header">
                                    <p className="dash-card-title">
                                        <i className="bi bi-map-fill" />
                                        Mapa en vivo
                                    </p>
                                    <button className="dash-ver-todas-btn" onClick={() => navigate('/mapa')}>
                                        Expandir <i className="bi bi-box-arrow-up-right" />
                                    </button>
                                </div>

                                {loading ? (
                                    <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                                ) : (
                                    <div className="dash-map-wrap" onClick={() => navigate('/mapa')}>
                                        <MapContainer
                                            center={[-34.6037, -58.3816]}
                                            zoom={12}
                                            style={{ height: '100%', width: '100%' }}
                                            zoomControl={false}
                                            attributionControl={false}
                                            dragging={false}
                                            scrollWheelZoom={false}
                                            doubleClickZoom={false}
                                            touchZoom={false}
                                            keyboard={false}
                                        >
                                            <TileLayer
                                                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                                            />
                                            {animalesMapa
                                                .filter(a => a.latitud && a.longitud)
                                                .map(a => (
                                                    <Marker
                                                        key={a.id}
                                                        position={[a.latitud!, a.longitud!]}
                                                        icon={determinarIcono(a)}
                                                    />
                                                ))
                                            }
                                            <MapFitter animales={animalesMapa} />
                                        </MapContainer>

                                    </div>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* ══ DEBAJO DEL PLIEGUE ══════════════════════════════════════ */}

                    <div className="dash-below-grid">

                        {/* ── Idea 6: Embudo reproductivo ──────────────────────── */}
                        <div className="dash-card">
                            <div className="dash-card-header">
                                <p className="dash-card-title">
                                    <i className="bi bi-diagram-3-fill" />
                                    Embudo reproductivo
                                </p>
                                {!loadingMando && cm.embudo.inseminadasSinDiagnostico > 0 && (
                                    <span style={{ fontSize: '0.75rem', color: '#ECC94B' }}>
                                        {cm.embudo.inseminadasSinDiagnostico} esperan diagnóstico
                                    </span>
                                )}
                            </div>

                            {loadingMando ? (
                                <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                            ) : cm.embudo.totalReproductivos === 0 ? (
                                <p className="dash-no-items">Sin animales reproductivos registrados</p>
                            ) : (
                                <>
                                    <div className="dash-embudo-bar-wrap">
                                        {Object.entries(cm.embudo.distribucion)
                                            .filter(([, v]) => v > 0)
                                            .map(([estado, count]) => (
                                                <div
                                                    key={estado}
                                                    className={`dash-embudo-seg dash-embudo-${estado.toLowerCase()}`}
                                                    style={{ flex: count }}
                                                    title={`${ESTADO_REPRODUCTIVO_LABEL[estado] ?? estado.replace('_', ' ')}: ${count}`}
                                                />
                                            ))}
                                    </div>
                                    <div className="dash-embudo-legend">
                                        {Object.entries(cm.embudo.distribucion).filter(([, v]) => v > 0).map(([estado, count]) => (
                                            <span key={estado} className="dash-embudo-leg-item">
                                                <span className={`dash-dot dash-embudo-${estado.toLowerCase()}`}
                                                    style={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%' }}
                                                />
                                                {ESTADO_REPRODUCTIVO_LABEL[estado] ?? estado.replace('_', ' ')}
                                                <strong>{count}</strong>
                                            </span>
                                        ))}
                                    </div>
                                </>
                            )}
                        </div>

                        {/* ── Idea 8: Tendencia 8 semanas ──────────────────────────── */}
                        <div className="dash-card">
                            <div className="dash-card-header">
                                <p className="dash-card-title">
                                    <i className="bi bi-graph-up" />
                                    Tendencia de alertas · 8 semanas
                                </p>
                            </div>
                            {loadingMando ? (
                                <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                            ) : cm.tendencia.tipos.every(t => cm.tendencia.datos[t].every(v => v === 0)) ? (
                                <p className="dash-no-items">
                                    <i className="bi bi-bar-chart" style={{ color: '#94A3B8' }} />
                                    Sin alertas en las últimas 8 semanas
                                </p>
                            ) : (
                                <TrendChart
                                    datos={cm.tendencia.datos}
                                    semanas={cm.tendencia.semanas}
                                    tipos={cm.tendencia.tipos}
                                />
                            )}
                        </div>

                        {/* ── Idea 10: Comparativa por lote (Placeholder) ─────────── */}
                        <div className="dash-card">
                            <div className="dash-card-header">
                                <p className="dash-card-title">
                                    <i className="bi bi-bar-chart-line-fill" />
                                    Comparativa por lote
                                </p>
                            </div>
                            <div className="dash-salud-peso-empty" style={{ padding: '40px 16px' }}>
                                <i className="bi bi-kanban" style={{ fontSize: '1.6rem', color: '#CBD5E1' }} />
                                <span className="dash-salud-peso-empty-text">
                                    Disponible próximamente
                                </span>
                                <span className="dash-salud-peso-empty-sub">
                                    Módulo de analítica por lote en desarrollo
                                </span>
                            </div>
                        </div>
                    </div>

                </>
            )}

        </div>
    );
}
