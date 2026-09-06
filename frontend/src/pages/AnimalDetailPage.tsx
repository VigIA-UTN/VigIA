import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Container, Button, Spinner, Alert, Card, Row, Col, Form, Modal, Badge } from 'react-bootstrap';
import {
    getAnimalById,
    updateAnimal,
    getHistorialEstados,
    registrarEstadoReproductivo,
    editarEstadoReproductivo,
    getHistorialSalud,
    registrarEstadoSalud,
    ESTADO_REPRODUCTIVO_LABEL,
    ESTADO_SALUD_LABEL,
    type AnimalResponse,
    type AnimalRequest,
    type EstadoReproductivoResponse,
    type EstadoReproductivoRequest,
    type TipoReproductivo,
    type TipoEstadoSalud,
    type EstadoSaludResponse,
    getIntervenciones,
    type IntervencionResponse
} from '../api/animalesApi';
import { getRodeoLotes, type RodeoLoteResponse } from '../api/rodeoLotesApi';
import { useAuthStore } from '../store/authStore';
import './AnimalDetailPage.css';
import './AnimalesPage.css';
import ActividadTab from './ActividadTab';
import AnimalAlertasSanitarias from '../components/AnimalAlertasSanitarias';
import IntervencionFormPanel from '../components/IntervencionFormPanel';
import type { AlertaResponse } from '../api/alertaApi';

const TIPOS_REPRODUCTIVOS: TipoReproductivo[] = ['VACIA', 'GESTANTE', 'CELO', 'ANESTRO'];

const TIPOS_ESTADO_SALUD: TipoEstadoSalud[] = ['SANO', 'EN_OBSERVACION', 'ENFERMO'];

function estadoSaludBadge(estado: string | null | undefined) {
    if (!estado) return <span className="badge bg-secondary">—</span>;
    const map: Record<string, string> = {
        SANO: 'bg-success',
        EN_OBSERVACION: 'bg-warning text-dark',
        ENFERMO: 'bg-danger',
    };
    return <Badge bg="" className={`${map[estado] || 'bg-secondary'}`}>{ESTADO_SALUD_LABEL[estado] ?? estado}</Badge>;
}

interface SaludForm {
    estado: TipoEstadoSalud | '';
    diagnosticoNotas: string;
    tratamientoIndicado: string;
    fecha: string;
}

const INTERVENCION_TIPO_LABELS: Record<string, string> = {
    TRATAMIENTO: 'Tratamiento',
    VACUNACION: 'Vacunación',
    REVISION_VETERINARIA: 'Revisión veterinaria',
    INSEMINACION: 'Inseminación',
    OTRO: 'Otro',
};

function estadoReproductivoBadge(estado: string | null | undefined) {
    if (!estado) return <span className="badge bg-secondary">—</span>;
    const map: Record<string, string> = {
        GESTANTE: 'bg-success',
        CELO: 'bg-danger',
        VACIA: 'bg-secondary',
        ANESTRO: 'bg-info',
        POSIBLE_CELO: 'bg-warning text-dark',
        INSEMINADA: 'bg-primary',
        PARIDA: 'bg-success',
    };
    return <Badge bg="" className={`${map[estado] || 'bg-secondary'}`}>{ESTADO_REPRODUCTIVO_LABEL[estado] ?? estado}</Badge>;
}

const TIPO_EVENTO_LABEL: Record<string, string> = {
    ESTADO_REPRODUCTIVO: 'Estado',
    CELO_AUTOMATICO: 'Celo (ml-celo)',
    INSEMINACION: 'Inseminación',
};

function formatFechaLocal(iso: string | null | undefined) {
    if (!iso) return '—';
    const [y, m, d] = iso.split('-');
    if (d) return `${d.substring(0, 2)}/${m}/${y}`;
    return `${m}/${y}`;
}

