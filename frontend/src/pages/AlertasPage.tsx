import { useState, useEffect, useRef, useCallback } from 'react';
import { Container, Spinner, Alert, Form, Button } from 'react-bootstrap';
import { getAlertas, esAnestro, coincideFiltroTipo, type AlertaResponse } from '../api/alertaApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { useAuthStore } from '../store/authStore';
import AlertDetailSidebar from '../components/AlertDetailSidebar';
import DatoDesactualizadoBadge from '../components/DatoDesactualizadoBadge';
import IntervencionFormPanel from '../components/IntervencionFormPanel';
import './AnimalesPage.css'; // For common vigia styles
import './AlertasPage.css';

function formatFecha(iso: string | null) {
    if (!iso) return '—';
    const date = new Date(iso);
    return date.toLocaleString([], {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    });
}

function getSeveridadClass(severidad?: string) {
    if (severidad === 'ALTA') return 'severidad-alta';
    if (severidad === 'MEDIA') return 'severidad-media';
    return 'severidad-baja';
}

function getEstadoClass(estado: string) {
    if (estado === 'CONFIRMADA') return 'estado-confirmada';
    if (estado === 'DESCARTADA') return 'estado-descartada';
    return 'estado-pendiente';
}

export default function AlertasPage() {
    const selectedId = useEstablecimientoStore((s) => s.selectedId);
    const token = useAuthStore((s) => s.token);

    const [alertas, setAlertas] = useState<AlertaResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // Filters
    const [filtroEstado, setFiltroEstado] = useState<string>('PENDIENTE');
    const [filtroTipo, setFiltroTipo] = useState<string>('');
    const [filtroSeveridad, setFiltroSeveridad] = useState<string>('');

    // Table Sorting (client side based on fetched data, backend already returns sorted by severidad DESC)
    const [sortField, setSortField] = useState<string | null>(null);
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

    // Sidebar overlay
    const [selectedAlerta, setSelectedAlerta] = useState<AlertaResponse | null>(null);
    const [isIntervencionOpen, setIsIntervencionOpen] = useState(false);
    const [intervencionAlerta, setIntervencionAlerta] = useState<AlertaResponse | null>(null);

    const maxAlertaIdRef = useRef<number>(0);

    const fetchAlertas = useCallback(async () => {
        if (!selectedId) return;
        setLoading(true);
        setError(null);
        try {
            const data = await getAlertas(
                selectedId,
                filtroTipo || undefined,
                filtroSeveridad || undefined,
                filtroEstado || undefined
            );
            setAlertas(data);
            if (data.length > 0) {
                // Update max ID for SSE
                const currentMax = Math.max(...data.map((a) => a.id));
                if (currentMax > maxAlertaIdRef.current) {
                    maxAlertaIdRef.current = currentMax;
                }
            }
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } } };
            setError(e.response?.data?.message || 'Error al cargar las alertas.');
        } finally {
            setLoading(false);
        }
    }, [selectedId, filtroTipo, filtroSeveridad, filtroEstado]);

    // Refetch when filters change
    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        fetchAlertas();
    }, [fetchAlertas]);

    // Setup SSE connection for real-time updates
    useEffect(() => {
        if (!selectedId) return;
        let isMounted = true;

        const API_URL = import.meta.env.VITE_API_URL as string;
        let sseUrl = `${API_URL}/alertas/stream?establecimientoId=${selectedId}`;
        if (token) {
            sseUrl += `&token=${token}`;
        }
        if (maxAlertaIdRef.current > 0) {
            sseUrl += `&lastEventId=${maxAlertaIdRef.current}`;
        }

        const eventSource = new EventSource(sseUrl);

        eventSource.onmessage = (event) => {
            if (!isMounted) return;
            const newAlerta = JSON.parse(event.data) as AlertaResponse;

            // Update max ID
            if (newAlerta.id > maxAlertaIdRef.current) {
                maxAlertaIdRef.current = newAlerta.id;
            }

            // Actualizar si ya existe, o agregar si es nueva y cumple filtros
            setAlertas((prev) => {
                if (prev.some((a) => a.id === newAlerta.id)) {
                    return prev.map(a => a.id === newAlerta.id ? newAlerta : a);
                }
                // Si estamos filtrando y la alerta nueva no matchea, la ignoramos
                if (filtroEstado && newAlerta.estado !== filtroEstado) return prev;
                if (!coincideFiltroTipo(newAlerta, filtroTipo)) return prev;
                if (filtroSeveridad && newAlerta.severidad !== filtroSeveridad) return prev;
                
                return [newAlerta, ...prev];
            });
        };

        eventSource.onerror = (error) => {
            console.error("SSE connection error in AlertasPage:", error);
        };

        return () => {
            isMounted = false;
            eventSource.close();
        };
    }, [selectedId, token, filtroEstado, filtroTipo, filtroSeveridad]); // reconnect if filters change, to only get relevant SSE (though SSE from backend might send all, frontend filters them)

    const handleActionComplete = (alertaId: number, nuevoEstado: 'CONFIRMADA' | 'DESCARTADA') => {
        setAlertas((prev) => {
            // If the filter is PENDIENTE, remove it from the list when resolved
            if (filtroEstado === 'PENDIENTE') {
                return prev.filter((a) => a.id !== alertaId);
            }
            // Otherwise just update its state
            return prev.map((a) => (a.id === alertaId ? { ...a, estado: nuevoEstado } : a));
        });
    };

    const handleSort = (field: string) => {
        if (sortField === field) {
            if (sortDirection === 'asc') {
                setSortDirection('desc');
            } else {
                setSortField(null);
                setSortDirection('asc');
            }
        } else {
            setSortField(field);
            setSortDirection('asc');
        }
    };

    const sortedAlertas = [...alertas].sort((a, b) => {
        if (!sortField) return 0; // Default sort from backend
        let valA = a[sortField as keyof AlertaResponse] as string | number | null | undefined;
        let valB = b[sortField as keyof AlertaResponse] as string | number | null | undefined;
        
        if (valA === null || valA === undefined) valA = '';
        if (valB === null || valB === undefined) valB = '';

        if (valA < valB) return sortDirection === 'asc' ? -1 : 1;
        if (valA > valB) return sortDirection === 'asc' ? 1 : -1;
        return 0;
    });

    return (
        <>
            <div className="vigia-page-container">
                <Container>
                    <div className="vigia-page-header">
                        <div className="d-flex align-items-center gap-2">
                            <h2 className="vigia-page-title">Histórico de Alertas</h2>
                        </div>
                        <div className="d-flex align-items-center gap-2">
                            <Button className="btn-vigia-success" onClick={fetchAlertas} title="Actualizar">
                                <i className="bi bi-arrow-clockwise" /> Refrescar
                            </Button>
                        </div>
                    </div>

                    <div className="mb-4 mt-3 d-flex flex-wrap gap-3 p-3 bg-white rounded-3 shadow-sm" style={{ border: '1px solid #E2E8F0' }}>
                        <div style={{ flex: '1 1 200px' }}>
                            <Form.Label className="text-muted fw-semibold small mb-1">Estado</Form.Label>
                            <Form.Select 
                                value={filtroEstado} 
                                onChange={(e) => setFiltroEstado(e.target.value)}
                                style={{ borderColor: '#CBD5E0' }}
                            >
                                <option value="">Todos los estados</option>
                                <option value="PENDIENTE">Pendientes</option>
                                <option value="CONFIRMADA">Confirmadas</option>
                                <option value="DESCARTADA">Descartadas</option>
                            </Form.Select>
                        </div>
                        <div style={{ flex: '1 1 200px' }}>
                            <Form.Label className="text-muted fw-semibold small mb-1">Tipo</Form.Label>
                            <Form.Select 
                                value={filtroTipo} 
                                onChange={(e) => setFiltroTipo(e.target.value)}
                                style={{ borderColor: '#CBD5E0' }}
                            >
                                <option value="">Todos los tipos</option>
                                <option value="CELO">Celo</option>
                                <option value="SALUD">Salud</option>
                                <option value="GEOESPACIAL">Geoespacial</option>
                            </Form.Select>
                        </div>
                        <div style={{ flex: '1 1 200px' }}>
                            <Form.Label className="text-muted fw-semibold small mb-1">Severidad</Form.Label>
                            <Form.Select 
                                value={filtroSeveridad} 
                                onChange={(e) => setFiltroSeveridad(e.target.value)}
                                style={{ borderColor: '#CBD5E0' }}
                            >
                                <option value="">Todas las severidades</option>
                                <option value="ALTA">Alta</option>
                                <option value="MEDIA">Media</option>
                                <option value="BAJA">Baja</option>
                            </Form.Select>
                        </div>
                    </div>

                    {error && <Alert variant="danger" className="mt-3">{error}</Alert>}

                    {loading && alertas.length === 0 ? (
                        <div className="text-center mt-5">
                            <Spinner animation="border" style={{ color: '#1E3A5F' }} />
                        </div>
                    ) : (
                        <div className="vigia-table-wrapper">
                            <table className="vigia-table alerta-row">
                                <thead>
                                    <tr>
                                        <th onClick={() => handleSort('animalId')} style={{ cursor: 'pointer' }}>
                                            <div className="d-flex align-items-center justify-content-start">
                                                ID Animal <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px' }}>{sortField === 'animalId' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                            </div>
                                        </th>
                                        <th onClick={() => handleSort('tipo')} style={{ cursor: 'pointer' }}>
                                            <div className="d-flex align-items-center justify-content-start">
                                                Tipo <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px' }}>{sortField === 'tipo' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                            </div>
                                        </th>
                                        <th onClick={() => handleSort('severidad')} style={{ cursor: 'pointer' }}>
                                            <div className="d-flex align-items-center justify-content-center">
                                                Severidad <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px' }}>{sortField === 'severidad' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                            </div>
                                        </th>
                                        <th onClick={() => handleSort('tsGeneracion')} style={{ cursor: 'pointer' }}>
                                            <div className="d-flex align-items-center justify-content-start">
                                                Fecha <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px' }}>{sortField === 'tsGeneracion' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                            </div>
                                        </th>
                                        <th onClick={() => handleSort('estado')} style={{ cursor: 'pointer' }}>
                                            <div className="d-flex align-items-center justify-content-center">
                                                Estado <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px' }}>{sortField === 'estado' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                            </div>
                                        </th>
                                        <th className="text-center">Acciones</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {sortedAlertas.length === 0 ? (
                                        <tr>
                                            <td colSpan={6} className="text-center py-4 text-muted">
                                                No se encontraron alertas que coincidan con los filtros.
                                            </td>
                                        </tr>
                                    ) : (
                                        sortedAlertas.map((a) => {
                                            // Fallback gravedad to severidad mapping just in case backend still sends gravedad
                                            const sev = a.severidad ?? (a.gravedad as 'ALTA' | 'MEDIA' | 'BAJA') ?? (a.tipo === 'CELO' ? 'MEDIA' : 'ALTA');
                                            const tipoLower = esAnestro(a) ? 'anestro' : (a.tipo || 'CELO').toLowerCase();
                                            
                                            return (
                                                <tr key={a.id}>
                                                    <td className="fw-medium">#{a.animalId}</td>
                                                    <td>
                                                        <div className="d-flex align-items-center gap-2 flex-wrap">
                                                            <span className={`tipo-badge tipo-${tipoLower}`}>{esAnestro(a) ? 'ANESTRO' : a.tipo || 'CELO'}</span>
                                                            <DatoDesactualizadoBadge alerta={a} />
                                                        </div>
                                                    </td>
                                                    <td className="text-center">
                                                        <span className={`severidad-badge ${getSeveridadClass(sev)}`}>
                                                            {sev}
                                                        </span>
                                                    </td>
                                                    <td>{formatFecha(a.tsGeneracion)}</td>
                                                    <td className="text-center">
                                                        <span className={`estado-alerta-badge ${getEstadoClass(a.estado)}`}>
                                                            {a.estado}
                                                        </span>
                                                    </td>
                                                    <td className="text-center">
                                                        <Button
                                                            variant="vigia-outline"
                                                            size="sm"
                                                            onClick={() => setSelectedAlerta(a)}
                                                        >
                                                            <i className="bi bi-eye"></i> Ver Detalle
                                                        </Button>
                                                    </td>
                                                </tr>
                                            );
                                        })
                                    )}
                                </tbody>
                            </table>
                        </div>
                    )}
                </Container>
            </div>

            {/* Alert detail overlay */}
            {selectedAlerta && (
                <AlertDetailSidebar
                    alerta={selectedAlerta}
                    mode="fixed"
                    onClose={() => setSelectedAlerta(null)}
                    onActionComplete={handleActionComplete}
                    onOpenIntervencion={() => {
                        setIntervencionAlerta(selectedAlerta);
                        setIsIntervencionOpen(true);
                    }}
                />
            )}

            {isIntervencionOpen && intervencionAlerta && (
                <IntervencionFormPanel
                    alerta={intervencionAlerta}
                    mode="fixed"
                    onClose={() => setIsIntervencionOpen(false)}
                    onSuccess={() => {
                        setIsIntervencionOpen(false);
                        fetchAlertas();
                    }}
                />
            )}
        </>
    );
}
