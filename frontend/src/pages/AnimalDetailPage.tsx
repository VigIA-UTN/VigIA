import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Container, Button, Spinner, Alert, Card, Row, Col, Form, Modal, Badge } from 'react-bootstrap';
import {
    getAnimalById,
    updateAnimal,
    getHistorialEstados,
    registrarEstadoReproductivo,
    editarEstadoReproductivo,
    type AnimalResponse,
    type AnimalRequest,
    type EstadoReproductivoResponse,
    type EstadoReproductivoRequest,
    type TipoReproductivo,
    getIntervenciones,
    type IntervencionResponse
} from '../api/animalesApi';
import { getRodeoLotes, type RodeoLoteResponse } from '../api/rodeoLotesApi';
import { useAuthStore } from '../store/authStore';
import './AnimalDetailPage.css';
import './AnimalesPage.css';

const TIPOS_REPRODUCTIVOS: TipoReproductivo[] = ['VACIA', 'GESTANTE', 'CELO', 'ANESTRO'];

function estadoReproductivoBadge(estado: string | null | undefined) {
    if (!estado) return <span className="badge bg-secondary">—</span>;
    const map: Record<string, string> = {
        GESTANTE: 'bg-success',
        CELO: 'bg-danger',
        VACIA: 'bg-secondary',
        ANESTRO: 'bg-info',
    };
    return <Badge bg="" className={`${map[estado] || 'bg-secondary'}`}>{estado}</Badge>;
}

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
            estado: estado.estado as TipoReproductivo,
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
                    <h2 className="vigia-page-title text-break pe-3 mb-0">Ficha del Animal: {animal.alias || animal.idCaravanaOficial || `#${animal.id}`}
                        {animal.enCalibracion && (
                            <Badge bg="info" className="ms-3 align-middle" style={{ fontSize: '0.9rem' }}>
                                <i className="bi bi-clock-history me-1" /> Calibración en proceso ({animal.diasRestantesCalibracion} días)
                            </Badge>
                        )}
                    </h2>
                    <div className="d-flex align-items-center gap-2 flex-shrink-0">
                        {rol === 'PRODUCTOR' && !isEditing && (
                            <Button className="btn-vigia-primary px-4" onClick={() => setIsEditing(true)}>
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

                {formError && <Alert variant="danger">{formError}</Alert>}

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
                                        {historial.map((h, idx) => (
                                            <tr key={h.id} className={idx === 0 ? 'fw-semibold' : ''}>
                                                <td>{estadoReproductivoBadge(h.estado)}</td>
                                                <td>{formatFechaLocal(h.fechaUltimoParto)}</td>
                                                <td>{formatFechaLocal(h.fechaUltimaInseminacion)}</td>
                                                <td>
                                                    <span className={`badge ${h.origen === 'MANUAL' ? 'bg-primary' : h.origen === 'CARGA_MASIVA' ? 'bg-warning text-dark' : 'bg-info'}`}>
                                                        {h.origen || '—'}
                                                    </span>
                                                </td>
                                                <td>{h.registradoPor || '—'}</td>
                                                <td>{formatDateTime(h.fechaRegistro)}</td>
                                                {(rol === 'PRODUCTOR' || rol === 'VETERINARIO') && (
                                                    <td className="text-center">
                                                        {idx === 0 && (
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
                                            <th>ID Alerta</th>
                                            <th>Interventor</th>
                                            <th>Descripción</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {intervenciones.map((intv) => (
                                            <tr key={intv.id}>
                                                <td>{formatDateTime(intv.fechaHora)}</td>
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
                                    <option key={t} value={t}>{t}</option>
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
        </div>
    );
}
