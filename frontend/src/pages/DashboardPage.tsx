/* eslint-disable react-hooks/purity, react-hooks/set-state-in-effect */
import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Spinner, Alert } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

import { getAnimales, ESTADO_REPRODUCTIVO_LABEL } from '../api/animalesApi';
import type { AnimalResponse } from '../api/animalesApi';
import { getAlertas, getVentanaInseminacion } from '../api/alertaApi';
import type { AlertaResponse, VentanaInseminacionResponse } from '../api/alertaApi';
import { getRodeoLotes } from '../api/rodeoLotesApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import { getAnimalesMapa } from '../api/animalesApi';
import type { AnimalMapa } from '../api/animalesApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import './DashboardPage.css';

// ── Fix default Leaflet icons (same as MapPage) ──────────────────────────────
// eslint-disable-next-line @typescript-eslint/no-explicit-any
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
    iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// ── Helpers ──────────────────────────────────────────────────────────────────

const SILENCIO_THRESHOLD_H = 6;     // collar sin señal > 6h = silencioso
const BATERIA_BAJA_PCT = 20;        // batería ≤ 20% = crítica
const DIAS_ABIERTOS_URGENTE = 90;   // días abiertos para mostrar en embudo
const INSEMINADA_DIAS_DIAG = 35;    // inseminada > 35d sin confirmación

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
function getWeekKey(ts: string): string {
    const d = new Date(ts);
    const jan4 = new Date(d.getFullYear(), 0, 4);
    const week = Math.ceil(((d.getTime() - jan4.getTime()) / 86400000 + jan4.getDay() + 1) / 7);
    return `${d.getFullYear()}-W${String(week).padStart(2, '0')}`;
}

function last8WeekKeys(): string[] {
    const keys: string[] = [];
    for (let i = 7; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i * 7);
        keys.push(getWeekKey(d.toISOString()));
    }
    return [...new Set(keys)].slice(-8);
}

