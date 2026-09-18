import { useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import { Spinner, Alert, Button } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { getAnimales, ESTADO_REPRODUCTIVO_LABEL } from '../api/animalesApi';
import type { AnimalResponse } from '../api/animalesApi';
import { getAlertas, getResumenAlertas } from '../api/alertaApi';
import type { AlertaResponse } from '../api/alertaApi';
import { getRodeoLotes } from '../api/rodeoLotesApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import './AnimalesPage.css';
import './DashboardPage.css';

// ── Helpers ──────────────────────────────────────────────────────────────────

function dentroDeDias(tsGeneracion: string, dias: number): boolean {
    const fecha = new Date(tsGeneracion);
    const ahora = new Date();
    const diffMs = ahora.getTime() - fecha.getTime();
    return diffMs >= 0 && diffMs <= dias * 24 * 60 * 60 * 1000;
}

function esReproductiva(fechaNacimiento: string): boolean {
    const nacimiento = new Date(fechaNacimiento);
    const ahora = new Date();
    const diffAnios = (ahora.getTime() - nacimiento.getTime()) / (1000 * 60 * 60 * 24 * 365.25);
    return diffAnios >= 1;
}

function tiempoTranscurrido(ts: string): string {
    const diff = Math.floor((Date.now() - new Date(ts).getTime()) / 1000);
    if (diff < 60) return `hace ${diff}s`;
    if (diff < 3600) return `hace ${Math.floor(diff / 60)}min`;
    if (diff < 86400) return `hace ${Math.floor(diff / 3600)}h`;
    return `hace ${Math.floor(diff / 86400)}d`;
}

function nombreAnimal(a: AnimalResponse): string {
    return a.alias || a.idCaravanaOficial || `#${a.id}`;
}

// ── Types ─────────────────────────────────────────────────────────────────────

interface AlertaConNombre extends AlertaResponse {
    animalNombre: string;
}

interface RodeoSummary {
    rodeo: RodeoLoteResponse;
    total: number;
    enCelo: number;
    conAlerta: number;
}

// ── Sub-components ────────────────────────────────────────────────────────────

function SectionTitle({ icon, children }: { icon: string; children: ReactNode }) {
    return (
        <p className="dash-section-title">
            <i className={`bi ${icon} me-2`} />
            {children}
        </p>
    );
}

function KpiChip({
    value, label, icon, color, loading,
}: {
    value: number; label: string; icon: string; color: string; loading: boolean;
}) {
    return (
        <div className="dash-kpi-chip" style={{ borderLeftColor: color }}>
            <div className="dash-kpi-chip-icon" style={{ color }}>
                <i className={`bi ${icon}`} />
            </div>
            <div className="dash-kpi-chip-body">
                {loading
                    ? <Spinner animation="border" size="sm" style={{ color, width: 20, height: 20 }} />
                    : <span className="dash-kpi-chip-val" style={{ color }}>{value}</span>
                }
                <span className="dash-kpi-chip-lbl">{label}</span>
            </div>
        </div>
    );
}

// ── Main Component ────────────────────────────────────────────────────────────

export default function DashboardPage() {
    const selectedId = useEstablecimientoStore((s) => s.selectedId);
    const navigate = useNavigate();

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

    // KPI
    const [totalActivos, setTotalActivos] = useState(0);
    const [totalSinCaravana, setTotalSinCaravana] = useState(0);
    const [alertasCelo, setAlertasCelo] = useState(0);
    const [alertasSalud, setAlertasSalud] = useState(0);
    const [alertasAbigeato, setAlertasAbigeato] = useState(0);

    // Reproductivo
    const [celoAhoraAnimales, setCeloAhoraAnimales] = useState<AnimalResponse[]>([]);
    const [celo7, setCelo7] = useState(0);
    const [celo30, setCelo30] = useState(0);
    const [proximosCelo, setProximosCelo] = useState(0);
    const [distReproductiva, setDistReproductiva] = useState<[string, number][]>([]);

    // Salud
    const [distSalud, setDistSalud] = useState({ sano: 0, observacion: 0, enfermo: 0 });
    const [animalesRiesgo, setAnimalesRiesgo] = useState<AnimalResponse[]>([]);

    // Alertas recientes
    const [alertasRecientes, setAlertasRecientes] = useState<AlertaConNombre[]>([]);

    // Rodeos
    const [rodeosSummary, setRodeosSummary] = useState<RodeoSummary[]>([]);

    const fetchDashboard = useCallback(async () => {
        if (!selectedId) return;
        setLoading(true);
        setError(null);
        try {
            const [animales, alertasCeloData, resumen, todasAlertas, rodeos] = await Promise.all([
                getAnimales(selectedId),
                getAlertas(selectedId, 'CELO'),
                getResumenAlertas(selectedId),
                getAlertas(selectedId),
                getRodeoLotes(selectedId),
            ]);

            // Mapa de animales para cross-reference
            const animalMap = new Map<number, AnimalResponse>(animales.map(a => [a.id, a]));

            // Activos
            const activos = animales.filter(a => a.estadoActual !== 'INACTIVO');
            setTotalActivos(activos.length);
            setTotalSinCaravana(activos.filter(a => !a.caravanaId).length);

            // Celo activo (< 24h, PENDIENTE)
            const celoActivo = alertasCeloData.filter(
                a => a.estado === 'PENDIENTE' && dentroDeDias(a.tsGeneracion, 1)
            );
            const celoActivoIds = new Set(celoActivo.map(a => a.animalId));
            const celoAhoraArr = Array.from(celoActivoIds)
                .map(id => animalMap.get(id))
                .filter((a): a is AnimalResponse => !!a)
                .slice(0, 5);
            setCeloAhoraAnimales(celoAhoraArr);

            setCelo7(new Set(
                alertasCeloData.filter(a => dentroDeDias(a.tsGeneracion, 7)).map(a => a.animalId)
            ).size);
            setCelo30(new Set(
                alertasCeloData.filter(a => dentroDeDias(a.tsGeneracion, 30)).map(a => a.animalId)
            ).size);

            const proximosSet = new Set<number>();
            alertasCeloData.forEach(a => {
                const diffDias = (Date.now() - new Date(a.tsGeneracion).getTime()) / (1000 * 60 * 60 * 24);
                if (diffDias >= 1 && diffDias <= 5 && !celoActivoIds.has(a.animalId)) {
                    proximosSet.add(a.animalId);
                }
            });
            setProximosCelo(proximosSet.size);

            // Distribución reproductiva (solo animales reproductivos con estado registrado)
            const distMap: Record<string, number> = {};
            activos.filter(a => esReproductiva(a.fechaNacimiento)).forEach(a => {
                const estado = a.estadoReproductivo;
                if (estado) distMap[estado] = (distMap[estado] || 0) + 1;
            });
            setDistReproductiva(Object.entries(distMap).sort((a, b) => b[1] - a[1]));

            // Resumen alertas
            setAlertasCelo(resumen.reproductivo ?? 0);
            setAlertasSalud(resumen.sanitario ?? 0);
            setAlertasAbigeato(resumen.geoespacial ?? 0);

            // Salud
            const sano = activos.filter(a => !a.estadoSalud || a.estadoSalud === 'SANO').length;
            const observacion = activos.filter(a => a.estadoSalud === 'EN_OBSERVACION').length;
            const enfermo = activos.filter(a => a.estadoSalud === 'ENFERMO').length;
            setDistSalud({ sano, observacion, enfermo });
            // Enfermos primero, luego observación
            setAnimalesRiesgo(
                activos
                    .filter(a => a.estadoSalud === 'ENFERMO' || a.estadoSalud === 'EN_OBSERVACION')
                    .sort((a, b) => {
                        if (a.estadoSalud === 'ENFERMO' && b.estadoSalud !== 'ENFERMO') return -1;
                        if (b.estadoSalud === 'ENFERMO' && a.estadoSalud !== 'ENFERMO') return 1;
                        return 0;
                    })
                    .slice(0, 5)
            );

            // Alertas recientes (PENDIENTE, más recientes primero, máx 5)
            const recientes: AlertaConNombre[] = todasAlertas
                .filter(a => a.estado === 'PENDIENTE')
                .sort((a, b) => new Date(b.tsGeneracion).getTime() - new Date(a.tsGeneracion).getTime())
                .slice(0, 5)
                .map(a => {
                    const anim = animalMap.get(a.animalId);
                    return { ...a, animalNombre: anim ? nombreAnimal(anim) : `Animal #${a.animalId}` };
                });
            setAlertasRecientes(recientes);

            // Rodeos (solo con animales activos)
            const rodeosConAnimales: RodeoSummary[] = rodeos
                .filter(r => r.activo)
                .map(r => {
                    const rAnimales = activos.filter(a => a.rodeoLoteId === r.id);
                    return {
                        rodeo: r,
                        total: rAnimales.length,
                        enCelo: rAnimales.filter(a => celoActivoIds.has(a.id)).length,
                        conAlerta: rAnimales.filter(a => a.estadoActual === 'CON_ALERTA').length,
                    };
                })
                .filter(r => r.total > 0);
            setRodeosSummary(rodeosConAnimales);

            setLastUpdated(new Date());
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } } };
            setError(e.response?.data?.message || 'Error al cargar el dashboard.');
        } finally {
            setLoading(false);
        }
    }, [selectedId]);

    useEffect(() => {
        fetchDashboard();
    }, [fetchDashboard]);

    const formatTs = () =>
        lastUpdated ? lastUpdated.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';

    const totalAlertas = alertasCelo + alertasSalud + alertasAbigeato;
    const totalConProblema = distSalud.observacion + distSalud.enfermo;
    const totalRepro = distReproductiva.reduce((s, [, v]) => s + v, 0);

    // ── Sin establecimiento seleccionado ────────────────────────────────────
    if (!selectedId) {
        return (
            <div className="dashboard-page">
                <div className="dash-empty">
                    <i className="bi bi-info-circle me-2" />
                    Seleccioná un establecimiento en la barra de navegación para ver el dashboard.
                </div>
            </div>
        );
    }

    // ── Render ───────────────────────────────────────────────────────────────
    return (
        <div className="dashboard-page">

            {/* ── Header ──────────────────────────────────────────────── */}
            <div className="dash-header">
                <div>
                    <h2 className="vigia-page-title">Dashboard</h2>
                    {lastUpdated && (
                        <span className="dashboard-refresh-badge" style={{ marginTop: 6, display: 'inline-flex' }}>
                            <i className="bi bi-clock" />
                            Actualizado {formatTs()}
                        </span>
                    )}
                </div>
                <Button className="btn-vigia-success" onClick={fetchDashboard} disabled={loading}>
                    {loading
                        ? <Spinner animation="border" size="sm" />
                        : <><i className="bi bi-arrow-clockwise me-1" />Refrescar</>
                    }
                </Button>
            </div>

            {error && (
                <Alert variant="danger" style={{ borderRadius: 10, maxWidth: 1200, margin: '0 auto 16px' }}>
                    <i className="bi bi-exclamation-triangle me-2" />{error}
                </Alert>
            )}

            {/* ── KPI Chips ────────────────────────────────────────────── */}
            <div className="dash-kpi-row">
                {/* Verde brand */}
                <KpiChip
                    value={totalActivos}
                    label="Animales activos"
                    icon="bi-list-check"
                    color="#8CC63F"
                    loading={loading}
                />
                {/* Rojo ALTA — siempre llamativo, no se apaga en 0 */}
                <KpiChip
                    value={totalAlertas}
                    label="Alertas activas"
                    icon="bi-bell-fill"
                    color="#E53E3E"
                    loading={loading}
                />
                {/* Cyan accent del sistema */}
                <KpiChip
                    value={celoAhoraAnimales.length}
                    label="En celo ahora"
                    icon="bi-heart-pulse-fill"
                    color="#13D4FB"
                    loading={loading}
                />
                {/* Ámbar vibrante — más luminoso que el MEDIA del sistema */}
                <KpiChip
                    value={totalConProblema}
                    label="Problemas de salud"
                    icon="bi-bandaid-fill"
                    color="#ECC94B"
                    loading={loading}
                />
                {/* Teal apagado — sin caravana es info, no alerta */}
                <KpiChip
                    value={totalSinCaravana}
                    label="Sin caravana"
                    icon="bi-tag-fill"
                    color="rgba(255,255,255,0.4)"
                    loading={loading}
                />
            </div>

            {/* ── Paneles Reproductivo + Salud ──────────────────────────── */}
            <div className="dash-panels-row">

                {/* Reproductivo */}
                <div className="dash-card">
                    <SectionTitle icon="bi-heart-pulse">Estado Reproductivo</SectionTitle>

                    {loading ? (
                        <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                    ) : distReproductiva.length > 0 ? (
                        <div className="dash-repro-dist">
                            {distReproductiva.map(([estado, count]) => (
                                <div key={estado} className="dash-dist-row">
                                    <span className="dash-dist-label">
                                        {ESTADO_REPRODUCTIVO_LABEL[estado] ?? estado}
                                    </span>
                                    <div className="dash-dist-bar-track">
                                        <div
                                            className={`dash-dist-bar dash-repro-${estado.toLowerCase()}`}
                                            style={{
                                                width: `${totalRepro > 0 ? Math.round((count / totalRepro) * 100) : 0}%`,
                                            }}
                                        />
                                    </div>
                                    <span className="dash-dist-count">{count}</span>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <p className="dash-no-items">Sin estados reproductivos registrados</p>
                    )}

                    {/* Mini KPIs de celo */}
                    <div className="dash-celo-kpis">
                        <div className="dash-celo-kpi">
                            <span className="dash-celo-kpi-val">{loading ? '—' : celo7}</span>
                            <span className="dash-celo-kpi-lbl">Últ. 7 días</span>
                        </div>
                        <div className="dash-celo-kpi">
                            <span className="dash-celo-kpi-val">{loading ? '—' : celo30}</span>
                            <span className="dash-celo-kpi-lbl">Últ. 30 días</span>
                        </div>
                        <div className="dash-celo-kpi">
                            <span className="dash-celo-kpi-val">{loading ? '—' : proximosCelo}</span>
                            <span className="dash-celo-kpi-lbl">Próx. 2–5d</span>
                        </div>
                    </div>

                    {/* Animales en celo ahora */}
                    {!loading && (
                        <div className="dash-animal-list">
                            <p className="dash-animal-list-title">
                                <span className="dash-dot" style={{ background: '#F472B6' }} />
                                En celo ahora
                                {celoAhoraAnimales.length > 0 ? ` (${celoAhoraAnimales.length})` : ''}
                            </p>
                            {celoAhoraAnimales.length > 0 ? (
                                celoAhoraAnimales.map(a => (
                                    <div key={a.id} className="dash-animal-row" onClick={() => navigate(`/animales/${a.id}`)}>
                                        <i className="bi bi-arrow-right-circle-fill" style={{ color: '#F472B6', flexShrink: 0 }} />
                                        <span className="dash-animal-nombre">{nombreAnimal(a)}</span>
                                        {a.rodeoLoteNombre && (
                                            <span className="dash-animal-rodeo">{a.rodeoLoteNombre}</span>
                                        )}
                                    </div>
                                ))
                            ) : (
                                <span className="dash-ok-text">
                                    <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                    Sin alertas de celo activas
                                </span>
                            )}
                        </div>
                    )}
                </div>

                {/* Salud */}
                <div className="dash-card">
                    <SectionTitle icon="bi-bandaid">Estado de Salud</SectionTitle>

                    {/* Barra segmentada */}
                    <div className="dash-salud-bar-wrap">
                        {loading ? (
                            <div className="dash-salud-seg" style={{ flex: 1, background: '#E2E8F0' }} />
                        ) : totalActivos > 0 ? (
                            <>
                                {distSalud.sano > 0 && (
                                    <div
                                        className="dash-salud-seg dash-salud-sano"
                                        style={{ flex: distSalud.sano }}
                                        title={`Sanos: ${distSalud.sano}`}
                                    />
                                )}
                                {distSalud.observacion > 0 && (
                                    <div
                                        className="dash-salud-seg dash-salud-obs"
                                        style={{ flex: distSalud.observacion }}
                                        title={`En observación: ${distSalud.observacion}`}
                                    />
                                )}
                                {distSalud.enfermo > 0 && (
                                    <div
                                        className="dash-salud-seg dash-salud-enfermo"
                                        style={{ flex: distSalud.enfermo }}
                                        title={`Enfermos: ${distSalud.enfermo}`}
                                    />
                                )}
                            </>
                        ) : (
                            <div className="dash-salud-seg" style={{ flex: 1, background: '#E2E8F0' }} />
                        )}
                    </div>

                    {/* Leyenda */}
                    <div className="dash-salud-legend">
                        <span className="dash-leg-item">
                            <span className="dash-dot dash-dot-sano" />
                            Sanos <strong>{loading ? '—' : distSalud.sano}</strong>
                        </span>
                        <span className="dash-leg-item">
                            <span className="dash-dot dash-dot-obs" />
                            Observación <strong>{loading ? '—' : distSalud.observacion}</strong>
                        </span>
                        <span className="dash-leg-item">
                            <span className="dash-dot dash-dot-enfermo" />
                            Enfermos <strong>{loading ? '—' : distSalud.enfermo}</strong>
                        </span>
                    </div>

                    {/* Animales que requieren atención */}
                    {!loading && (
                        <div className="dash-animal-list">
                            <p className="dash-animal-list-title">
                                <span className="dash-dot" style={{ background: '#FBBF24' }} />
                                Requieren atención
                                {animalesRiesgo.length > 0 ? ` (${animalesRiesgo.length})` : ''}
                            </p>
                            {animalesRiesgo.length > 0 ? (
                                animalesRiesgo.map(a => (
                                    <div key={a.id} className="dash-animal-row" onClick={() => navigate(`/animales/${a.id}`)}>
                                        <i
                                            className="bi bi-arrow-right-circle-fill"
                                            style={{
                                                color: a.estadoSalud === 'ENFERMO' ? '#E53E3E' : '#D69E2E',
                                                flexShrink: 0,
                                            }}
                                        />
                                        <span className="dash-animal-nombre">{nombreAnimal(a)}</span>
                                        <span className={`dash-salud-badge dash-salud-badge-${a.estadoSalud === 'ENFERMO' ? 'enfermo' : 'obs'}`}>
                                            {a.estadoSalud === 'ENFERMO' ? 'Enfermo' : 'Observación'}
                                        </span>
                                    </div>
                                ))
                            ) : (
                                <span className="dash-ok-text">
                                    <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                                    Todos los animales sanos
                                </span>
                            )}
                        </div>
                    )}
                </div>
            </div>

            {/* ── Alertas Recientes ────────────────────────────────────── */}
            <div className="dash-card dash-card-full">
                <div className="dash-card-header-row">
                    <SectionTitle icon="bi-bell">Alertas Recientes</SectionTitle>
                    <button className="dash-ver-todas-btn" onClick={() => navigate('/alertas')}>
                        Ver todas <i className="bi bi-arrow-right ms-1" />
                    </button>
                </div>

                {loading ? (
                    <div className="dash-loading-center"><Spinner animation="border" size="sm" /></div>
                ) : alertasRecientes.length === 0 ? (
                    <p className="dash-no-items">
                        <i className="bi bi-check-circle-fill" style={{ color: '#38A169' }} />
                        Sin alertas pendientes
                    </p>
                ) : (
                    <div className="dash-alertas-list">
                        {alertasRecientes.map(a => (
                            <div key={a.id} className="dash-alerta-row">
                                <span className={`dash-tipo-chip dash-tipo-${a.tipo.toLowerCase()}`}>
                                    {a.tipo === 'CELO' ? 'Reproductiva'
                                        : a.tipo === 'SALUD' ? 'Salud'
                                            : 'Posición'}
                                </span>
                                <span
                                    className="dash-alerta-animal"
                                    onClick={() => navigate(`/animales/${a.animalId}`)}
                                >
                                    <i className="bi bi-arrow-right-circle" />
                                    {a.animalNombre}
                                </span>
                                {a.severidad && (
                                    <span className={`dash-sev-chip dash-sev-${a.severidad.toLowerCase()}`}>
                                        {a.severidad}
                                    </span>
                                )}
                                <span className="dash-alerta-tiempo">{tiempoTranscurrido(a.tsGeneracion)}</span>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* ── Rodeos / Lotes ───────────────────────────────────────── */}
            {!loading && rodeosSummary.length > 0 && (
                <div className="dash-card dash-card-full">
                    <SectionTitle icon="bi-grid-3x3-gap">Rodeos / Lotes</SectionTitle>
                    <div className="dash-rodeos-grid">
                        {rodeosSummary.map(r => (
                            <div key={r.rodeo.id} className="dash-rodeo-card">
                                <p className="dash-rodeo-name">{r.rodeo.nombre}</p>
                                <div className="dash-rodeo-kpis">
                                    <div className="dash-rodeo-kpi">
                                        <span className="dash-rodeo-val">{r.total}</span>
                                        <span className="dash-rodeo-lbl">Animales</span>
                                    </div>
                                    <div className="dash-rodeo-kpi">
                                        <span
                                            className="dash-rodeo-val"
                                            style={{ color: r.enCelo > 0 ? '#F472B6' : '#94A3B8' }}
                                        >
                                            {r.enCelo}
                                        </span>
                                        <span className="dash-rodeo-lbl">En celo</span>
                                    </div>
                                    <div className="dash-rodeo-kpi">
                                        <span
                                            className="dash-rodeo-val"
                                            style={{ color: r.conAlerta > 0 ? '#EF4444' : '#94A3B8' }}
                                        >
                                            {r.conAlerta}
                                        </span>
                                        <span className="dash-rodeo-lbl">Con alerta</span>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

        </div>
    );
}
