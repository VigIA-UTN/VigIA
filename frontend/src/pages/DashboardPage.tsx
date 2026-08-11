import { useState, useEffect, useCallback } from 'react';
import { Spinner, Alert, Button } from 'react-bootstrap';
import { getAnimales } from '../api/animalesApi';
import { getAlertas, getResumenAlertas } from '../api/alertaApi';
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

// ── Sub-component: valor con spinner ─────────────────────────────────────────

function PbiVal({ value, loading, size = 'md' }: { value: number; loading: boolean; size?: 'lg' | 'md' }) {
    const fontSize = size === 'lg' ? '4rem' : '2.6rem';
    if (loading) {
        return (
            <div className="pbi-spinner-val">
                <Spinner animation="border" size="sm" style={{ color: 'rgba(255,255,255,0.45)', width: '22px', height: '22px' }} />
            </div>
        );
    }
    return <span className="pbi-val" style={{ fontSize }}>{value}</span>;
}

// ── Main Component ────────────────────────────────────────────────────────────

export default function DashboardPage() {
    const selectedId = useEstablecimientoStore((s) => s.selectedId);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

    const [celoAhora, setCeloAhora] = useState(0);
    const [celo7, setCelo7] = useState(0);
    const [celo30, setCelo30] = useState(0);
    const [proximosCelo, setProximosCelo] = useState(0);
    const [totalMonitoreados, setTotalMonitoreados] = useState(0);
    const [alertasCelo, setAlertasCelo] = useState(0);
    const [alertasSalud, setAlertasSalud] = useState(0);
    const [alertasAbigeato, setAlertasAbigeato] = useState(0);

    const fetchDashboard = useCallback(async () => {
        if (!selectedId) return;
        setLoading(true);
        setError(null);
        try {
            const [animales, alertasCeloData, resumen] = await Promise.all([
                getAnimales(selectedId),
                getAlertas(selectedId, 'CELO', undefined, undefined),
                getResumenAlertas(selectedId),
            ]);

            const monitoreados = animales.filter(
                (a) => a.estadoActual !== 'INACTIVO' && esReproductiva(a.fechaNacimiento)
            );
            setTotalMonitoreados(monitoreados.length);

            const celoActivo = alertasCeloData.filter(
                (a) => a.estado === 'PENDIENTE' && dentroDeDias(a.tsGeneracion, 1)
            );
            const celoActivoIds = new Set(celoActivo.map((a) => a.animalId));
            setCeloAhora(celoActivoIds.size);

            setCelo7(new Set(alertasCeloData.filter((a) => dentroDeDias(a.tsGeneracion, 7)).map((a) => a.animalId)).size);
            setCelo30(new Set(alertasCeloData.filter((a) => dentroDeDias(a.tsGeneracion, 30)).map((a) => a.animalId)).size);

            const proximosSet = new Set<number>();
            alertasCeloData.forEach((a) => {
                const diffDias = (new Date().getTime() - new Date(a.tsGeneracion).getTime()) / (1000 * 60 * 60 * 24);
                if (diffDias >= 1 && diffDias <= 5 && !celoActivoIds.has(a.animalId)) {
                    proximosSet.add(a.animalId);
                }
            });
            setProximosCelo(proximosSet.size);

            setAlertasCelo(resumen.reproductivo ?? 0);
            setAlertasSalud(resumen.sanitario ?? 0);
            setAlertasAbigeato(resumen.geoespacial ?? 0);

            setLastUpdated(new Date());
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } } };
            setError(e.response?.data?.message || 'Error al cargar el dashboard.');
        } finally {
            setLoading(false);
        }
    }, [selectedId]);

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        fetchDashboard();
    }, [fetchDashboard]);

    const formatTs = () =>
        lastUpdated ? lastUpdated.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';

    // Sin establecimiento seleccionado
    if (!selectedId) {
        return (
            <div className="dashboard-pbi-page">
                <div className="pbi-empty">
                    <i className="bi bi-info-circle me-2" />
                    Seleccioná un establecimiento en la barra de navegación para ver el dashboard.
                </div>
            </div>
        );
    }

    const totalAlertas = alertasCelo + alertasSalud + alertasAbigeato;

    return (
        <div className="dashboard-pbi-page">

            {/* ── Header (sobre fondo claro) ── */}
            <div className="vigia-page-header" style={{ maxWidth: 1100, margin: '0 auto 20px' }}>
                <div>
                    <h2 className="vigia-page-title">Dashboard</h2>
                    {lastUpdated && (
                        <span className="dashboard-refresh-badge" style={{ marginTop: '6px', display: 'inline-flex' }}>
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
                <div style={{ maxWidth: 860, margin: '0 auto 16px' }}>
                    <Alert variant="danger" style={{ borderRadius: 10 }}>
                        <i className="bi bi-exclamation-triangle me-2" />{error}
                    </Alert>
                </div>
            )}

            {/* ── Widget navy contenido ── */}
            <div className="pbi-widget">

                {/* Fila 1: Animales Activos + Alertas Activas */}
                <div className="pbi-main-row">

                    {/* Hero */}
                    <div className="pbi-kpi-hero">
                        <PbiVal value={totalMonitoreados} loading={loading} size="lg" />
                        <p className="pbi-lbl">Animales Activos</p>
                    </div>

                    <div className="pbi-divider" />

                    {/* Alertas por tipo */}
                    <div className="pbi-alertas-group">
                        <p className="pbi-alertas-group-title">Alertas Activas</p>
                        <div className="pbi-alertas-row">
                            <div className="pbi-alerta-card">
                                <PbiVal value={alertasCelo} loading={loading} />
                                <p className="pbi-lbl"><span className="pbi-dot pbi-dot-repro" />Reproductivas</p>
                            </div>
                            <div className="pbi-alerta-card">
                                <PbiVal value={alertasSalud} loading={loading} />
                                <p className="pbi-lbl"><span className="pbi-dot pbi-dot-salud" />Salud</p>
                            </div>
                            <div className="pbi-alerta-card">
                                <PbiVal value={alertasAbigeato} loading={loading} />
                                <p className="pbi-lbl"><span className="pbi-dot pbi-dot-abigeato" />Posición</p>
                            </div>
                            <div className="pbi-alerta-card">
                                <PbiVal value={totalAlertas} loading={loading} />
                                <p className="pbi-lbl"><span className="pbi-dot pbi-dot-total" />Total</p>
                            </div>
                        </div>
                    </div>

                </div>

                {/* Separador */}
                <div className="pbi-inner-sep" />

                {/* Fila 2: KPIs de Celo */}
                <p className="pbi-panel-title">
                    <i className="bi bi-heart-pulse me-2" />Estado Reproductivo — Celo
                </p>
                <div className="pbi-celo-row">
                    <div className="pbi-kpi-card">
                        <PbiVal value={celoAhora} loading={loading} />
                        <p className="pbi-lbl">En celo ahora</p>
                        <p className="pbi-sub" style={{ color: '#A8C8E8', textTransform: 'uppercase', fontWeight: 600 }}>ALERTAS ACTIVAS &lt; 24 H</p>
                    </div>
                    <div className="pbi-kpi-card">
                        <PbiVal value={celo7} loading={loading} />
                        <p className="pbi-lbl">Últimos 7 días</p>
                        <p className="pbi-sub" style={{ color: '#A8C8E8', textTransform: 'uppercase', fontWeight: 600 }}>ANIMALES EN CELO</p>
                    </div>
                    <div className="pbi-kpi-card">
                        <PbiVal value={celo30} loading={loading} />
                        <p className="pbi-lbl">Últimos 30 días</p>
                        <p className="pbi-sub" style={{ color: '#A8C8E8', textTransform: 'uppercase', fontWeight: 600 }}>ANIMALES EN CELO</p>
                    </div>
                    <div className="pbi-kpi-card">
                        <PbiVal value={proximosCelo} loading={loading} />
                        <p className="pbi-lbl">Próximos a celo</p>
                        <p className="pbi-sub" style={{ color: '#A8C8E8', textTransform: 'uppercase', fontWeight: 600 }}>ESTIMADO 2–5 DÍAS</p>
                    </div>
                </div>

            </div>
        </div>
    );
}