function formatDateTime(dt: string | null | undefined) {
    if (!dt) return '—';
    const date = new Date(dt);
    return date.toLocaleString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

const EMPTY_ESTADO_FORM: EstadoReproductivoRequest = {
    estado: 'VACIA',
    fechaUltimoParto: null,
    fechaUltimaInseminacion: null,
};

const EMPTY_SALUD_FORM: SaludForm = {
    estado: '',
    diagnosticoNotas: '',
    tratamientoIndicado: '',
    fecha: '',
};

export default function AnimalDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const rol = useAuthStore(state => state.rol);

    const [animal, setAnimal] = useState<AnimalResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [isEditing, setIsEditing] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);

    const [form, setForm] = useState<Partial<AnimalRequest>>({});
    const [rodeoLotes, setRodeoLotes] = useState<RodeoLoteResponse[]>([]);

    // Historial de estados reproductivos
    const [historial, setHistorial] = useState<EstadoReproductivoResponse[]>([]);
    const [loadingHistorial, setLoadingHistorial] = useState(false);

    // Intervenciones
    const [intervenciones, setIntervenciones] = useState<IntervencionResponse[]>([]);
    const [loadingIntervenciones, setLoadingIntervenciones] = useState(false);

    // Modal de estado reproductivo
    const [showEstadoModal, setShowEstadoModal] = useState(false);
    const [editingEstado, setEditingEstado] = useState<EstadoReproductivoResponse | null>(null); // null = nuevo, not null = editar
    const [estadoForm, setEstadoForm] = useState<EstadoReproductivoRequest>(EMPTY_ESTADO_FORM);
    const [submittingEstado, setSubmittingEstado] = useState(false);
    const [estadoFormError, setEstadoFormError] = useState<string | null>(null);
    const [estadoSuccess, setEstadoSuccess] = useState<string | null>(null);

    // Historial de estados de salud
    const [saludHistorial, setSaludHistorial] = useState<EstadoSaludResponse[]>([]);
    const [loadingSalud, setLoadingSalud] = useState(false);

    // Modal de estado de salud
    const [showSaludModal, setShowSaludModal] = useState(false);
    const [saludForm, setSaludForm] = useState<SaludForm>(EMPTY_SALUD_FORM);
    const [submittingSalud, setSubmittingSalud] = useState(false);
    const [saludFormError, setSaludFormError] = useState<string | null>(null);
    const [saludSuccess, setSaludSuccess] = useState<string | null>(null);

    // Tab state — 'actividad' is the default
    const [activeTab, setActiveTab] = useState<'actividad' | 'ficha'>('actividad');

    // NUEVO: para manejar el modal de intervención desde la pestaña actividad
    const [isIntervencionOpen, setIsIntervencionOpen] = useState(false);
    const [intervencionAlerta, setIntervencionAlerta] = useState<AlertaResponse | null>(null);

    const fetchHistorial = async (animalId: number) => {
        setLoadingHistorial(true);
        try {
            const data = await getHistorialEstados(animalId);
            setHistorial(data);
        } catch {
            // no-op: historial is optional
        } finally {
            setLoadingHistorial(false);
        }
    };

    const fetchIntervencionesData = async (animalId: number) => {
        setLoadingIntervenciones(true);
        try {
            const data = await getIntervenciones(animalId);
            setIntervenciones(data);
        } catch {
            // no-op: intervenciones is optional
        } finally {
            setLoadingIntervenciones(false);
        }
    };

    const fetchSalud = async (animalId: number) => {
        setLoadingSalud(true);
        try {
            const data = await getHistorialSalud(animalId);
            setSaludHistorial(data);
        } catch {
            // no-op: historial de salud is optional
        } finally {
            setLoadingSalud(false);
        }
    };

    useEffect(() => {
        if (!id) return;
        (async () => {
            try {
                const data = await getAnimalById(Number(id));
                setAnimal(data);
                setForm({
                    idCaravanaOficial: data.idCaravanaOficial,
                    alias: data.alias,
                    fechaNacimiento: data.fechaNacimiento,
                    raza: data.raza,
                    rodeoLoteId: data.rodeoLoteId,
                    estadoActual: data.estadoActual,
                    establecimientoId: data.establecimientoId
                });

                if (rol === 'PRODUCTOR') {
                    const lotes = await getRodeoLotes(data.establecimientoId);
                    setRodeoLotes(lotes.filter(l => l.activo));
                }

                fetchHistorial(data.id);
                fetchIntervencionesData(data.id);
                fetchSalud(data.id);
            } catch (err) {
                const e = err as { response?: { data?: { message?: string } } };
                setError(e.response?.data?.message || 'Error al cargar la ficha del animal.');
            } finally {
                setLoading(false);
            }
        })();
    }, [id, rol]);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        const { name, value } = e.target;
        setForm(prev => ({ ...prev, [name]: value || null }));
    };

    const handleSave = async () => {
        if (!id || !form.establecimientoId || !form.fechaNacimiento || !form.estadoActual) return;
        setSubmitting(true);
        setFormError(null);
        try {
            const updated = await updateAnimal(Number(id), form as AnimalRequest);
            setAnimal(updated);
            setIsEditing(false);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } } };
            setFormError(e.response?.data?.message || 'Error al actualizar animal');
        } finally {
            setSubmitting(false);
        }
    };

    const openNuevoEstadoModal = () => {
        setEditingEstado(null);
        const lastEstado = historial.length > 0 ? historial[0] : null;
        setEstadoForm({
            estado: 'VACIA',
            fechaUltimoParto: lastEstado?.fechaUltimoParto || null,
            fechaUltimaInseminacion: lastEstado?.fechaUltimaInseminacion || null,
        });
        setEstadoFormError(null);
        setEstadoSuccess(null);
        setShowEstadoModal(true);
    };

    const openEditarEstadoModal = (estado: EstadoReproductivoResponse) => {
        setEditingEstado(estado);
        setEstadoForm({
            estado: (estado.estado ?? 'VACIA') as TipoReproductivo,
            fechaUltimoParto: estado.fechaUltimoParto,
            fechaUltimaInseminacion: estado.fechaUltimaInseminacion,
        });
        setEstadoFormError(null);
        setEstadoSuccess(null);
        setShowEstadoModal(true);
    };

    const handleEstadoFormChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        const { name, value } = e.target;
        setEstadoForm(prev => ({ ...prev, [name]: value || null }));
    };

    const handleSubmitEstado = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!id) return;
        setEstadoFormError(null);
        setEstadoSuccess(null);

        if (estadoForm.fechaUltimoParto && estadoForm.fechaUltimaInseminacion) {
            const fParto = new Date(estadoForm.fechaUltimoParto);
            const fInsem = new Date(estadoForm.fechaUltimaInseminacion);
            if (fInsem > fParto && estadoForm.estado !== 'GESTANTE') {
                setEstadoFormError('La fecha de última inseminación no puede ser posterior al último parto sin un estado de gestación.');
                return;
            }
        }

        if (estadoForm.fechaUltimoParto) {
            const referenceIndex = editingEstado ? 1 : 0;
            const refEstado = historial.length > referenceIndex ? historial[referenceIndex] : null;
            if (refEstado?.fechaUltimoParto) {
                const fPartoNuevo = new Date(estadoForm.fechaUltimoParto);
                const fPartoAnterior = new Date(refEstado.fechaUltimoParto);
                if (fPartoNuevo < fPartoAnterior) {
                    setEstadoFormError(
                        `La fecha de último parto no puede ser anterior a la ya registrada (${refEstado.fechaUltimoParto.split('-').reverse().join('/')}).`
                    );
                    return;
                }
            }
        }

        setSubmittingEstado(true);
        try {
            if (editingEstado) {
                await editarEstadoReproductivo(Number(id), editingEstado.id, estadoForm);
            } else {
                await registrarEstadoReproductivo(Number(id), estadoForm);
            }
            // Guardado exitoso: mostrar éxito y cerrar modal
            setEstadoSuccess(editingEstado ? 'Estado actualizado correctamente.' : 'Estado registrado correctamente.');
            // Refresh animal e historial (en bloque separado para que un fallo no tape el éxito)
            try {
                const [updatedAnimal, updatedHistorial] = await Promise.all([
                    getAnimalById(Number(id)),
                    getHistorialEstados(Number(id)),
                ]);
                setAnimal(updatedAnimal);
                setHistorial(updatedHistorial);
            } catch {
                // Si el refresh falla, recargamos historial aparte
                await fetchHistorial(Number(id));
            }
            setTimeout(() => {
                setShowEstadoModal(false);
                setEstadoSuccess(null);
            }, 1200);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setEstadoFormError(
                typeof e.response?.data === 'string'
                    ? e.response.data
                    : e.response?.data?.message || 'Error al guardar el estado reproductivo.'
            );
        } finally {
            setSubmittingEstado(false);
        }
    };

    const openNuevoSaludModal = () => {
        setSaludForm({ ...EMPTY_SALUD_FORM, fecha: new Date().toISOString().split('T')[0] });
        setSaludFormError(null);
        setSaludSuccess(null);
        setShowSaludModal(true);
    };

    const handleSaludFormChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        const { name, value } = e.target;
        setSaludForm(prev => ({ ...prev, [name]: value }));
    };

    const handleSubmitSalud = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!id) return;
        setSaludFormError(null);
        setSaludSuccess(null);

        if (!saludForm.estado) {
            setSaludFormError('El estado general es obligatorio.');
            return;
        }

        setSubmittingSalud(true);
        try {
            await registrarEstadoSalud(Number(id), {
                estado: saludForm.estado,
                diagnosticoNotas: saludForm.diagnosticoNotas.trim() || null,
                tratamientoIndicado: saludForm.tratamientoIndicado.trim() || null,
                fecha: saludForm.fecha || null,
            });
            setSaludSuccess('Estado de salud registrado correctamente.');
            try {
                const [updatedAnimal, updatedSalud] = await Promise.all([
                    getAnimalById(Number(id)),
                    getHistorialSalud(Number(id)),
                ]);
                setAnimal(updatedAnimal);
                setSaludHistorial(updatedSalud);
            } catch {
                await fetchSalud(Number(id));
            }
            setTimeout(() => {
                setShowSaludModal(false);
                setSaludSuccess(null);
            }, 1200);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setSaludFormError(
                typeof e.response?.data === 'string'
                    ? e.response.data
                    : e.response?.data?.message || 'Error al guardar el estado de salud.'
            );
        } finally {
            setSubmittingSalud(false);
        }
    };

    if (loading) {
        return (
            <div className="text-center mt-5">
                <Spinner animation="border" style={{ color: '#1E3A5F' }} />
            </div>
        );
    }

    if (error) {
        return (
            <Container className="mt-4">
                <Alert variant="danger">{error}</Alert>
                <Button variant="secondary" onClick={() => navigate('/animales')}>
                    Volver al listado
                </Button>
            </Container>
        );
    }

    if (!animal) return null;

    return (
        <div className="vigia-page-container animal-detail-container">
            <Container>
                <div className="vigia-page-header mb-4 flex-wrap gap-3">
                    <h2 className="vigia-page-title text-break pe-3 mb-0">
                        {animal.alias || animal.idCaravanaOficial || `#${animal.id}`}
                        {animal.enCalibracion && (
                            <Badge bg="info" className="ms-3 align-middle" style={{ fontSize: '0.9rem' }}>
                                <i className="bi bi-clock-history me-1" /> Calibración en proceso ({animal.diasRestantesCalibracion === 0 ? 'finaliza hoy' : `${animal.diasRestantesCalibracion} días`})
                            </Badge>
                        )}
                    </h2>
                    <div className="d-flex align-items-center gap-2 flex-shrink-0">
                        {rol === 'PRODUCTOR' && !isEditing && (
                            <Button
                                className="btn-vigia-primary px-4"
                                onClick={() => setIsEditing(true)}
                                style={{ visibility: activeTab === 'ficha' ? 'visible' : 'hidden' }}
                            >
                                <i className="bi bi-pencil me-2" /> Editar
                            </Button>
                        )}
                        {isEditing && (
                            <>
                                <Button variant="secondary" className="px-4" onClick={() => {
                                    setIsEditing(false);
                                    setFormError(null);
                                    setForm({
                                        idCaravanaOficial: animal.idCaravanaOficial,
                                        alias: animal.alias,
                                        fechaNacimiento: animal.fechaNacimiento,
                                        raza: animal.raza,
                                        rodeoLoteId: animal.rodeoLoteId,
                                        estadoActual: animal.estadoActual,
                                        establecimientoId: animal.establecimientoId
                                    });
                                }}>
                                    Cancelar
                                </Button>
                                <Button className="btn-vigia-success px-4" onClick={handleSave} disabled={submitting}>
                                    {submitting ? <Spinner as="span" animation="border" size="sm" /> : 'Guardar'}
                                </Button>
                            </>
                        )}
                        <Button variant="outline-secondary" className="px-4" onClick={() => navigate('/animales')}>
                            <i className="bi bi-arrow-left me-2" /> Volver
                        </Button>
                    </div>
                </div>

                {/* ── Tab bar ── */}
                <div className="animal-tabs-bar">
                    <button
                        className={`animal-tab-btn${activeTab === 'actividad' ? ' active' : ''}`}
                        onClick={() => { setActiveTab('actividad'); setIsEditing(false); }}
                    >
                        Actividad
                    </button>
                    <button
                        className={`animal-tab-btn${activeTab === 'ficha' ? ' active' : ''}`}
                        onClick={() => setActiveTab('ficha')}
                    >
                        Ficha
                    </button>
                </div>

                {formError && <Alert variant="danger">{formError}</Alert>}

                {animal.enCalibracion && (
                    <Alert variant="info" className="d-flex align-items-center shadow-sm mb-4" style={{ borderLeft: '5px solid #0dcaf0' }}>
                        <i className="bi bi-info-circle-fill fs-3 me-3 text-info"></i>
                        <div>
                            <h5 className="alert-heading mb-1" style={{ fontSize: '1.1rem' }}>Período de calibración en proceso</h5>
                            <p className="mb-0">
                                {animal.diasRestantesCalibracion === 0
                                    ? <>La calibración <strong>finaliza hoy</strong>.</>
                                    : <>Faltan <strong>{animal.diasRestantesCalibracion} días</strong> para finalizar.</>
                                } Durante esta etapa el collar está recolectando datos base del comportamiento del animal, por lo tanto <strong>no se generarán alertas sanitarias</strong> temporariamente.
                            </p>
                        </div>
                    </Alert>
                )}

                {/* ── Tab: Actividad ── */}
                {activeTab === 'actividad' && (
                    <div key="actividad" className="tab-content-animated">
                        <ActividadTab animalId={animal.id} />
                        <AnimalAlertasSanitarias animalId={animal.id} establecimientoId={animal.establecimientoId} onOpenIntervencion={(alerta) => {
                            setIntervencionAlerta(alerta);
                            setIsIntervencionOpen(true);
                        }} />
                        {isIntervencionOpen && intervencionAlerta && (
                            <IntervencionFormPanel
                                alerta={intervencionAlerta}
                                mode="fixed"
                                onClose={() => setIsIntervencionOpen(false)}
                                onSuccess={() => {
                                    fetchIntervencionesData(animal.id);
                                    setIsIntervencionOpen(false);
                                }}
                            />
                        )}
                    </div>
                )}

                {/* ── Tab: Ficha ── */}
                {activeTab === 'ficha' && (<div key="ficha" className="tab-content-animated">

                    <Card className="shadow-sm border-0 animal-card mb-4">
                        <Card.Body className="p-4 p-md-5">
                            {isEditing ? (
                                <Form>
                                    <h4 className="section-title mb-4">Información General</h4>
                                    <Row className="g-4">
                                        <Col md={6}>
                                            <Form.Group className="mb-3">
                                                <Form.Label className="fw-bold">Identificador Oficial</Form.Label>
                                                <Form.Control name="idCaravanaOficial" value={form.idCaravanaOficial ?? ''} onChange={handleChange} />
                                            </Form.Group>
                                            <Form.Group className="mb-3">
                                                <Form.Label className="fw-bold">Nombre/Alias</Form.Label>
                                                <Form.Control name="alias" value={form.alias ?? ''} onChange={handleChange} />
                                            </Form.Group>
                                            <Form.Group className="mb-3">
                                                <Form.Label className="fw-bold">Fecha de Nacimiento</Form.Label>
                                                <Form.Control type="date" name="fechaNacimiento" value={form.fechaNacimiento ?? ''} onChange={handleChange} />
                                            </Form.Group>
                                            <Form.Group className="mb-3">
                                                <Form.Label className="fw-bold">Raza</Form.Label>
                                                <Form.Control name="raza" value={form.raza ?? ''} onChange={handleChange} />
                                            </Form.Group>
                                        </Col>
                                        <Col md={6}>
                                            <Form.Group className="mb-3">
                                                <Form.Label className="fw-bold">Establecimiento</Form.Label>
                                                <Form.Control plaintext readOnly value={`#${animal.establecimientoId}`} />
                                            </Form.Group>
                                            <Form.Group className="mb-3">
                                                <Form.Label className="fw-bold">Rodeo/Lote</Form.Label>
                                                <Form.Select name="rodeoLoteId" value={form.rodeoLoteId ?? ''} onChange={handleChange}>
                                                    <option value="">Sin rodeo</option>
                                                    {rodeoLotes.filter(l => l.establecimientoId === animal.establecimientoId).map(l => (
                                                        <option key={l.id} value={l.id}>{l.nombre}</option>
                                                    ))}
                                                </Form.Select>
                                            </Form.Group>
                                            <Form.Group className="mb-3">
                                                <Form.Label className="fw-bold">Estado Actual</Form.Label>
                                                <div>
                                                    <span className={`badge ${animal.estadoActual === 'ACTIVO' ? 'bg-success' : animal.estadoActual === 'CON_ALERTA' ? 'bg-warning text-dark' : 'bg-secondary'}`}>
                                                        {animal.estadoActual}
                                                    </span>
                                                </div>
                                            </Form.Group>
                                            <ul className="list-unstyled detail-list mt-4">
                                                <li><strong>Fecha Alta en VigIA:</strong> {formatFechaLocal(animal.fechaAlta)}</li>
                                            </ul>
                                        </Col>
                                    </Row>
                                </Form>
                            ) : (
                                <>
                                    <h4 className="section-title mb-4">Información General</h4>
                                    <Row className="g-4">
                                        <Col md={6}>
                                            <ul className="list-unstyled detail-list">
                                                <li><strong>Identificador Oficial:</strong> {animal.idCaravanaOficial || '—'}</li>
                                                <li><strong>Nombre/Alias:</strong> {animal.alias || '—'}</li>
                                                <li><strong>Fecha de Nacimiento:</strong> {formatFechaLocal(animal.fechaNacimiento)}</li>
                                                <li><strong>Raza:</strong> {animal.raza || '—'}</li>
                                            </ul>
                                        </Col>
                                        <Col md={6}>
                                            <ul className="list-unstyled detail-list">
                                                <li><strong>Establecimiento:</strong> #{animal.establecimientoId}</li>
                                                <li><strong>Rodeo/Lote:</strong> {animal.rodeoLoteNombre || (animal.rodeoLoteId ? `#${String(animal.rodeoLoteId).padStart(2, '0')}` : '—')}</li>
                                                <li><strong>Fecha Alta en VigIA:</strong> {formatFechaLocal(animal.fechaAlta)}</li>
                                                <li><strong>Estado Actual:</strong> <span className={`badge ${animal.estadoActual === 'ACTIVO' ? 'bg-success' : animal.estadoActual === 'CON_ALERTA' ? 'bg-warning text-dark' : 'bg-secondary'}`}>{animal.estadoActual}</span></li>
                                                <li><strong>Estado de Salud:</strong> {estadoSaludBadge(saludHistorial[0]?.estado ?? animal.estadoSalud)}</li>
                                            </ul>
                                        </Col>
                                    </Row>
                                </>
                            )}
                        </Card.Body>
                    </Card>

                    {/* ── Sección Historial Reproductivo ── */}
                    <Card className="shadow-sm border-0 animal-card">
                        <Card.Body>
                            <div className="d-flex justify-content-between align-items-center mb-3">
                                <h4 className="section-title mb-0">Historial Reproductivo</h4>
                                {(rol === 'PRODUCTOR' || rol === 'VETERINARIO') && (
                                    <Button className="btn-vigia-success btn-sm px-3" onClick={openNuevoEstadoModal}>
                                        <i className="bi bi-plus-lg me-1" /> Registrar estado
                                    </Button>
                                )}
                            </div>

                            {loadingHistorial ? (
                                <div className="text-center py-3">
                                    <Spinner animation="border" size="sm" style={{ color: '#1E3A5F' }} />
                                </div>
                            ) : historial.length === 0 ? (
                                <p className="text-muted text-center py-3">Sin registros reproductivos aún.</p>
                            ) : (
                                <div className="table-responsive">
                                    <table className="vigia-table" style={{ fontSize: '0.92rem' }}>
                                        <thead>
                                            <tr>
                                                <th>Tipo</th>
                                                <th>Estado</th>
                                                <th>Último Parto</th>
                                                <th>Última Inseminación</th>
                                                <th>Origen</th>
                                                <th>Registrado por</th>
                                                <th>Fecha Registro</th>
                                                {(rol === 'PRODUCTOR' || rol === 'VETERINARIO') && <th className="text-center">Acciones</th>}
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {historial.map((h, idx) => {
                                                const origen = h.origenNormalizado || h.origen;
                                                const editable = h.tipoEvento !== 'INSEMINACION';
                                                return (
                                                <tr key={`${h.tipoEvento ?? 'X'}-${h.id}`} className={idx === 0 ? 'fw-semibold' : ''}>
                                                    <td>{h.tipoEvento ? (TIPO_EVENTO_LABEL[h.tipoEvento] || h.tipoEvento) : '—'}</td>
                                                    <td>{estadoReproductivoBadge(h.estado)}</td>
                                                    <td>{formatFechaLocal(h.fechaUltimoParto)}</td>
                                                    <td>{formatFechaLocal(h.fechaUltimaInseminacion)}</td>
                                                    <td>
                                                        <span className={`badge ${origen === 'MANUAL' ? 'bg-primary' : origen === 'AUTOMATICO' ? 'bg-info' : 'bg-secondary'}`}>
                                                            {origen || '—'}
                                                        </span>
                                                    </td>
                                                    <td>{h.registradoPor || '—'}</td>
                                                    <td>{formatDateTime(h.fechaRegistro)}</td>
                                                    {(rol === 'PRODUCTOR' || rol === 'VETERINARIO') && (
                                                        <td className="text-center">
                                                            {idx === 0 && editable && (
                                                                <Button
                                                                    variant="outline-primary"
                                                                    size="sm"
                                                                    onClick={() => openEditarEstadoModal(h)}
                                                                >
                                                                    <i className="bi bi-pencil" /> Editar
                                                                </Button>
                                                            )}
                                                        </td>
                                                    )}
                                                </tr>
                                                );
                                            })}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </Card.Body>
                    </Card>

                    {/* ── Sección Estado de Salud ── */}
                    <Card className="shadow-sm border-0 animal-card mt-4">
                        <Card.Body>
                            <div className="d-flex justify-content-between align-items-center mb-3">
                                <h4 className="section-title mb-0">Estado de Salud</h4>
                                {(rol === 'PRODUCTOR' || rol === 'VETERINARIO') && (
                                    <Button className="btn-vigia-success btn-sm px-3" onClick={openNuevoSaludModal}>
                                        <i className="bi bi-plus-lg me-1" /> Registrar estado
                                    </Button>
                                )}
                            </div>

                            {loadingSalud ? (
                                <div className="text-center py-3">
                                    <Spinner animation="border" size="sm" style={{ color: '#1E3A5F' }} />
                                </div>
                            ) : saludHistorial.length === 0 ? (
                                <p className="text-muted text-center py-3">Sin registros de salud aún.</p>
                            ) : (
                                <div className="table-responsive">
                                    <table className="vigia-table" style={{ fontSize: '0.92rem' }}>
                                        <thead>
                                            <tr>
                                                <th>Fecha Revisión</th>
                                                <th>Estado</th>
                                                <th>Diagnóstico / Notas</th>
                                                <th>Tratamiento</th>
                                                <th>Origen</th>
                                                <th>Registrado por</th>
                                                <th>Fecha Registro</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {saludHistorial.map((s, idx) => (
                                                <tr key={s.id} className={idx === 0 ? 'fw-semibold' : ''}>
                                                    <td>{formatFechaLocal(s.fechaRevision)}</td>
                                                    <td>{estadoSaludBadge(s.estado)}</td>
                                                    <td>{s.diagnosticoNotas || '—'}</td>
                                                    <td>{s.tratamientoIndicado || '—'}</td>
                                                    <td>
                                                        <span className={`badge ${s.origen === 'MANUAL' ? 'bg-primary' : s.origen === 'AUTOMATICO' ? 'bg-info' : 'bg-secondary'}`}>
                                                            {s.origen || '—'}
                                                        </span>
                                                    </td>
                                                    <td>{s.registradoPor || '—'}</td>
                                                    <td>{formatDateTime(s.fechaRegistro)}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </Card.Body>
                    </Card>

                    {/* ── Sección Intervenciones ── */}
                    <Card className="shadow-sm border-0 animal-card mt-4">
                        <Card.Body>
                            <div className="d-flex justify-content-between align-items-center mb-3">
                                <h4 className="section-title mb-0">Intervenciones</h4>
                            </div>

                            {loadingIntervenciones ? (
                                <div className="text-center py-3">
                                    <Spinner animation="border" size="sm" style={{ color: '#1E3A5F' }} />
                                </div>
                            ) : intervenciones.length === 0 ? (
                                <p className="text-muted text-center py-3">No hay intervenciones registradas para este animal.</p>
                            ) : (
                                <div className="table-responsive">
                                    <table className="vigia-table" style={{ fontSize: '0.92rem' }}>
                                        <thead>
                                            <tr>
                                                <th>Fecha y Hora</th>
                                                <th>Tipo</th>
                                                <th>ID Alerta</th>
                                                <th>Interventor</th>
                                                <th>Descripción</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {intervenciones.map((intv) => (
                                                <tr key={intv.id}>
                                                    <td>{formatDateTime(intv.fechaHora)}</td>
                                                    <td>{INTERVENCION_TIPO_LABELS[intv.tipo] ?? intv.tipo ?? '—'}</td>
                                                    <td>{intv.alertaId ? `#${intv.alertaId}` : '—'}</td>
                                                    <td>{intv.interventor || '—'}</td>
                                                    <td>{intv.descripcion}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </Card.Body>
                    </Card>

                </div>)}
            </Container>

            {/* ── Modal Registrar / Editar Estado Reproductivo ── */}
            <Modal show={showEstadoModal} onHide={() => setShowEstadoModal(false)} centered dialogClassName="modal-nuevo-animal">
                <Modal.Header closeButton>
                    <Modal.Title>
                        {editingEstado ? 'Editar Estado Reproductivo' : 'Registrar Estado Reproductivo'}
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {estadoFormError && <Alert variant="danger">{estadoFormError}</Alert>}
                    {estadoSuccess && <Alert variant="success">{estadoSuccess}</Alert>}
                    <Form onSubmit={handleSubmitEstado} id="form-estado-reproductivo">
                        <Form.Group className="mb-3">
                            <Form.Label>Estado Reproductivo*</Form.Label>
                            <Form.Select name="estado" value={estadoForm.estado} onChange={handleEstadoFormChange} required>
                                {TIPOS_REPRODUCTIVOS.map(t => (
                                    <option key={t} value={t}>{ESTADO_REPRODUCTIVO_LABEL[t] ?? t}</option>
                                ))}
                            </Form.Select>
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Fecha Último Parto</Form.Label>
                            <Form.Control
                                type="date"
                                name="fechaUltimoParto"
                                value={estadoForm.fechaUltimoParto ?? ''}
                                onChange={handleEstadoFormChange}
                                max={new Date().toISOString().split('T')[0]}
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Fecha Última Inseminación</Form.Label>
                            <Form.Control
                                type="date"
                                name="fechaUltimaInseminacion"
                                value={estadoForm.fechaUltimaInseminacion ?? ''}
                                onChange={handleEstadoFormChange}
                                max={new Date().toISOString().split('T')[0]}
                            />
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button
                        type="submit"
                        form="form-estado-reproductivo"
                        disabled={submittingEstado || !!estadoSuccess}
                        className="btn-guardar-animal"
                    >
                        {submittingEstado
                            ? <Spinner as="span" animation="border" size="sm" />
                            : editingEstado ? 'Actualizar' : 'Registrar'
                        }
                    </Button>
                </Modal.Footer>
            </Modal>

            {/* ── Modal Registrar Estado de Salud ── */}
            <Modal show={showSaludModal} onHide={() => setShowSaludModal(false)} centered dialogClassName="modal-nuevo-animal">
                <Modal.Header closeButton>
                    <Modal.Title>Registrar Estado de Salud</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {saludFormError && <Alert variant="danger">{saludFormError}</Alert>}
                    {saludSuccess && <Alert variant="success">{saludSuccess}</Alert>}
                    <Form onSubmit={handleSubmitSalud} id="form-estado-salud">
                        <Form.Group className="mb-3">
                            <Form.Label>Fecha de revisión</Form.Label>
                            <Form.Control
                                type="date"
                                name="fecha"
                                value={saludForm.fecha}
                                onChange={handleSaludFormChange}
                                max={new Date().toISOString().split('T')[0]}
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Estado General*</Form.Label>
                            <Form.Select name="estado" value={saludForm.estado} onChange={handleSaludFormChange} required>
                                <option value="" disabled>Seleccioná un estado</option>
                                {TIPOS_ESTADO_SALUD.map(t => (
                                    <option key={t} value={t}>{ESTADO_SALUD_LABEL[t] ?? t}</option>
                                ))}
                            </Form.Select>
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Diagnóstico / Notas</Form.Label>
                            <Form.Control
                                as="textarea"
                                rows={3}
                                name="diagnosticoNotas"
                                value={saludForm.diagnosticoNotas}
                                onChange={handleSaludFormChange}
                                maxLength={1000}
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Tratamiento indicado</Form.Label>
                            <Form.Control
                                as="textarea"
                                rows={2}
                                name="tratamientoIndicado"
                                value={saludForm.tratamientoIndicado}
                                onChange={handleSaludFormChange}
                                maxLength={1000}
                            />
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button
                        type="submit"
                        form="form-estado-salud"
                        disabled={submittingSalud || !!saludSuccess || !saludForm.estado}
                        className="btn-guardar-animal"
                    >
                        {submittingSalud
                            ? <Spinner as="span" animation="border" size="sm" />
                            : 'Registrar'
                        }
                    </Button>
                </Modal.Footer>
            </Modal>
        </div>
    );
}
