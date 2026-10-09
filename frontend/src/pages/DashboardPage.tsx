/* eslint-disable react-hooks/purity, react-hooks/set-state-in-effect */
import { useState, useEffect, useMemo, useCallback } from 'react';
import { Spinner, Alert } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

import { getAnimales, getIntervencionesEstablecimiento, ESTADO_REPRODUCTIVO_LABEL } from '../api/animalesApi';
import type { AnimalResponse, IntervencionResponse } from '../api/animalesApi';
import { getAlertas, getVentanaInseminacion, esAnestro, tieneDatoDesactualizado, textoAccionRecomendada } from '../api/alertaApi';
import type { AlertaResponse, VentanaInseminacionResponse } from '../api/alertaApi';
import { getRodeoLotes } from '../api/rodeoLotesApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import { getAnimalesMapa } from '../api/animalesApi';
import type { AnimalMapa } from '../api/animalesApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { determinarIcono } from '../utils/animalPins';
import './DashboardPage.css';

// ── Helpers ──────────────────────────────────────────────────────────────────

const SILENCIO_THRESHOLD_H = 6;     // caravana sin señal > 6h = silenciosa
const BATERIA_BAJA_PCT = 20;        // batería ≤ 20% = crítica
const DIAS_ABIERTOS_URGENTE = 90;   // días abiertos para mostrar en embudo
const INSEMINADA_DIAS_DIAG = 35;    // inseminada > 35d sin confirmación
const DIAS_GESTACION = 283;         // parto esperado a 283 días de la inseminación
// Estados en los que la vaca sigue "abierta" (sin preñez confirmada) desde el último parto.
const ESTADOS_ABIERTOS = new Set(['VACIA', 'CELO', 'POSIBLE_CELO', 'INSEMINADA', 'PARIDA', 'ANESTRO']);
const PERIODOS_DIAS = [7, 30, 90];  // opciones del selector de período (Salud y Resumen ejecutivo)
const PERIODO_DEFAULT_DIAS = 30;
const DIA_MS = 24 * 60 * 60 * 1000;
const REVISAR_MAX = 5;              // filas por sub-sección en "Animales a revisar"

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

function diffDias(ts: string): number {
    return (Date.now() - new Date(ts).getTime()) / (1000 * 60 * 60 * 24);
}

function esReproductiva(a: AnimalResponse): boolean {
    return diffDias(a.fechaNacimiento) / 365.25 >= 1;
}

