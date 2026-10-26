import { useEffect, useState } from 'react';
import { Card, Button, Spinner, Alert, Form, Modal, Badge } from 'react-bootstrap';
import {
    getAnimalById,
    getHistorialEstados,
    registrarEstadoReproductivo,
    editarEstadoReproductivo,
    ESTADO_REPRODUCTIVO_LABEL,
    type AnimalResponse,
    type EstadoReproductivoResponse,
    type EstadoReproductivoRequest,
    type TipoReproductivo,
} from '../api/animalesApi';
import LineaTiempoAlertas from '../components/LineaTiempoAlertas';
import { formatDateTime, formatFechaLocal } from '../utils/fechas';
import './AnimalDetailPage.css';
import './AnimalesPage.css';

const TIPOS_REPRODUCTIVOS: TipoReproductivo[] = ['VACIA', 'GESTANTE', 'CELO', 'ANESTRO'];

const EMPTY_ESTADO_FORM: EstadoReproductivoRequest = {
    estado: 'VACIA',
    fechaUltimoParto: null,
    fechaUltimaInseminacion: null,
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

/** Badge de origen del dato (automático vs manual). */
function origenBadge(origen: string | null | undefined) {
    switch (origen) {
        case 'AUTOMATICO':
            return <Badge bg="" className="bg-info"><i className="bi bi-robot me-1" />Automático</Badge>;
        case 'MANUAL':
            return <Badge bg="" className="bg-primary"><i className="bi bi-person-fill me-1" />Manual</Badge>;
        case 'CARGA_MASIVA':
            return <Badge bg="" className="bg-primary"><i className="bi bi-upload me-1" />Carga masiva</Badge>;
        default:
            return <span className="badge bg-secondary">—</span>;
    }
}

const formatDateForInput = (dateStr: string | null | undefined) => (dateStr ? dateStr.split('T')[0] : null);

interface Props {
    animal: AnimalResponse;
    rol: string | null | undefined;
    onAnimalUpdated: (a: AnimalResponse) => void;
}

export default function ReproduccionTab({ animal, rol, onAnimalUpdated }: Props) {
    const animalId = animal.id;
    const canEdit = rol === 'PRODUCTOR' || rol === 'VETERINARIO';

    const [historial, setHistorial] = useState<EstadoReproductivoResponse[]>([]);
    const [loadingHistorial, setLoadingHistorial] = useState(true);

    const [showEstadoModal, setShowEstadoModal] = useState(false);
    const [editingEstado, setEditingEstado] = useState<EstadoReproductivoResponse | null>(null); // null = nuevo
    const [estadoForm, setEstadoForm] = useState<EstadoReproductivoRequest>(EMPTY_ESTADO_FORM);
    const [submittingEstado, setSubmittingEstado] = useState(false);
    const [estadoFormError, setEstadoFormError] = useState<string | null>(null);
    const [estadoSuccess, setEstadoSuccess] = useState<string | null>(null);

    const fetchHistorial = async () => {
        setLoadingHistorial(true);
        try {
            setHistorial(await getHistorialEstados(animalId));
        } catch {
            // no-op: historial is optional
        } finally {
            setLoadingHistorial(false);
        }
    };

    useEffect(() => {
        let cancelled = false;
        getHistorialEstados(animalId)
            .then(d => { if (!cancelled) setHistorial(d); })
            .catch(() => { /* no-op: historial is optional */ })
            .finally(() => { if (!cancelled) setLoadingHistorial(false); });
        return () => { cancelled = true; };
    }, [animalId]);

    const openNuevoEstadoModal = () => {
        setEditingEstado(null);
        const lastEstado = historial.length > 0 ? historial[0] : null;
        setEstadoForm({
            estado: 'VACIA',
            fechaUltimoParto: formatDateForInput(lastEstado?.fechaUltimoParto),
            fechaUltimaInseminacion: formatDateForInput(lastEstado?.fechaUltimaInseminacion),
        });
        setEstadoFormError(null);
        setEstadoSuccess(null);
        setShowEstadoModal(true);
    };

    const openEditarEstadoModal = (estado: EstadoReproductivoResponse) => {
        setEditingEstado(estado);
        setEstadoForm({
            estado: (estado.estado ?? 'VACIA') as TipoReproductivo,
            fechaUltimoParto: formatDateForInput(estado.fechaUltimoParto),
            fechaUltimaInseminacion: formatDateForInput(estado.fechaUltimaInseminacion),
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
                await editarEstadoReproductivo(animalId, editingEstado.id, estadoForm);
            } else {
                await registrarEstadoReproductivo(animalId, estadoForm);
            }
            setEstadoSuccess(editingEstado ? 'Estado actualizado correctamente.' : 'Estado registrado correctamente.');
            // Refresh en bloque separado para que un fallo no tape el éxito
            try {
                const [updatedAnimal, updatedHistorial] = await Promise.all([
                    getAnimalById(animalId),
                    getHistorialEstados(animalId),
                ]);
                onAnimalUpdated(updatedAnimal);
                setHistorial(updatedHistorial);
            } catch {
                await fetchHistorial();
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

    return (
        <>
            <LineaTiempoAlertas animalId={animalId} estados={historial} onAlertaResuelta={fetchHistorial} />

            {/* ── Sección Historial Reproductivo ── */}
            <Card className="shadow-sm border-0 animal-card">
                <Card.Body>
                    <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
                        <h4 className="section-title mb-0">Historial Reproductivo</h4>
                        {canEdit && (
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
                        <div className="table-responsive w-100" style={{ maxWidth: '100%', WebkitOverflowScrolling: 'touch' }}>
                            <table className="vigia-table" style={{ fontSize: '0.92rem' }}>
                                <thead>
                                    <tr>
                                        <th>Estado</th>
                                        <th>Último Parto</th>
                                        <th>Última Inseminación</th>
                                        <th>Origen</th>
                                        <th>Registrado por</th>
                                        <th>Fecha Registro</th>
                                        {canEdit && <th className="text-center">Acciones</th>}
                                    </tr>
                                </thead>
                                <tbody>
                                    {historial.map((h, idx) => {
                                        // Las inseminaciones provienen de `intervenciones` (id de otra tabla), no se editan acá.
                                        const editable = h.estado !== 'INSEMINADA';
                                        const rowOrigenClass = h.origen === 'AUTOMATICO' ? 'hist-row-auto' : 'hist-row-manual';
                                        return (
                                            <tr key={`${h.id}-${h.fechaRegistro}`} className={`${idx === 0 ? 'fw-semibold ' : ''}${rowOrigenClass}`}>
                                                <td>{estadoReproductivoBadge(h.estado)}</td>
                                                <td>{formatFechaLocal(h.fechaUltimoParto)}</td>
                                                <td>{formatFechaLocal(h.fechaUltimaInseminacion)}</td>
                                                <td>{origenBadge(h.origen)}</td>
                                                <td>{h.registradoPor || '—'}</td>
                                                <td>{formatDateTime(h.fechaRegistro)}</td>
                                                {canEdit && (
                                                    <td className="text-center">
                                                        {idx === 0 && editable && (
                                                            <Button variant="vigia-outline" size="sm" onClick={() => openEditarEstadoModal(h)}>
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
        </>
    );
}