export default function DashboardPage() {
    const selectedId = useEstablecimientoStore(s => s.selectedId);
    const navigate = useNavigate();

    const [activeTab, setActiveTab] = useState<'mando' | 'ejecutivo' | 'salud'>('mando');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [loteFilter, setLoteFilter] = useState<number | 'todos'>('todos');

    // Raw data
    const [animales, setAnimales] = useState<AnimalResponse[]>([]);
    const [alertasPendientes, setAlertasPendientes] = useState<AlertaResponse[]>([]);
    const [alertasArchivadasCelo, setAlertasArchivadasCelo] = useState<AlertaResponse[]>([]);
    const [rodeos, setRodeos] = useState<RodeoLoteResponse[]>([]);
    const [animalesMapa, setAnimalesMapa] = useState<AnimalMapa[]>([]);

    // Ventanas de celo (mapa alertaId → ventana)
    const [ventanas, setVentanas] = useState<Map<number, VentanaInseminacionResponse>>(new Map());

    // Tambien necesitamos todas las alertas de los ultimos 60 dias para tendencias
    const [todasAlertas60d, setTodasAlertas60d] = useState<AlertaResponse[]>([]);

    const fetchDashboard = useCallback(async () => {
        if (!selectedId) return;
        setLoading(true);
        setError(null);
        try {
            const [anim, pendientes, celoArchivadas, rodeosData, mapaData, confirmadas, descartadas] = await Promise.all([
                getAnimales(selectedId),
                getAlertas(selectedId, undefined, undefined, 'PENDIENTE'),
                getAlertas(selectedId, 'CELO', undefined, 'ARCHIVADA'),
                getRodeoLotes(selectedId),
                getAnimalesMapa(selectedId),
                getAlertas(selectedId, undefined, undefined, 'CONFIRMADA'),
                getAlertas(selectedId, undefined, undefined, 'DESCARTADA'),
            ]);
            setAnimales(anim);
            setAlertasPendientes(pendientes);
            setAlertasArchivadasCelo(celoArchivadas);
            setRodeos(rodeosData.filter(r => r.activo));
            setAnimalesMapa(mapaData);
            // Para tendencias y métricas de equipo: todas las alertas de los últimos 60d
            const hace60d = 60 * 24 * 60 * 60 * 1000;
            const todas = [...pendientes, ...celoArchivadas, ...confirmadas, ...descartadas].filter(
                a => Date.now() - new Date(a.tsGeneracion).getTime() <= hace60d
            );
            setTodasAlertas60d(todas);

            // Fetch ventanas para cada alerta de celo PENDIENTE
            const celoPendientes = pendientes.filter(a => a.tipo === 'CELO');
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

    // ── Idea 1: Cola de atención ─────────────────────────────────────────────
    const colaItems: ColaItem[] = useMemo(() => {
        const items: ColaItem[] = alertasFiltradas.map(a => ({
            alerta: a,
            animal: animalMap.get(a.animalId),
            ventana: ventanas.get(a.id),
        }));

        // Detectar collares silenciosos (no collar o sin señal > 6h)
        const activosSinCollar = animalesFiltrados.filter(
            a => a.estadoActual !== 'INACTIVO' && (
                !a.caravanaId ||
                (a.caravanaUltimaSenial && horasTranscurridas(a.caravanaUltimaSenial) > SILENCIO_THRESHOLD_H)
            )
        );
        // Los collares silenciosos como "alertas sintéticas" — sólo si no tienen ya una alerta
        const alertaIds = new Set(alertasFiltradas.map(a => a.animalId));
        activosSinCollar.filter(a => !alertaIds.has(a.id)).forEach(a => {
            items.push({
                alerta: {
                    id: -a.id, // ID sintético negativo
                    animalId: a.id,
                    tipo: 'COLLAR',
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
            a => Date.now() - new Date(a.tsGeneracion).getTime() <= hace30d
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

    // ── Idea 4: Cobertura de collares ─────────────────────────────────────────
    const cobertura = useMemo(() => {
        const elegibles = animalesFiltrados.filter(a => a.estadoActual !== 'INACTIVO');
        const total = elegibles.length;
        if (total === 0) return { pct: 0, ok: 0, calibrando: 0, silencio: 0, bateriaBaja: 0, sinCollar: 0, total: 0 };

        let ok = 0, calibrando = 0, silencio = 0, bateriaBaja = 0, sinCollar = 0;
        elegibles.forEach(a => {
            if (!a.caravanaId) { sinCollar++; return; }
            if (a.enCalibracion) { calibrando++; return; }
            const hs = a.caravanaUltimaSenial ? horasTranscurridas(a.caravanaUltimaSenial) : Infinity;
            if (hs > SILENCIO_THRESHOLD_H) { silencio++; return; }
            if (a.caravanaBateria !== null && a.caravanaBateria <= BATERIA_BAJA_PCT) { bateriaBaja++; return; }
            ok++;
        });
        return { pct: Math.round((ok / total) * 100), ok, calibrando, silencio, bateriaBaja, sinCollar, total };
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
        let diasAbiertosProm = 0;
        let inseminadasPendienteDiag = 0;

        reproductivos.forEach(a => {
            const estado = a.estadoReproductivo ?? 'SIN_ESTADO';
            dist[estado] = (dist[estado] ?? 0) + 1;

            // Días abiertos
            if ((estado === 'VACIA' || estado === 'CELO' || estado === 'INSEMINADA') && a.fechaUltimoParto) {
                diasAbiertosProm += diffDias(a.fechaUltimoParto);
            }
            // Inseminadas sin diagnóstico
            if (estado === 'INSEMINADA' && a.fechaUltimaInseminacion) {
                if (diffDias(a.fechaUltimaInseminacion) > INSEMINADA_DIAS_DIAG) inseminadasPendienteDiag++;
            }
        });

        const totalRepro = reproductivos.length;
        return { dist, totalRepro, diasAbiertosProm: totalRepro > 0 ? Math.round(diasAbiertosProm / totalRepro) : 0, inseminadasPendienteDiag };
    }, [animalesFiltrados]);

    // ── Idea 10: Tabla por rodeo (ejecutivo) ──────────────────────────────
    const tablaRodeos = useMemo(() => {
        const hace30d = 30 * 24 * 60 * 60 * 1000;
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

            // Ventanas perdidas (celo archivadas del rodeo, últimos 30d)
            const ventPerdidas = alertasArchivadasCelo.filter(al => {
                const anim = animalMap.get(al.animalId);
                return anim?.rodeoLoteId === rodeo.id &&
                    Date.now() - new Date(al.tsGeneracion).getTime() <= hace30d;
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
    }, [rodeos, animales, alertasPendientes, alertasArchivadasCelo, animalMap]);

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
    const tendencia8Semanas = useMemo(() => {
        const semanas = last8WeekKeys();

        type TipoTendencia = 'CELO' | 'SALUD' | 'FUERA_DE_PERIMETRO';
        const tipos: TipoTendencia[] = ['CELO', 'SALUD', 'FUERA_DE_PERIMETRO'];

        const datos: Record<TipoTendencia, number[]> = { CELO: [], SALUD: [], FUERA_DE_PERIMETRO: [] };

        semanas.forEach(sem => {
            tipos.forEach(tipo => {
                const count = todasAlertas60d.filter(a =>
                    a.tipo === tipo && getWeekKey(a.tsGeneracion) === sem
                ).length;
                datos[tipo].push(count);
            });
        });

        return { semanas, datos, tipos };
    }, [todasAlertas60d]);

    // ── Idea 9: ¿Está actuando el equipo? ────────────────────────────────
    const equipoMetrics = useMemo(() => {
        const confirmadas = todasAlertas60d.filter(a => a.estado === 'CONFIRMADA');
        const descartadas = todasAlertas60d.filter(a => a.estado === 'DESCARTADA');
        const pendientes30d = todasAlertas60d.filter(a => a.estado === 'PENDIENTE');
        const atendidas = confirmadas.length + descartadas.length;
        const total = atendidas + pendientes30d.length;

        // Mediana de tiempo en cola (en horas) de las alertas pendientes actuales
        const horas = alertasPendientes.map(a => horasTranscurridas(a.tsGeneracion)).sort((a, b) => a - b);
        const mediana = horas.length > 0
            ? horas[Math.floor(horas.length / 2)]
            : 0;

        const pctAtendidas = total > 0 ? Math.round((atendidas / total) * 100) : 0;
        const pctDescartadas = atendidas > 0 ? Math.round((descartadas.length / atendidas) * 100) : 0;

        // Histograma de antigüedad de pendientes actuales
        const buckets = [
            { label: '<6h', max: 6 },
            { label: '6-24h', max: 24 },
            { label: '1-3d', max: 72 },
            { label: '>3d', max: Infinity },
        ];
        const hist = buckets.map(b => ({
            label: b.label,
            count: alertasPendientes.filter(a => {
                const h = horasTranscurridas(a.tsGeneracion);
                const prev = buckets[buckets.indexOf(b) - 1]?.max ?? 0;
                return h > prev && h <= b.max;
            }).length,
        }));

        return { mediana, pctAtendidas, pctDescartadas, hist };
    }, [todasAlertas60d, alertasPendientes]);

    // ── Salud tab: KPIs ──────────────────────────────────────────────────────
    const kpiSaludSinAtender = useMemo(() =>
        alertasFiltradas.filter(a => a.tipo === 'SALUD' && horasTranscurridas(a.tsGeneracion) > 24).length,
        [alertasFiltradas]);

    const kpiEnCalibracion = useMemo(() =>
        animalesFiltrados.filter(a => a.estadoActual !== 'INACTIVO' && a.enCalibracion).length,
        [animalesFiltrados]);

    // ── Salud tab: Alertas de salud sin atender ──────────────────────────────
    const alertasSaludSinAtender = useMemo(() =>
        alertasFiltradas
            .filter(a => a.tipo === 'SALUD' && horasTranscurridas(a.tsGeneracion) > 24)
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

        const anestroSinInterv = reproductivos.filter(a => a.estadoReproductivo === 'ANESTRO');

        const totalConEstado = reproductivos.filter(
            a => a.estadoReproductivo && a.estadoReproductivo !== 'SIN_ESTADO'
        ).length;
        const completitudPct = reproductivos.length > 0
            ? Math.round((totalConEstado / reproductivos.length) * 100)
            : 0;

        return { vaciasLargo, inseminadasSinDiag, anestroSinInterv, completitudPct, totalRepro: reproductivos.length };
    }, [animalesFiltrados]);

    // ── Salud tab: Partos esperados ──────────────────────────────────────────
    const partosEsperados = useMemo(() => {
        const DIAS_GESTACION = 283;
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

        return { semanas, sinFecha, total: gestantesConFecha.length + sinFecha };
    }, [animalesFiltrados]);


    // ── Salud tab: Desempeño reproductivo ────────────────────────────────────
    const desempenyo = useMemo(() => {
        const hace30d = 30 * 24 * 60 * 60 * 1000;
        const reproductivos = animalesFiltrados.filter(a => a.estadoActual !== 'INACTIVO' && esReproductiva(a));

        // Celo detectado: total alertas de celo en 30d
        const celoDetectado = todasAlertas60d.filter(a =>
            a.tipo === 'CELO' &&
            Date.now() - new Date(a.tsGeneracion).getTime() <= hace30d
        ).length;

        // Atendido: celo CONFIRMADA en 30d
        const celoAtendido = todasAlertas60d.filter(a =>
            a.tipo === 'CELO' && a.estado === 'CONFIRMADA' &&
            Date.now() - new Date(a.tsGeneracion).getTime() <= hace30d
        ).length;

        // Inseminadas actualmente
        const inseminadas = reproductivos.filter(a => a.estadoReproductivo === 'INSEMINADA').length;

        // Gestantes actualmente
        const gestantes = reproductivos.filter(a => a.estadoReproductivo === 'GESTANTE').length;

        return { celoDetectado, celoAtendido, inseminadas, gestantes, n: reproductivos.length };
    }, [animalesFiltrados, todasAlertas60d]);

    // ── Salud tab: Reincidencia (Idea 13) ────────────────────────────────────
    const reincidencia = useMemo(() => {
        const hace30d = 30 * 24 * 60 * 60 * 1000;
        const now = Date.now();
        const alertas30d = todasAlertas60d.filter(a =>
            now - new Date(a.tsGeneracion).getTime() <= hace30d
        );

        // Por animal: cantidad de alertas en 30d
        const porAnimal = new Map<number, number>();
        alertas30d.forEach(a => {
            porAnimal.set(a.animalId, (porAnimal.get(a.animalId) ?? 0) + 1);
        });

        const rankingAnimales = Array.from(porAnimal.entries())
            .filter(([, count]) => count >= 2)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5)
            .map(([animalId, count]) => ({ animal: animalMap.get(animalId), count }));

        // Por lote: salidas de perímetro repetidas
        const porLote = new Map<string, number>();
        alertas30d.filter(a => a.tipo === 'FUERA_DE_PERIMETRO').forEach(a => {
            const anim = animalMap.get(a.animalId);
            const lote = anim?.rodeoLoteNombre ?? 'Sin lote';
            porLote.set(lote, (porLote.get(lote) ?? 0) + 1);
        });
        const rankingLotes = Array.from(porLote.entries())
            .sort((a, b) => b[1] - a[1])
            .slice(0, 3)
            .map(([nombre, count]) => ({ nombre, count }));

        return { rankingAnimales, rankingLotes };
    }, [todasAlertas60d, animalMap]);

    // ── Salud tab: Última intervención (proxy via alertas CONFIRMADA) ─────────
    // TODO: reemplazar con endpoint GET /intervenciones?establecimientoId={id} cuando esté disponible
    const ultimaIntervencion = useMemo(() => {
        // Proxy: la fecha de la última alerta CONFIRMADA por animal como
        // aproximación de la última vez que el equipo revisó el animal.
        const ultimaPorAnimal = new Map<number, string>();
        todasAlertas60d
            .filter(a => a.estado === 'CONFIRMADA' || a.estado === 'DESCARTADA')
            .sort((a, b) => new Date(a.tsGeneracion).getTime() - new Date(b.tsGeneracion).getTime())
            .forEach(a => { ultimaPorAnimal.set(a.animalId, a.tsGeneracion); });

        return animalesFiltrados
            .filter(a => a.estadoActual !== 'INACTIVO')
            .map(a => ({
                animal: a,
                ultimaFecha: ultimaPorAnimal.get(a.id) ?? null,
                dias: ultimaPorAnimal.has(a.id) ? Math.floor(diffDias(ultimaPorAnimal.get(a.id)!)) : null,
            }))
            .sort((a, b) => {
                // Nunca revisados primero, luego más días sin revisión
                if (a.dias === null && b.dias === null) return 0;
                if (a.dias === null) return -1;
                if (b.dias === null) return 1;
                return b.dias - a.dias;
            })
            .slice(0, 5);
    }, [animalesFiltrados, todasAlertas60d]);

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
                {/* ── Header ejecutivo ─────────────────────────────────── */}
                <div className="dash-exec-header">
                    <span className="dash-exec-period">Período: 30 d vs. anterior ▼</span>
                </div>

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
                                                    {row.totalAnim > 0 && (
                                                        <span style={{ color: '#94A3B8', fontSize: '0.78rem', marginLeft: 6 }}>
                                                            ({row.totalAnim} animales)
                                                        </span>
                                                    )}
                                                </td>
                                                <td>{cobCell(row.cobPct)}</td>
                                                <td className="dash-exec-cell-normal">{row.alertasPend}</td>
                                                <td>{alertas100Cell(row.alertas100, row.alertasPend)}</td>
                                                <td className="dash-exec-cell-normal">{row.ventPerdidas}</td>
                                                <td>{vacias90Cell(row.pctVacias90)}</td>
                                                <td>
                                                    <button
                                                        className="dash-exec-ver-btn"
                                                        onClick={() => navigate('/animales')}
                                                    >
                                                        Ver
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                                <span className="dash-exec-table-note" style={{display:'none'}} />
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

                        {todasAlertas60d.length === 0 ? (
                            <p className="dash-no-items">
                                <i className="bi bi-bar-chart" style={{ color: '#94A3B8' }} />
                                Sin datos de alertas en los últimos 60 días
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
                                <span className="dash-team-kpi-val">
                                    {equipoMetrics.mediana < 1
                                        ? `${Math.round(equipoMetrics.mediana * 60)}min`
                                        : equipoMetrics.mediana < 24
                                        ? `${Math.round(equipoMetrics.mediana)}h`
                                        : `${Math.round(equipoMetrics.mediana / 24)}d`
                                    }
                                </span>
                            </div>
                            <div className="dash-team-kpi">
                                <span className="dash-team-kpi-label">Atendidas</span>
                                <span className="dash-team-kpi-val">{equipoMetrics.pctAtendidas}%</span>
                            </div>
                            <div className="dash-team-kpi">
                                <span className="dash-team-kpi-label">Descartadas</span>
                                <span className="dash-team-kpi-val">{equipoMetrics.pctDescartadas}%</span>
                            </div>
                        </div>

                        <div className="dash-team-hist-label">
                            Distribución de antigüedad de alertas pendientes
                        </div>
                        <div className="dash-team-hist">
                            {equipoMetrics.hist.map(bucket => (
                                <div key={bucket.label} className="dash-team-hist-bar-wrap">
                                    <div
                                        className="dash-team-hist-bar"
                                        style={{ height: `${Math.round((bucket.count / maxHist) * 68)}px` }}
                                        title={`${bucket.count} alertas`}
                                    />
                                    <span className="dash-team-hist-bar-label">{bucket.label}</span>
                                </div>
                            ))}
                        </div>
                        <span className="dash-team-hist-sub">
                            Distribución del tiempo en cola (h) — alertas pendientes actuales
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
        const maxDesempenyo = Math.max(desempenyo.celoDetectado, 1);
        const N_MIN = 5; // ocultar tasa cuando n < N_MIN

        return (
            <>
                {/* ── Título ──────────────────────────────────────────── */}
                <div className="dash-salud-header">
                    <div className="dash-salud-filters">
                        <span className="dash-salud-period">Período: 30 d ▾</span>
                    </div>
                </div>

                {/* ── STRIP DE 4 KPIs ────────────────────────────────────── */}
                <div className="dash-salud-kpi-row">
                    <div className="dash-salud-kpi">
                        <span className="dash-salud-kpi-label">Días abiertos (promedio)</span>
                        <span className="dash-salud-kpi-val">{embudo.diasAbiertosProm}</span>
                        <span className="dash-salud-kpi-sub">animales sin gesta confirmada</span>
                    </div>
                    <div className="dash-salud-kpi">
                        <span className="dash-salud-kpi-label">Ventanas perdidas (30 d)</span>
                        <span className="dash-salud-kpi-val dash-salud-kpi-warn">
                            {celoArchivadasRecientes > 0
                                ? `${Math.round((celoArchivadasRecientes / Math.max(celoArchivadasRecientes + ventanaItems.length, 1)) * 100)}%`
                                : '0%'
                            }
                        </span>
                        <span className="dash-salud-kpi-sub">n={celoArchivadasRecientes} oportunidades</span>
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
                                                    onClick={() => navigate('/animales')}
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
                                                    onClick={() => navigate('/animales')}
                                                >
                                                    — ver lista
                                                </button>
                                            )}
                                        </div>
                                    </div>

                                    <div className="dash-salud-exc-row">
                                        <div className="dash-salud-exc-desc">
                                            <span className="dash-salud-exc-state anestro">ANESTRO</span>
                                            {' '}sin intervención
                                        </div>
                                        <div className="dash-salud-exc-action">
                                            <strong>{embudoExcepciones.anestroSinInterv.length}</strong>
                                            {embudoExcepciones.anestroSinInterv.length > 0 && (
                                                <button
                                                    className="dash-ver-todas-btn"
                                                    onClick={() => navigate('/animales')}
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
                                alertasSaludSinAtender.slice(0, 3).map(alerta => {
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
                                            <div className="dash-salud-sparkline">
                                                {/* Placeholder de actividad (wireframe usa ~~ ) */}
                                                <svg width="40" height="18" viewBox="0 0 40 18">
                                                    <polyline
                                                        points="0,9 8,5 16,12 24,4 32,10 40,7"
                                                        fill="none" stroke="#94A3B8" strokeWidth="1.5"
                                                        strokeLinejoin="round" strokeLinecap="round"
                                                    />
                                                </svg>
                                            </div>
                                            <span className="dash-salud-revisar-tiempo">
                                                {dias > 0 ? `${dias} d` : `${Math.floor(h)}h`}
                                            </span>
                                        </div>
                                    );
                                })
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
                                animalesARevisar.slice(0, 4).map(a => {
                                    const diasObs = a.caravanaUltimaSenial
                                        ? Math.floor(diffDias(a.caravanaUltimaSenial))
                                        : null;
                                    const esEnfermo = a.estadoSalud === 'ENFERMO';
                                    const sinRevisionLargo = diasObs !== null && diasObs > 14;
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
                                            >
                                                {diasObs !== null ? `${diasObs} d sin revisión` : '—'}
                                            </span>
                                        </div>
                                    );
                                })
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
                                    Por semana, próximos 60 d
                                    {partosEsperados.sinFecha > 0 && (
                                        <span className="dash-partos-subfecha">
                                            {' '}· {partosEsperados.sinFecha} sin fecha de inseminación
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

                        {desempenyo.n < N_MIN ? (
                            <p className="dash-no-items">
                                <i className="bi bi-info-circle" style={{ color: '#94A3B8' }} />
                                {' '}Se necesitan al menos {N_MIN} animales reproductivos (n={desempenyo.n})
                            </p>
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
                                    n={desempenyo.n} · tasas ocultas cuando n &lt; {N_MIN}
                                </div>
                            </>
                        )}
                    </div>
                </div>

                {/* ── DEBAJO DEL PLIEGUE ─────────────────────────────────────── */}
                <div className="dash-salud-fold-label">
                    <span>debajo del pliegue</span>
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
                                {' '}Sin reincidentes en 30 d
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
                                        <span className="dash-salud-reincidencia-count">{item.count} alertas / 30 d</span>
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

                        <div className="dash-salud-interv-note">
                            {/* Nota: proxy via alertas atendidas hasta que exista endpoint global de intervenciones */}
                            Basado en alertas atendidas (proxy)
                        </div>

                        {ultimaIntervencion.length === 0 ? (
                            <p className="dash-no-items">Sin datos de intervenciones</p>
                        ) : (
                            ultimaIntervencion.map((item, i) => (
                                <div
                                    key={i}
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
                            ))
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
                        <>
                            <h2>Requiere atención hoy</h2>
                            <div className="dash-header-meta">
                                <span className="dash-live-dot" />
                                EN VIVO
                            </div>
                        </>
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
                        Cobertura de collares
                    </div>
                    {loading ? <Spinner animation="border" size="sm" /> : (
                        <div className="dash-kpi-chip-val">{kpiCobertura}%</div>
                    )}
                    <div className="dash-kpi-chip-sub">
                        {cobertura.silencio > 0 ? `${cobertura.silencio} sin señal · ` : ''}
                        {cobertura.bateriaBaja > 0 ? `${cobertura.bateriaBaja} batería baja` : 'collares ok'}
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

                                    const isUrgentAtender = (isCelo && horasRestantes !== null && horasRestantes <= 3) ||
                                        (!isCelo && horas >= 24);

                                    const sevDotClass = alerta.severidad === 'ALTA' ? 'dash-sev-dot-alta'
                                        : alerta.severidad === 'MEDIA' ? 'dash-sev-dot-media'
                                            : alerta.severidad === 'BAJA' ? 'dash-sev-dot-baja'
                                                : 'dash-sev-dot-none';

                                    const tipoClass = alerta.tipo === 'CELO' ? 'dash-tipo-celo'
                                        : alerta.tipo === 'SALUD' ? 'dash-tipo-salud'
                                            : alerta.tipo === 'FUERA_DE_PERIMETRO' ? 'dash-tipo-perimetro'
                                                : 'dash-tipo-collar';

                                    const tipoLabel = alerta.tipo === 'CELO' ? 'CELO'
                                        : alerta.tipo === 'SALUD' ? 'SALUD'
                                            : alerta.tipo === 'FUERA_DE_PERIMETRO' ? 'PERÍMETRO'
                                                : 'COLLAR';

                                    return (
                                        <div key={`${alerta.id}-${i}`}>
                                            {i > 0 && <div className="dash-divider" />}
                                            <div
                                                className="dash-cola-row"
                                                onClick={() => animal && navigate(`/animales/${animal.id}`)}
                                            >
                                                <span className={`dash-sev-dot ${sevDotClass}`} />
                                                <div className="dash-cola-nombre">{nombreAnimal(animal)}</div>
                                                <div className="dash-cola-rec-bar" />
                                                <span className={`dash-tipo-chip ${tipoClass}`}>{tipoLabel}</span>
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

                    {/* ── Idea 4: Cobertura de collares ─────────────────── */}
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
                                    {cobertura.sinCollar > 0 && (
                                        <div className="dash-coverage-seg dash-coverage-sincollar"
                                            style={{ flex: cobertura.sinCollar }} title={`Sin collar: ${cobertura.sinCollar}`} />
                                    )}
                                </div>

                                <div className="dash-coverage-legend">
                                    {[
                                        { label: 'Monitoreados', count: cobertura.ok, cls: 'dash-coverage-ok' },
                                        { label: 'En calibración', count: cobertura.calibrando, cls: 'dash-coverage-calibrando' },
                                        { label: 'Sin señal > 6h', count: cobertura.silencio, cls: 'dash-coverage-silencio' },
                                        { label: `Batería ≤ ${BATERIA_BAJA_PCT}%`, count: cobertura.bateriaBaja, cls: 'dash-coverage-bateria' },
                                        { label: 'Sin collar', count: cobertura.sinCollar, cls: 'dash-coverage-sincollar' },
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

                {/* ── Idea 8: Tendencia 8 semanas (Placeholder) ───────────── */}
                <div className="dash-card">
                    <div className="dash-card-header">
                        <p className="dash-card-title">
                            <i className="bi bi-graph-up" />
                            Tendencia 8 semanas
                        </p>
                    </div>
                    <div className="dash-salud-peso-empty" style={{ padding: '40px 16px' }}>
                        <i className="bi bi-bar-chart-steps" style={{ fontSize: '1.6rem', color: '#CBD5E1' }} />
                        <span className="dash-salud-peso-empty-text">
                            Disponible próximamente
                        </span>
                        <span className="dash-salud-peso-empty-sub">
                            Módulo de tendencias en desarrollo
                        </span>
                    </div>
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