function nombreAnimal(a: AnimalResponse | null | undefined): string {
    if (!a) return '—';
    return a.alias || a.idCaravanaOficial || `#${a.id}`;
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

// Celo con ventana de inseminación: el anestro llega con tipo CELO pero no la tiene.
const esCeloConVentana = (a: AlertaResponse) => a.tipo === 'CELO' && !esAnestro(a);

// ── Urgencia para ranking (menor = más urgente) ───────────────────────────────
function urgencia(a: AlertaResponse, ventana?: VentanaInseminacionResponse): number {
    if (a.tipo === 'CELO' && ventana?.estado === 'ACTIVA') {
        return ventana.tiempoRestanteSegundos / 3600; // horas restantes
    }
    if (a.tipo === 'FUERA_DE_PERIMETRO') return 24 + horasTranscurridas(a.tsGeneracion);
    const sevVal = a.severidad === 'ALTA' ? 0 : a.severidad === 'MEDIA' ? 1 : 2;
    return 100 + sevVal * 10 + horasTranscurridas(a.tsGeneracion);
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

interface ColaItem {
    alerta: AlertaResponse;
    animal: AnimalResponse | undefined;
    ventana?: VentanaInseminacionResponse;
}

interface VentanaItem {
    alerta: AlertaResponse;
    animal: AnimalResponse | undefined;
    ventana: VentanaInseminacionResponse;
}

// ─────────────────────────────────────────────────────────────────────────────

// ── Colores del trend chart ──────────────────────────────────────────────
const TREND_COLORS = {
    CELO: '#1A3558',
    SALUD: '#3B8D99',
    FUERA_DE_PERIMETRO: '#13D4FB',
} as const;

// ── Helpers del resumen ejecutivo ─────────────────────────────────────────
// Semana ISO 8601 (lunes a domingo; la semana 1 es la que contiene el primer jueves del año).
function getWeekKey(ts: string): string {
    const d = new Date(ts);
    const jueves = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    jueves.setDate(jueves.getDate() - ((jueves.getDay() + 6) % 7) + 3);
    const anioIso = jueves.getFullYear();
    const primerJueves = new Date(anioIso, 0, 4);
    primerJueves.setDate(primerJueves.getDate() - ((primerJueves.getDay() + 6) % 7) + 3);
    // Math.round absorbe la hora de diferencia de los cambios de horario.
    const week = 1 + Math.round((jueves.getTime() - primerJueves.getTime()) / (7 * 86400000));
    return `${anioIso}-W${String(week).padStart(2, '0')}`;
}

function last8WeekKeys(): string[] {
    const keys: string[] = [];
    for (let i = 7; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i * 7);
        keys.push(getWeekKey(d.toISOString()));
    }
    return keys;
}

type TipoTendencia = 'CELO' | 'SALUD' | 'FUERA_DE_PERIMETRO';

// Alertas generadas por semana y por tipo en las últimas 8 semanas (los anestros no cuentan como celo).
function calcularTendencia8Semanas(alertas: AlertaResponse[]) {
    const semanas = last8WeekKeys();
    const tipos: TipoTendencia[] = ['CELO', 'SALUD', 'FUERA_DE_PERIMETRO'];
    const datos: Record<TipoTendencia, number[]> = { CELO: [], SALUD: [], FUERA_DE_PERIMETRO: [] };

    semanas.forEach(sem => {
        tipos.forEach(tipo => {
            const count = alertas.filter(a =>
                a.tipo === tipo && !esAnestro(a) && getWeekKey(a.tsGeneracion) === sem
            ).length;
            datos[tipo].push(count);
        });
    });

    return { semanas, datos, tipos };
}

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

export default function DashboardPage() {
    const selectedId = useEstablecimientoStore(s => s.selectedId);
    const navigate = useNavigate();

    const [activeTab, setActiveTab] = useState<'mando' | 'ejecutivo' | 'salud'>('mando');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [loteFilter, setLoteFilter] = useState<number | 'todos'>('todos');
    const [periodoDias, setPeriodoDias] = useState(PERIODO_DEFAULT_DIAS);

    // Raw data
    const [animales, setAnimales] = useState<AnimalResponse[]>([]);
    const [alertasPendientes, setAlertasPendientes] = useState<AlertaResponse[]>([]);
    const [alertasArchivadasCelo, setAlertasArchivadasCelo] = useState<AlertaResponse[]>([]);
    const [rodeos, setRodeos] = useState<RodeoLoteResponse[]>([]);
    const [animalesMapa, setAnimalesMapa] = useState<AnimalMapa[]>([]);

    // Ventanas de celo (mapa alertaId → ventana)
    const [ventanas, setVentanas] = useState<Map<number, VentanaInseminacionResponse>>(new Map());

    // Alertas generadas dentro del período más largo del selector (cubre también la tendencia de 8 semanas)
    const [alertasRecientes, setAlertasRecientes] = useState<AlertaResponse[]>([]);

    // Intervenciones del establecimiento (null si no se pudieron cargar)
    const [intervenciones, setIntervenciones] = useState<IntervencionResponse[] | null>([]);

    const fetchDashboard = useCallback(async () => {
        if (!selectedId) return;
        setLoading(true);
        setError(null);
        try {
            const [anim, pendientes, celoArchivadas, rodeosData, mapaData, confirmadas, descartadas, interv] = await Promise.all([
                getAnimales(selectedId),
                getAlertas(selectedId, undefined, undefined, 'PENDIENTE'),
                getAlertas(selectedId, 'CELO', undefined, 'ARCHIVADA'),
                getRodeoLotes(selectedId),
                getAnimalesMapa(selectedId),
                getAlertas(selectedId, undefined, undefined, 'CONFIRMADA'),
                getAlertas(selectedId, undefined, undefined, 'DESCARTADA'),
                // Si falla, el resto del dashboard sigue funcionando.
                getIntervencionesEstablecimiento(selectedId).catch(() => null),
            ]);
            setAnimales(anim);
            setIntervenciones(interv);
            setAlertasPendientes(pendientes);
            setAlertasArchivadasCelo(celoArchivadas);
            setRodeos(rodeosData.filter(r => r.activo));
            setAnimalesMapa(mapaData);
            // Para tendencias y métricas por período
            const ventanaMs = Math.max(...PERIODOS_DIAS) * DIA_MS;
            const todas = [...pendientes, ...celoArchivadas, ...confirmadas, ...descartadas].filter(
                a => Date.now() - new Date(a.tsGeneracion).getTime() <= ventanaMs
            );
            setAlertasRecientes(todas);

            // Fetch ventanas para cada alerta de celo PENDIENTE
            const celoPendientes = pendientes.filter(esCeloConVentana);
            const ventanaResults = await Promise.allSettled(
                celoPendientes.map(a => getVentanaInseminacion(a.id, selectedId))
            );
            const vm = new Map<number, VentanaInseminacionResponse>();
            ventanaResults.forEach((r, i) => {
                if (r.status === 'fulfilled') vm.set(celoPendientes[i].id, r.value);
            });
            setVentanas(vm);
        } catch (e) {
            const err = e as { response?: { data?: { message?: string } } };
            setError(err.response?.data?.message || 'Error al cargar el dashboard.');
        } finally {
            setLoading(false);
        }
    }, [selectedId]);

    useEffect(() => { fetchDashboard(); }, [fetchDashboard]);

    // ── Mapa animal
    const animalMap = useMemo(() => new Map(animales.map(a => [a.id, a])), [animales]);

    // ── Filtrado por lote ────────────────────────────────────────────────────
    const animalesFiltrados = useMemo(() =>
        loteFilter === 'todos' ? animales : animales.filter(a => a.rodeoLoteId === loteFilter),
        [animales, loteFilter]);

    const alertasFiltradas = useMemo(() =>
        loteFilter === 'todos'
            ? alertasPendientes
            : alertasPendientes.filter(a => {
                const animal = animalMap.get(a.animalId);
                return animal?.rodeoLoteId === loteFilter;
            }),
        [alertasPendientes, animalMap, loteFilter]);

    // Alertas recientes (cualquier estado) del lote seleccionado
    const alertasRecientesLote = useMemo(() =>
        loteFilter === 'todos'
            ? alertasRecientes
            : alertasRecientes.filter(a => animalMap.get(a.animalId)?.rodeoLoteId === loteFilter),
        [alertasRecientes, animalMap, loteFilter]);

    // Última intervención registrada por animal
    const ultimaIntervPorAnimal = useMemo(() => {
        const m = new Map<number, IntervencionResponse>();
        (intervenciones ?? []).forEach(i => {
            const prev = m.get(i.animalId);
            if (!prev || new Date(i.fechaHora) > new Date(prev.fechaHora)) m.set(i.animalId, i);
        });
        return m;
    }, [intervenciones]);

    // ── Idea 1: Cola de atención ─────────────────────────────────────────────
    const colaItems: ColaItem[] = useMemo(() => {
        const items: ColaItem[] = alertasFiltradas.map(a => ({
            alerta: a,
            animal: animalMap.get(a.animalId),
            ventana: ventanas.get(a.id),
        }));

        // Detectar caravanas silenciosas (sin caravana o sin señal > 6h)
        const activosSinCaravana = animalesFiltrados.filter(
            a => a.estadoActual !== 'INACTIVO' && (
                !a.caravanaId ||
                (a.caravanaUltimaSenial && horasTranscurridas(a.caravanaUltimaSenial) > SILENCIO_THRESHOLD_H)
            )
        );
        // Las caravanas silenciosas como "alertas sintéticas" — sólo si no tienen ya una alerta
        const alertaIds = new Set(alertasFiltradas.map(a => a.animalId));
        activosSinCaravana.filter(a => !alertaIds.has(a.id)).forEach(a => {
            items.push({
                alerta: {
                    id: -a.id, // ID sintético negativo
                    animalId: a.id,
                    tipo: 'CARAVANA',
                    estado: 'PENDIENTE',
                    tsGeneracion: a.caravanaUltimaSenial ?? a.fechaAlta,
                } as AlertaResponse,
                animal: a,
            });
        });

        return items.sort((a, b) => urgencia(a.alerta, a.ventana) - urgencia(b.alerta, b.ventana));
    }, [alertasFiltradas, animalesFiltrados, animalMap, ventanas]);

    // ── Idea 2: Ventanas de inseminación ─────────────────────────────────────
    const ventanaItems: VentanaItem[] = useMemo(() => {
        return alertasFiltradas
            .filter(a => a.tipo === 'CELO' && ventanas.has(a.id) && (ventanas.get(a.id)!.estado === 'ACTIVA' || ventanas.get(a.id)!.estado === 'FUTURA'))
            .map(a => ({
                alerta: a,
                animal: animalMap.get(a.animalId),
                ventana: ventanas.get(a.id)!,
            }))
            .sort((a, b) => {
                const aFutura = a.ventana.estado === 'FUTURA';
                const bFutura = b.ventana.estado === 'FUTURA';
                if (!aFutura && bFutura) return -1;
                if (aFutura && !bFutura) return 1;
                return a.ventana.tiempoRestanteSegundos - b.ventana.tiempoRestanteSegundos;
            });
    }, [alertasFiltradas, animalMap, ventanas]);

    const celoArchivadasRecientes = useMemo(() => {
        const hace30d = 30 * 24 * 60 * 60 * 1000;
        return alertasArchivadasCelo.filter(
            a => esCeloConVentana(a) && Date.now() - new Date(a.tsGeneracion).getTime() <= hace30d
        ).length;
    }, [alertasArchivadasCelo]);

    // ── Idea 3: Animales a revisar ────────────────────────────────────────────
    const animalesARevisar = useMemo(() => {
        return animalesFiltrados
            .filter(a => a.estadoActual !== 'INACTIVO' && (
                a.estadoSalud === 'ENFERMO' || a.estadoSalud === 'EN_OBSERVACION'
            ))
            .sort((a, b) => {
                if (a.estadoSalud === 'ENFERMO' && b.estadoSalud !== 'ENFERMO') return -1;
                if (b.estadoSalud === 'ENFERMO' && a.estadoSalud !== 'ENFERMO') return 1;
                return 0;
            });
    }, [animalesFiltrados]);

    // ── Idea 4: Cobertura de caravanas ─────────────────────────────────────────
    const cobertura = useMemo(() => {
        const elegibles = animalesFiltrados.filter(a => a.estadoActual !== 'INACTIVO');
        const total = elegibles.length;
        if (total === 0) return { pct: 0, ok: 0, calibrando: 0, silencio: 0, bateriaBaja: 0, sinCaravana: 0, total: 0 };

        let ok = 0, calibrando = 0, silencio = 0, bateriaBaja = 0, sinCaravana = 0;
        elegibles.forEach(a => {
            if (!a.caravanaId) { sinCaravana++; return; }
            if (a.enCalibracion) { calibrando++; return; }
            const hs = a.caravanaUltimaSenial ? horasTranscurridas(a.caravanaUltimaSenial) : Infinity;
            if (hs > SILENCIO_THRESHOLD_H) { silencio++; return; }
            if (a.caravanaBateria !== null && a.caravanaBateria <= BATERIA_BAJA_PCT) { bateriaBaja++; return; }
            ok++;
        });
        return { pct: Math.round((ok / total) * 100), ok, calibrando, silencio, bateriaBaja, sinCaravana, total };
    }, [animalesFiltrados]);

    // ── Idea 6: Embudo reproductivo ───────────────────────────────────────────
    const embudo = useMemo(() => {
        const reproductivos = animalesFiltrados.filter(
            a => a.estadoActual !== 'INACTIVO' && esReproductiva(a)
        );
        const dist: Record<string, number> = {
            VACIA: 0, CELO: 0, POSIBLE_CELO: 0, INSEMINADA: 0,
            GESTANTE: 0, PARIDA: 0, ANESTRO: 0, SIN_ESTADO: 0,
        };
        let diasAbiertosSuma = 0;
        let nAbiertas = 0;
        let inseminadasPendienteDiag = 0;

        reproductivos.forEach(a => {
            const estado = a.estadoReproductivo ?? 'SIN_ESTADO';
            dist[estado] = (dist[estado] ?? 0) + 1;

            // Días abiertos: desde el último parto, sólo en vacas que todavía no quedaron preñadas
            if (ESTADOS_ABIERTOS.has(estado) && a.fechaUltimoParto) {
                diasAbiertosSuma += diffDias(a.fechaUltimoParto);
                nAbiertas++;
            }
            // Inseminadas sin diagnóstico
            if (estado === 'INSEMINADA' && a.fechaUltimaInseminacion) {
                if (diffDias(a.fechaUltimaInseminacion) > INSEMINADA_DIAS_DIAG) inseminadasPendienteDiag++;
            }
        });

        const totalRepro = reproductivos.length;
        const diasAbiertosProm = nAbiertas > 0 ? Math.round(diasAbiertosSuma / nAbiertas) : 0;
        return { dist, totalRepro, diasAbiertosProm, nAbiertas, inseminadasPendienteDiag };
    }, [animalesFiltrados]);

    // ── Idea 10: Tabla por rodeo (ejecutivo) ──────────────────────────────
    const tablaRodeos = useMemo(() => {
        const periodoMs = periodoDias * DIA_MS;
        const hace90d = 90 * 24 * 60 * 60 * 1000;

        return rodeos.map(rodeo => {
            const animalesRodeo = animales.filter(
                a => a.rodeoLoteId === rodeo.id && a.estadoActual !== 'INACTIVO'
            );
            const totalAnim = animalesRodeo.length;
            if (totalAnim === 0) return { rodeo, totalAnim, cobPct: 0, alertasPend: 0, alertas100: 0, ventPerdidas: 0, pctVacias90: 0 };

            // Cobertura
            let okCount = 0;
            animalesRodeo.forEach(a => {
                if (!a.caravanaId || a.enCalibracion) return;
                const hs = a.caravanaUltimaSenial ? horasTranscurridas(a.caravanaUltimaSenial) : Infinity;
                if (hs <= SILENCIO_THRESHOLD_H && (a.caravanaBateria === null || a.caravanaBateria > BATERIA_BAJA_PCT)) okCount++;
            });
            const cobPct = Math.round((okCount / totalAnim) * 100);

            // Alertas pendientes del rodeo
            const alertasRodeo = alertasPendientes.filter(al => {
                const anim = animalMap.get(al.animalId);
                return anim?.rodeoLoteId === rodeo.id;
            });
            const alertasPend = alertasRodeo.length;
            const alertas100 = totalAnim > 0 ? Math.round((alertasPend / totalAnim) * 100) : 0;

            // Ventanas perdidas: celos archivados sin atender en el período. Los anestros también
            // llegan archivados (al registrarse un ciclo), pero no tienen ventana que perder.
            const ventPerdidas = alertasArchivadasCelo.filter(al => {
                const anim = animalMap.get(al.animalId);
                return esCeloConVentana(al) && anim?.rodeoLoteId === rodeo.id &&
                    Date.now() - new Date(al.tsGeneracion).getTime() <= periodoMs;
            }).length;

            // % vacías > 90d
            const reproductivas = animalesRodeo.filter(a => esReproductiva(a));
            const vacias90 = reproductivas.filter(a =>
                a.estadoReproductivo === 'VACIA' &&
                a.fechaUltimoParto &&
                Date.now() - new Date(a.fechaUltimoParto).getTime() > hace90d
            ).length;
            const pctVacias90 = reproductivas.length > 0
                ? Math.round((vacias90 / reproductivas.length) * 100)
                : 0;

            return { rodeo, totalAnim, cobPct, alertasPend, alertas100, ventPerdidas, pctVacias90 };
        });
    }, [rodeos, animales, alertasPendientes, alertasArchivadasCelo, animalMap, periodoDias]);

    // Promedio del establecimiento para comparar colores
    const mediaEstab = useMemo(() => {
        const rows = tablaRodeos.filter(r => r.totalAnim > 0);
        if (rows.length === 0) return { cobPct: 80, alertas100: 10, ventPerdidas: 2, pctVacias90: 15 };
        return {
            cobPct: Math.round(rows.reduce((s, r) => s + r.cobPct, 0) / rows.length),
            alertas100: Math.round(rows.reduce((s, r) => s + r.alertas100, 0) / rows.length),
            ventPerdidas: Math.round(rows.reduce((s, r) => s + r.ventPerdidas, 0) / rows.length),
            pctVacias90: Math.round(rows.reduce((s, r) => s + r.pctVacias90, 0) / rows.length),
        };
    }, [tablaRodeos]);

    // ── Idea 8: Tendencia 8 semanas ───────────────────────────────────────
    // Resumen ejecutivo: todo el establecimiento.
    const tendencia8Semanas = useMemo(() => calcularTendencia8Semanas(alertasRecientes), [alertasRecientes]);

    // Centro de mando: respeta el filtro de lote.
    const tendencia8SemanasLote = useMemo(() => {
        if (loteFilter === 'todos') return tendencia8Semanas;
        return calcularTendencia8Semanas(
            alertasRecientes.filter(a => animalMap.get(a.animalId)?.rodeoLoteId === loteFilter)
        );
    }, [alertasRecientes, animalMap, loteFilter, tendencia8Semanas]);

    // ── Idea 9: ¿Está actuando el equipo? ────────────────────────────────
    const equipoMetrics = useMemo(() => {
        const desde = Date.now() - periodoDias * DIA_MS;
        // Alertas generadas en el período. Los celos archivados cuentan como no atendidos; los
        // anestros archivados se cerraron solos al registrarse un ciclo, así que no son trabajo del equipo.
        const delPeriodo = alertasRecientes.filter(a =>
            new Date(a.tsGeneracion).getTime() >= desde &&
            !(a.estado === 'ARCHIVADA' && esAnestro(a))
        );
        const resueltas = delPeriodo.filter(a => a.estado === 'CONFIRMADA' || a.estado === 'DESCARTADA');
        const descartadas = resueltas.filter(a => a.estado === 'DESCARTADA').length;

        const pctAtendidas = delPeriodo.length > 0 ? Math.round((resueltas.length / delPeriodo.length) * 100) : 0;
        const pctDescartadas = resueltas.length > 0 ? Math.round((descartadas / resueltas.length) * 100) : 0;

        // Tiempo de atención: desde que se generó la alerta hasta que se confirmó o descartó.
        const tiempos = resueltas
            .filter(a => a.tsConfirmacion)
            .map(a => (new Date(a.tsConfirmacion!).getTime() - new Date(a.tsGeneracion).getTime()) / (1000 * 60 * 60));
        const buckets = [
            { label: '<6h', max: 6 },
            { label: '6-24h', max: 24 },
            { label: '1-3d', max: 72 },
            { label: '>3d', max: Infinity },
        ];
        const hist = buckets.map((b, i) => {
            const min = i === 0 ? -Infinity : buckets[i - 1].max;
            return { label: b.label, count: tiempos.filter(h => h >= min && h < b.max).length };
        });

        return { enCola: alertasPendientes.length, pctAtendidas, pctDescartadas, hist, totalAtendidas: tiempos.length };
    }, [alertasRecientes, alertasPendientes, periodoDias]);

    // ── Salud tab: KPIs ──────────────────────────────────────────────────────
    const kpiSaludSinAtender = useMemo(() =>
        alertasFiltradas.filter(a => a.tipo === 'SALUD' && horasTranscurridas(a.tsGeneracion) > 24).length,
        [alertasFiltradas]);

    const kpiEnCalibracion = useMemo(() =>
        animalesFiltrados.filter(a => a.estadoActual !== 'INACTIVO' && a.enCalibracion).length,
        [animalesFiltrados]);

    // ── Salud tab: Alertas de salud sin atender (todas las pendientes, la más vieja primero) ──
    const alertasSaludSinAtender = useMemo(() =>
        alertasFiltradas
            .filter(a => a.tipo === 'SALUD')
            .sort((a, b) => horasTranscurridas(b.tsGeneracion) - horasTranscurridas(a.tsGeneracion)),
        [alertasFiltradas]);

    // ── Salud tab: Excepciones del embudo ────────────────────────────────────
    const embudoExcepciones = useMemo(() => {
        const hace90d = 90 * 24 * 60 * 60 * 1000;
        const reproductivos = animalesFiltrados.filter(
            a => a.estadoActual !== 'INACTIVO' && esReproductiva(a)
        );

        const vaciasLargo = reproductivos.filter(a =>
            a.estadoReproductivo === 'VACIA' &&
            a.fechaUltimoParto &&
            Date.now() - new Date(a.fechaUltimoParto).getTime() > hace90d
        );

        const inseminadasSinDiag = reproductivos.filter(a =>
            a.estadoReproductivo === 'INSEMINADA' &&
            a.fechaUltimaInseminacion &&
            diffDias(a.fechaUltimaInseminacion) > INSEMINADA_DIAS_DIAG
        );

        // Anestro sin intervención: la alerta de anestro sigue pendiente (intervenir la cierra).
        const conAnestroPendiente = new Set(alertasPendientes.filter(esAnestro).map(a => a.animalId));
        const anestroSinInterv = reproductivos.filter(a => conAnestroPendiente.has(a.id));

        const totalConEstado = reproductivos.filter(
            a => a.estadoReproductivo && a.estadoReproductivo !== 'SIN_ESTADO'
        ).length;
        const completitudPct = reproductivos.length > 0
            ? Math.round((totalConEstado / reproductivos.length) * 100)
            : 0;

        return { vaciasLargo, inseminadasSinDiag, anestroSinInterv, completitudPct, totalRepro: reproductivos.length };
    }, [animalesFiltrados, alertasPendientes]);

    // ── Salud tab: Partos esperados ──────────────────────────────────────────
    const partosEsperados = useMemo(() => {
        const hoy = new Date();

        const gestantesConFecha = animalesFiltrados.filter(a =>
            a.estadoActual !== 'INACTIVO' &&
            a.estadoReproductivo === 'GESTANTE' &&
            a.fechaUltimaInseminacion
        );

        const sinFecha = animalesFiltrados.filter(a =>
            a.estadoActual !== 'INACTIVO' &&
            a.estadoReproductivo === 'GESTANTE' &&
            !a.fechaUltimaInseminacion
        ).length;

        // Agrupar por semana — próximas 12 semanas
        const semanas = Array.from({ length: 12 }, (_, w) => {
            const desde = new Date(hoy);
            desde.setDate(desde.getDate() + w * 7);
            const hasta = new Date(desde);
            hasta.setDate(hasta.getDate() + 7);

            // Label: "Sem 1", "Sem 2", etc., con fecha de inicio
            const dd = String(desde.getDate()).padStart(2, '0');
            const mm = String(desde.getMonth() + 1).padStart(2, '0');
            const label = `${dd}/${mm}`;

            const animalesSemana = gestantesConFecha.filter(a => {
                const ins = new Date(a.fechaUltimaInseminacion!);
                const parto = new Date(ins.getTime() + DIAS_GESTACION * 24 * 60 * 60 * 1000);
                return parto >= desde && parto < hasta;
            });

            return { label, count: animalesSemana.length, desde, hasta, animales: animalesSemana };
        });

        // Gestantes cuya fecha esperada ya pasó (posible parto sin registrar)
        const vencidos = gestantesConFecha.filter(a =>
            new Date(a.fechaUltimaInseminacion!).getTime() + DIAS_GESTACION * DIA_MS < hoy.getTime()
        ).length;

        return { semanas, sinFecha, vencidos, total: gestantesConFecha.length + sinFecha };
    }, [animalesFiltrados]);


    // ── Salud tab: Desempeño reproductivo ────────────────────────────────────
    const desempenyo = useMemo(() => {
        const periodoMs = periodoDias * DIA_MS;
        const enPeriodo = (ts: string) => Date.now() - new Date(ts).getTime() <= periodoMs;
        const reproductivos = animalesFiltrados.filter(a => a.estadoActual !== 'INACTIVO' && esReproductiva(a));
        const celosPeriodo = alertasRecientesLote.filter(a => esCeloConVentana(a) && enPeriodo(a.tsGeneracion));

        // Celo detectado / atendido: alertas de celo generadas en el período
        const celoDetectado = celosPeriodo.length;
        const celoAtendido = celosPeriodo.filter(a => a.estado === 'CONFIRMADA').length;

        // Ventanas perdidas: de esos celos, cuántos se archivaron sin atender
        const ventanasPerdidas = celosPeriodo.filter(a => a.estado === 'ARCHIVADA').length;

        // Inseminadas en el período, y de ellas las que hoy están gestantes
        const inseminadasPeriodo = reproductivos.filter(a =>
            a.fechaUltimaInseminacion && enPeriodo(a.fechaUltimaInseminacion)
        );
        const inseminadas = inseminadasPeriodo.length;
        const gestantes = inseminadasPeriodo.filter(a => a.estadoReproductivo === 'GESTANTE').length;

        return { celoDetectado, celoAtendido, ventanasPerdidas, inseminadas, gestantes, n: reproductivos.length };
    }, [animalesFiltrados, alertasRecientesLote, periodoDias]);

    // ── Salud tab: Reincidencia (Idea 13) ────────────────────────────────────
    const reincidencia = useMemo(() => {
        const periodoMs = periodoDias * DIA_MS;
        const now = Date.now();
        const alertasPeriodo = alertasRecientesLote.filter(a =>
            now - new Date(a.tsGeneracion).getTime() <= periodoMs
        );

        // Por animal: cantidad de alertas en el período
        const porAnimal = new Map<number, number>();
        alertasPeriodo.forEach(a => {
            porAnimal.set(a.animalId, (porAnimal.get(a.animalId) ?? 0) + 1);
        });

        const rankingAnimales = Array.from(porAnimal.entries())
            .filter(([, count]) => count >= 2)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5)
            .map(([animalId, count]) => ({ animal: animalMap.get(animalId), count }));

        // Por lote: salidas de perímetro repetidas
        const porLote = new Map<string, number>();
        alertasPeriodo.filter(a => a.tipo === 'FUERA_DE_PERIMETRO').forEach(a => {
            const anim = animalMap.get(a.animalId);
            const lote = anim?.rodeoLoteNombre ?? 'Sin lote';
            porLote.set(lote, (porLote.get(lote) ?? 0) + 1);
        });
        const rankingLotes = Array.from(porLote.entries())
            .filter(([, count]) => count >= 2)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 3)
            .map(([nombre, count]) => ({ nombre, count }));

        return { rankingAnimales, rankingLotes };
    }, [alertasRecientesLote, animalMap, periodoDias]);

    // ── Salud tab: Última intervención ───────────────────────────────────────
    const ultimaIntervencion = useMemo(() => {
        const activos = animalesFiltrados.filter(a => a.estadoActual !== 'INACTIVO');
        const idsActivos = new Set(activos.map(a => a.id));

        // La intervención más reciente del lote seleccionado
        const masReciente = (intervenciones ?? []).find(i => idsActivos.has(i.animalId)) ?? null;

        // Animales que llevan más tiempo sin intervenciones (nunca primero)
        const ranking = activos
            .map(a => {
                const ult = ultimaIntervPorAnimal.get(a.id);
                return { animal: a, dias: ult ? Math.floor(diffDias(ult.fechaHora)) : null };
            })
            .sort((a, b) => {
                if (a.dias === null && b.dias === null) return 0;
                if (a.dias === null) return -1;
                if (b.dias === null) return 1;
                return b.dias - a.dias;
            })
            .slice(0, 5);

        return { masReciente, ranking };
    }, [animalesFiltrados, intervenciones, ultimaIntervPorAnimal]);

    // Abre el listado de animales filtrado por estos ids
    const verListaAnimales = (lista: AnimalResponse[]) =>
        navigate(`/animales?ids=${lista.map(a => a.id).join(',')}`);

    // ── KPIs de header ────────────────────────────────────────────────────────
    const kpiAlertasPendientes = alertasFiltradas.length;
    const kpiVentanasAbiertas = ventanaItems.length;
    const kpiBajoObservacion = animalesARevisar.length;
    const kpiCobertura = cobertura.pct;
    const kpiVentanasSubtexto = (() => {
        const urgentes = ventanaItems.filter(v => v.ventana.tiempoRestanteSegundos / 3600 <= 3);
        return urgentes.length > 0 ? `${urgentes.length} por vencer en < 3h` : 'sin urgencia inmediata';
    })();
    const kpiObsSubtexto = (() => {
        const sinRevision = animalesARevisar.filter(a => !a.estadoSalud || a.estadoSalud === 'ENFERMO').length;
        return sinRevision > 0 ? `${sinRevision} enfermos` : 'en observación';
    })();


    // ── Render ────────────────────────────────────────────────────────────────

    // ── Resumen ejecutivo view ────────────────────────────────────────────
    function ResumenEjecutivoView() {
        if (loading) {
            return <div className="dash-loading-center" style={{ padding: 60 }}><Spinner animation="border" /></div>;
        }

        // Umbrales para colorear celdas
        const COB_UMBRAL_BAJO = 80; // < 80% cobertura → rojo
        const ALERTAS100_UMBRAL = mediaEstab.alertas100 * 1.4; // >40% sobre la media → rojo
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

        const maxHist = Math.max(...equipoMetrics.hist.map(b => b.count), 1);

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

                        {rodeos.length === 0 ? (
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
                                            <th>% vacías &gt; 90 d</th>
                                            <th></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {tablaRodeos.map(row => (
                                            <tr key={row.rodeo.id}>
                                                <td>
                                                    <span className="dash-exec-rodeo-name">{row.rodeo.nombre}</span>
                                                </td>
                                                <td className="dash-exec-cell-normal">{row.totalAnim}</td>
                                                <td>{cobCell(row.cobPct)}</td>
                                                <td className="dash-exec-cell-normal">{row.alertasPend}</td>
                                                <td>{alertas100Cell(row.alertas100, row.alertasPend)}</td>
                                                <td className="dash-exec-cell-normal">{row.ventPerdidas}</td>
                                                <td>{vacias90Cell(row.pctVacias90)}</td>
                                                <td>
                                                    <button
                                                        className="dash-exec-ver-btn"
                                                        onClick={() => navigate(`/animales?rodeo=${row.rodeo.id}`)}
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

                        {tendencia8Semanas.tipos.every(t => tendencia8Semanas.datos[t].every(v => v === 0)) ? (
                            <p className="dash-no-items">
                                <i className="bi bi-bar-chart" style={{ color: '#94A3B8' }} />
                                Sin datos de alertas en las últimas 8 semanas
                            </p>
                        ) : (
                            <TrendChart
                                datos={tendencia8Semanas.datos}
                                semanas={tendencia8Semanas.semanas}
                                tipos={tendencia8Semanas.tipos}
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
                                <span className="dash-team-kpi-val">{equipoMetrics.enCola}</span>
                                <span className="dash-team-kpi-sub">pendientes ahora</span>
                            </div>
                            <div className="dash-team-kpi">
                                <span className="dash-team-kpi-label">Atendidas</span>
                                <span className="dash-team-kpi-val">{equipoMetrics.pctAtendidas}%</span>
                                <span className="dash-team-kpi-sub">de las generadas</span>
                            </div>
                            <div className="dash-team-kpi">
                                <span className="dash-team-kpi-label">Descartadas</span>
                                <span className="dash-team-kpi-val">{equipoMetrics.pctDescartadas}%</span>
                                <span className="dash-team-kpi-sub">de las atendidas</span>
                            </div>
                        </div>

                        <div className="dash-team-hist-label">
                            Tiempo de atención
                        </div>
                        {equipoMetrics.totalAtendidas === 0 ? (
                            <p className="dash-no-items">
                                <i className="bi bi-hourglass" style={{ color: '#94A3B8' }} />
                                Sin alertas atendidas en los últimos {periodoDias} días
                            </p>
                        ) : (
                            <div className="dash-team-hist">
                                {equipoMetrics.hist.map(bucket => (
                                    <div key={bucket.label} className="dash-team-hist-bar-wrap">
                                        <span className="dash-team-hist-count">{bucket.count}</span>
                                        <div
                                            className="dash-team-hist-bar"
                                            style={{ height: `${Math.round((bucket.count / maxHist) * 52)}px` }}
                                            title={`${bucket.count} alertas`}
                                        />
                                        <span className="dash-team-hist-bar-label">{bucket.label}</span>
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
        if (loading) {
            return <div className="dash-loading-center" style={{ padding: 60 }}><Spinner animation="border" /></div>;
        }

        const maxParto = Math.max(...partosEsperados.semanas.map(s => s.count), 1);
        const maxDesempenyo = Math.max(
            desempenyo.celoDetectado, desempenyo.celoAtendido, desempenyo.inseminadas, desempenyo.gestantes, 1
        );

        return (
            <>
                {/* ── STRIP DE 4 KPIs ────────────────────────────────────── */}
                <div className="dash-salud-kpi-row">
                    <div className="dash-salud-kpi">
                        <span className="dash-salud-kpi-label">Días abiertos (promedio)</span>
                        <span className="dash-salud-kpi-val">{embudo.diasAbiertosProm}</span>
                        <span className="dash-salud-kpi-sub">
                            {embudo.nAbiertas > 0 ? `${embudo.nAbiertas} vacas abiertas con parto registrado` : 'sin vacas abiertas con parto registrado'}
                        </span>
                    </div>
                    <div className="dash-salud-kpi">
                        <span className="dash-salud-kpi-label">Ventanas perdidas ({periodoDias} d)</span>
                        <span className="dash-salud-kpi-val dash-salud-kpi-warn">
                            {desempenyo.celoDetectado > 0
                                ? `${Math.round((desempenyo.ventanasPerdidas / desempenyo.celoDetectado) * 100)}%`
                                : '0%'
                            }
                        </span>
                        <span className="dash-salud-kpi-sub">{desempenyo.ventanasPerdidas} de {desempenyo.celoDetectado} celos</span>
                    </div>
                    <div className="dash-salud-kpi">
                        <span className="dash-salud-kpi-label">Salud sin atender</span>
                        <span className={`dash-salud-kpi-val ${kpiSaludSinAtender > 0 ? 'dash-salud-kpi-danger' : ''}`}>
                            {kpiSaludSinAtender}
                        </span>
                        <span className="dash-salud-kpi-sub">&gt; 24 h desde la alerta</span>
                    </div>
                    <div className="dash-salud-kpi">
                        <span className="dash-salud-kpi-label">En calibración</span>
                        <span className="dash-salud-kpi-val dash-salud-kpi-muted">{kpiEnCalibracion}</span>
                        <span className="dash-salud-kpi-sub">sin monitoreo de salud</span>
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

                        {embudoExcepciones.totalRepro === 0 ? (
                            <p className="dash-no-items">Sin animales reproductivos registrados</p>
                        ) : (
                            <>
                                {/* Barra horizontal */}
                                <div className="dash-embudo-bar-wrap">
                                    {Object.entries(embudo.dist)
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
                                    {Object.entries(embudo.dist).filter(([, v]) => v > 0).map(([estado, count]) => (
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
                                            {' '}con &gt; {DIAS_ABIERTOS_URGENTE} días abiertos
                                        </div>
                                        <div className="dash-salud-exc-action">
                                            <strong>{embudoExcepciones.vaciasLargo.length}</strong>
                                            {embudoExcepciones.vaciasLargo.length > 0 && (
                                                <button
                                                    className="dash-ver-todas-btn"
                                                    onClick={() => verListaAnimales(embudoExcepciones.vaciasLargo)}
                                                >
                                                    — ver lista
                                                </button>
                                            )}
                                        </div>
                                    </div>

                                    <div className="dash-salud-exc-row">
                                        <div className="dash-salud-exc-desc">
                                            <span className="dash-salud-exc-state inseminada">INSEMINADA</span>
                                            {' '}sin diagnóstico &gt; {INSEMINADA_DIAS_DIAG} d
                                        </div>
                                        <div className="dash-salud-exc-action">
                                            <strong>{embudoExcepciones.inseminadasSinDiag.length}</strong>
                                            {embudoExcepciones.inseminadasSinDiag.length > 0 && (
                                                <button
                                                    className="dash-ver-todas-btn"
                                                    onClick={() => verListaAnimales(embudoExcepciones.inseminadasSinDiag)}
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
                                            <strong>{embudoExcepciones.anestroSinInterv.length}</strong>
                                            {embudoExcepciones.anestroSinInterv.length > 0 && (
                                                <button
                                                    className="dash-ver-todas-btn"
                                                    onClick={() => verListaAnimales(embudoExcepciones.anestroSinInterv)}
                                                >
                                                    — ver lista
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                </div>

                                <div className="dash-salud-completitud">
                                    Completitud del dato: {embudoExcepciones.completitudPct}% de los animales con estado registrado
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
                            {alertasSaludSinAtender.length === 0 ? (
                                <p className="dash-no-items" style={{ margin: '6px 0' }}>
                                    <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                    {' '}Sin alertas de salud pendientes
                                </p>
                            ) : (
                                alertasSaludSinAtender.slice(0, REVISAR_MAX).map(alerta => {
                                    const animal = animalMap.get(alerta.animalId);
                                    const h = horasTranscurridas(alerta.tsGeneracion);
                                    const dias = Math.floor(h / 24);
                                    const sevDot = alerta.severidad === 'ALTA' ? 'dash-sev-dot-alta'
                                        : alerta.severidad === 'MEDIA' ? 'dash-sev-dot-media'
                                        : 'dash-sev-dot-baja';
                                    return (
                                        <div
                                            key={alerta.id}
                                            className="dash-salud-revisar-row"
                                            onClick={() => animal && navigate(`/animales/${animal.id}`)}
                                        >
                                            <span className={`dash-sev-dot ${sevDot}`} />
                                            <span className="dash-salud-revisar-nombre">{nombreAnimal(animal)}</span>
                                            <span className={`dash-salud-revisar-tiempo ${h > 24 ? 'dash-salud-revisar-dias-urgente' : ''}`}>
                                                {dias > 0 ? `${dias} d` : `${Math.floor(h)}h`}
                                            </span>
                                        </div>
                                    );
                                })
                            )}
                            {alertasSaludSinAtender.length > REVISAR_MAX && (
                                <button className="dash-ver-todas-btn" onClick={() => navigate('/alertas')}>
                                    +{alertasSaludSinAtender.length - REVISAR_MAX} más — ver alertas
                                </button>
                            )}
                        </div>

                        <div className="dash-salud-revisar-divider" />

                        {/* Sub-sección: En observación / enfermos */}
                        <div className="dash-salud-revisar-section">
                            <span className="dash-salud-revisar-seclabel">En observación / enfermos</span>
                            {animalesARevisar.length === 0 ? (
                                <p className="dash-no-items" style={{ margin: '6px 0' }}>
                                    <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                    {' '}Todos los animales sanos
                                </p>
                            ) : (
                                animalesARevisar.slice(0, REVISAR_MAX).map(a => {
                                    const ultimaInterv = ultimaIntervPorAnimal.get(a.id);
                                    const diasSinRevision = ultimaInterv ? Math.floor(diffDias(ultimaInterv.fechaHora)) : null;
                                    const esEnfermo = a.estadoSalud === 'ENFERMO';
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
                                                className={`dash-salud-revisar-dias ${intervenciones && sinRevisionLargo ? 'dash-salud-revisar-dias-urgente' : ''}`}
                                                title={ultimaInterv ? `Última intervención: ${TIPO_INTERVENCION_LABEL[ultimaInterv.tipo]}` : undefined}
                                            >
                                                {!intervenciones ? '—'
                                                    : diasSinRevision !== null ? `${diasSinRevision} d sin revisión`
                                                        : 'sin intervenciones'}
                                            </span>
                                        </div>
                                    );
                                })
                            )}
                            {animalesARevisar.length > REVISAR_MAX && (
                                <button className="dash-ver-todas-btn" onClick={() => navigate('/animales?tab=salud')}>
                                    +{animalesARevisar.length - REVISAR_MAX} más — ver animales
                                </button>
                            )}
                        </div>
                    </div>
                </div>

                {/* ── GRID MEDIO (2 cols) ─────────────────────────────────── */}
                <div className="dash-salud-mid-grid">

                    {/* ── IZQUIERDA: Partos esperados ──────────────────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-calendar2-event-fill" />
                                Partos esperados
                            </p>

                        </div>

                        {partosEsperados.total === 0 ? (
                            <p className="dash-no-items">Sin animales gestantes registrados</p>
                        ) : (
                            <>
                                <div className="dash-partos-chart">
                                    {partosEsperados.semanas.map((sem, i) => (
                                        <div key={i} className={`dash-partos-bar-col ${sem.count > 0 ? 'dash-partos-bar-col-active' : ''}`}
                                            title={sem.count === 0 ? '' : (() => {
                                                const MAX_SHOW = 5;
                                                const nombres = sem.animales.map(a => nombreAnimal(a));
                                                const header = `📅 Semana ${sem.label} — ${sem.count} parto${sem.count > 1 ? 's' : ''} esperado${sem.count > 1 ? 's' : ''}`;
                                                if (nombres.length <= MAX_SHOW) {
                                                    return `${header}\n${nombres.map(n => `• ${n}`).join('\n')}`;
                                                }
                                                const resto = nombres.length - MAX_SHOW;
                                                return `${header}\n${nombres.slice(0, MAX_SHOW).map(n => `• ${n}`).join('\n')}\n• …y ${resto} más`;
                                            })()}
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
                                <div className="dash-partos-sub">
                                    Por semana, próximas 12 semanas
                                    {partosEsperados.sinFecha > 0 && (
                                        <span className="dash-partos-subfecha">
                                            {' '}· {partosEsperados.sinFecha} sin fecha de inseminación
                                        </span>
                                    )}
                                    {partosEsperados.vencidos > 0 && (
                                        <span className="dash-partos-subfecha">
                                            {' '}· {partosEsperados.vencidos} con fecha de parto vencida
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

                        {desempenyo.n === 0 ? (
                            <p className="dash-no-items">Sin animales reproductivos registrados</p>
                        ) : (
                            <>
                                <div className="dash-desempenyo-funnel">
                                    {[
                                        { label: 'Celo detectado', val: desempenyo.celoDetectado, cls: 'dash-desempenyo-bar-celo' },
                                        { label: 'Atendido',        val: desempenyo.celoAtendido,  cls: 'dash-desempenyo-bar-atend' },
                                        { label: 'Inseminado',      val: desempenyo.inseminadas,   cls: 'dash-desempenyo-bar-ins' },
                                        { label: 'Gestante',        val: desempenyo.gestantes,     cls: 'dash-desempenyo-bar-gest' },
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

                        {reincidencia.rankingAnimales.length === 0 && reincidencia.rankingLotes.length === 0 ? (
                            <p className="dash-no-items">
                                <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                {' '}Sin reincidentes en {periodoDias} d
                            </p>
                        ) : (
                            <>
                                {reincidencia.rankingAnimales.map((item, i) => (
                                    <div
                                        key={i}
                                        className="dash-salud-reincidencia-row"
                                        onClick={() => item.animal && navigate(`/animales/${item.animal.id}`)}
                                    >
                                        <span className="dash-salud-reincidencia-nombre">{nombreAnimal(item.animal)}</span>
                                        <span className="dash-salud-reincidencia-count">{item.count} alertas / {periodoDias} d</span>
                                    </div>
                                ))}
                                {reincidencia.rankingLotes.length > 0 && (
                                    <>
                                        <div className="dash-salud-revisar-divider" />
                                        {reincidencia.rankingLotes.map((item, i) => (
                                            <div key={i} className="dash-salud-reincidencia-row">
                                                <span className="dash-salud-reincidencia-nombre">{item.nombre}</span>
                                                <span className="dash-salud-reincidencia-count">{item.count} salidas de perímetro</span>
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

                        {intervenciones === null ? (
                            <p className="dash-no-items">No se pudieron cargar las intervenciones</p>
                        ) : (
                            <>
                                {ultimaIntervencion.masReciente ? (
                                    <div
                                        className="dash-salud-interv-ultima"
                                        onClick={() => navigate(`/animales/${ultimaIntervencion.masReciente!.animalId}`)}
                                    >
                                        <span className="dash-salud-interv-ultima-tipo">
                                            {TIPO_INTERVENCION_LABEL[ultimaIntervencion.masReciente.tipo]}
                                        </span>
                                        {' · '}{nombreAnimal(animalMap.get(ultimaIntervencion.masReciente.animalId))}
                                        {' · '}{ultimaIntervencion.masReciente.interventor}
                                        {' · '}hace {formatHoras(horasTranscurridas(ultimaIntervencion.masReciente.fechaHora))}
                                    </div>
                                ) : (
                                    <p className="dash-no-items">Sin intervenciones registradas</p>
                                )}

                                <div className="dash-salud-interv-note">Más tiempo sin intervenciones</div>
                                {ultimaIntervencion.ranking.map(item => (
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
                        )}
                    </div>

                    {/* ── Ganancia de peso (Idea 17) ────────────────────────── */}
                    <div className="dash-card">
                        <div className="dash-card-header">
                            <p className="dash-card-title">
                                <i className="bi bi-graph-up-arrow" />
                                Ganancia de peso
                            </p>

                        </div>
                        {/* TODO: implementar cuando exista endpoint GET /pesos?establecimientoId={id} */}
                        <div className="dash-salud-peso-empty">
                            <i className="bi bi-rulers" style={{ fontSize: '1.6rem', color: '#CBD5E1' }} />
                            <span className="dash-salud-peso-empty-text">
                                Disponible cuando haya pesadas cargadas
                            </span>
                            <span className="dash-salud-peso-empty-sub">
                                Solo si hay ≥ 2 pesadas · se oculta si la cobertura es baja
                            </span>
                        </div>
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
                        onClick={fetchDashboard}
                        disabled={loading}
                    >
                        {loading
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

            {/* ── TAB: Resumen ejecutivo ───────────────────────────────────── */}
            {activeTab === 'ejecutivo' && ResumenEjecutivoView()}

            {/* ── TAB: Salud y reproducción ─────────────────────────────────── */}
            {activeTab === 'salud' && SaludReproduccionView()}

            {/* ── TAB: Centro de mando ──────────────────────────────────────── */}
            {activeTab === 'mando' && (

            <>
            {/* ── KPI Chips ──────────────────────────────────────────────── */}

            <div className="dash-kpi-row">
                {/* Alertas pendientes */}
                <div className="dash-kpi-chip">
                    <div className="dash-kpi-chip-label">
                        Alertas pendientes
                    </div>
                    {loading ? <Spinner animation="border" size="sm" /> : (
                        <div className="dash-kpi-chip-val">{kpiAlertasPendientes}</div>
                    )}
                    <div className="dash-kpi-chip-sub">
                        {alertasFiltradas.filter(a => a.tipo === 'CELO').length} celo
                        · {alertasFiltradas.filter(a => a.tipo === 'SALUD').length} salud
                        · {alertasFiltradas.filter(a => a.tipo === 'FUERA_DE_PERIMETRO').length} perímetro
                    </div>
                </div>

                {/* Ventanas abiertas */}
                <div className="dash-kpi-chip">
                    <div className="dash-kpi-chip-label">
                        Ventanas abiertas
                    </div>
                    {loading ? <Spinner animation="border" size="sm" /> : (
                        <div className="dash-kpi-chip-val">{kpiVentanasAbiertas}</div>
                    )}
                    <div className="dash-kpi-chip-sub">{kpiVentanasSubtexto}</div>
                </div>

                {/* Bajo observación */}
                <div className="dash-kpi-chip">
                    <div className="dash-kpi-chip-label">
                        Bajo observación
                    </div>
                    {loading ? <Spinner animation="border" size="sm" /> : (
                        <div className="dash-kpi-chip-val">{kpiBajoObservacion}</div>
                    )}
                    <div className="dash-kpi-chip-sub">{kpiObsSubtexto}</div>
                </div>

                {/* Cobertura */}
                <div className="dash-kpi-chip">
                    <div className="dash-kpi-chip-label">
                        Cobertura de caravanas
                    </div>
                    {loading ? <Spinner animation="border" size="sm" /> : (
                        <div className="dash-kpi-chip-val">{kpiCobertura}%</div>
                    )}
                    <div className="dash-kpi-chip-sub">
                        {cobertura.silencio > 0 ? `${cobertura.silencio} sin señal · ` : ''}
                        {cobertura.bateriaBaja > 0 ? `${cobertura.bateriaBaja} batería baja` : 'caravanas ok'}
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
                                Ver todas ({kpiAlertasPendientes}) <i className="bi bi-arrow-right" />
                            </button>
                        </div>

                        {loading ? (
                            <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                        ) : colaItems.length === 0 ? (
                            <p className="dash-no-items">
                                <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                Sin alertas pendientes — todo en orden
                            </p>
                        ) : (
                            <>
                                <div className="dash-cola-header-row">
                                    <span /> {/* Spacer para severidad */}
                                    <span>Animal</span>
                                    <span>Acción recomendada</span>
                                    <span style={{ textAlign: 'center' }}>Tipo</span>
                                    <span style={{ textAlign: 'center' }}>Antigüedad</span>
                                    <span /> {/* Spacer para boton */}
                                </div>
                                {colaItems.slice(0, 8).map((item, i) => {
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
                                                <div className="dash-cola-rec" title={textoAccionRecomendada(alerta)}>
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
                                                <span className={tiempoClass}>{tiempoLabel}</span>
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
                            {!loading && (
                                <span className="dash-card-tag">
                                    Oportunidades perdidas (30d): {celoArchivadasRecientes}
                                </span>
                            )}
                        </div>

                        {loading ? (
                            <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                        ) : ventanaItems.length === 0 ? (
                            <p className="dash-no-items">
                                <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                Sin ventanas activas
                            </p>
                        ) : (
                            ventanaItems.map(({ alerta, animal, ventana }) => {
                                const isFutura = ventana.estado === 'FUTURA';
                                const vc = isFutura ? 'futura' : ventanaClass(ventana.tiempoRestanteSegundos);
                                const pct = isFutura ? 0 : Math.min(100, Math.max(0, ventana.porcentajeTranscurrido * 100));
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
                                ¿Puedo confiar en el monitoreo?
                            </p>
                        </div>

                        {loading ? (
                            <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                        ) : (
                            <>
                                <div className="dash-coverage-pct">
                                    {cobertura.pct}%
                                </div>
                                <div className="dash-coverage-sub">del rodeo vigilado</div>

                                <div className="dash-coverage-bar-wrap">
                                    {cobertura.ok > 0 && (
                                        <div className="dash-coverage-seg dash-coverage-ok"
                                            style={{ flex: cobertura.ok }} title={`Monitoreados: ${cobertura.ok}`} />
                                    )}
                                    {cobertura.calibrando > 0 && (
                                        <div className="dash-coverage-seg dash-coverage-calibrando"
                                            style={{ flex: cobertura.calibrando }} title={`Calibrando: ${cobertura.calibrando}`} />
                                    )}
                                    {cobertura.bateriaBaja > 0 && (
                                        <div className="dash-coverage-seg dash-coverage-bateria"
                                            style={{ flex: cobertura.bateriaBaja }} title={`Batería baja: ${cobertura.bateriaBaja}`} />
                                    )}
                                    {cobertura.silencio > 0 && (
                                        <div className="dash-coverage-seg dash-coverage-silencio"
                                            style={{ flex: cobertura.silencio }} title={`Sin señal: ${cobertura.silencio}`} />
                                    )}
                                    {cobertura.sinCaravana > 0 && (
                                        <div className="dash-coverage-seg dash-coverage-sincaravana"
                                            style={{ flex: cobertura.sinCaravana }} title={`Sin caravana: ${cobertura.sinCaravana}`} />
                                    )}
                                </div>

                                <div className="dash-coverage-legend">
                                    {[
                                        { label: 'Monitoreados', count: cobertura.ok, cls: 'dash-coverage-ok' },
                                        { label: 'En calibración', count: cobertura.calibrando, cls: 'dash-coverage-calibrando' },
                                        { label: 'Sin señal > 6h', count: cobertura.silencio, cls: 'dash-coverage-silencio' },
                                        { label: `Batería ≤ ${BATERIA_BAJA_PCT}%`, count: cobertura.bateriaBaja, cls: 'dash-coverage-bateria' },
                                        { label: 'Sin caravana', count: cobertura.sinCaravana, cls: 'dash-coverage-sincaravana' },
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
                        {!loading && embudo.inseminadasPendienteDiag > 0 && (
                            <span style={{ fontSize: '0.75rem', color: '#ECC94B' }}>
                                {embudo.inseminadasPendienteDiag} esperan diagnóstico
                            </span>
                        )}
                    </div>

                    {loading ? (
                        <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                    ) : embudo.totalRepro === 0 ? (
                        <p className="dash-no-items">Sin animales reproductivos registrados</p>
                    ) : (
                        <>
                            <div className="dash-embudo-bar-wrap">
                                {Object.entries(embudo.dist)
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
                                {Object.entries(embudo.dist).filter(([, v]) => v > 0).map(([estado, count]) => (
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
                    {loading ? (
                        <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                    ) : tendencia8SemanasLote.tipos.every(t => tendencia8SemanasLote.datos[t].every(v => v === 0)) ? (
                        <p className="dash-no-items">
                            <i className="bi bi-bar-chart" style={{ color: '#94A3B8' }} />
                            Sin alertas en las últimas 8 semanas
                        </p>
                    ) : (
                        <TrendChart
                            datos={tendencia8SemanasLote.datos}
                            semanas={tendencia8SemanasLote.semanas}
                            tipos={tendencia8SemanasLote.tipos}
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
