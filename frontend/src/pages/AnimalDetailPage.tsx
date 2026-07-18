import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Container, Button, Spinner, Alert, Card, Row, Col, Form } from 'react-bootstrap';
import { getAnimalById, updateAnimal, type AnimalResponse, type AnimalRequest } from '../api/animalesApi';
import { getEstablecimientos, type EstablecimientoResponse } from '../api/establecimientosApi';
import { getRodeoLotes, type RodeoLoteResponse } from '../api/rodeoLotesApi';
import { useAuthStore } from '../store/authStore';
import './AnimalDetailPage.css';

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
    const [establecimientos, setEstablecimientos] = useState<EstablecimientoResponse[]>([]);
    const [rodeoLotes, setRodeoLotes] = useState<RodeoLoteResponse[]>([]);

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
                    const est = await getEstablecimientos();
                    setEstablecimientos(est);
                    const lotes = await getRodeoLotes();
                    setRodeoLotes(lotes.filter(l => l.activo));
                }
            } catch (err) {
                const e = err as { response?: { data?: { message?: string } } };
                setError(e.response?.data?.message || 'Error al cargar la ficha del animal.');
            } finally {
                setLoading(false);
            }
        })();
    }, [id, rol]);

    const formatFecha = (iso: string | null | undefined) => {
        if (!iso) return '—';
        const [y, m, d] = iso.split('-');
        if (d) {
            return `${d.substring(0, 2)}/${m}/${y}`;
        }
        return `${m}/${y}`;
    };

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
                <div className="vigia-page-header mb-4">
                    <h2 className="vigia-page-title text-nowrap">Ficha del Animal: {animal.alias || animal.idCaravanaOficial || `#${animal.id}`}</h2>
                    <div className="d-flex align-items-center gap-2">
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

            <Card className="shadow-sm border-0 animal-card">
                <Card.Body>
                    {isEditing ? (
                        <Form>
                            <Row className="g-4">
                                <Col md={6}>
                                    <h4 className="section-title">Información General</h4>
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
                                    <Form.Group className="mb-3">
                                        <Form.Label className="fw-bold">Estado Actual</Form.Label>
                                        <div>
                                            <span className={`badge ${animal.estadoActual === 'ACTIVO' ? 'bg-success' : animal.estadoActual === 'CON_ALERTA' ? 'bg-warning text-dark' : 'bg-secondary'}`}>
                                                {animal.estadoActual}
                                            </span>
                                        </div>
                                    </Form.Group>
                                </Col>
                                <Col md={6}>
                                    <h4 className="section-title">Ubicación y Reproducción</h4>
                                    <Form.Group className="mb-3">
                                        <Form.Label className="fw-bold">Establecimiento</Form.Label>
                                        <Form.Select name="establecimientoId" value={form.establecimientoId ?? ''} onChange={handleChange}>
                                            <option value="">Seleccioná un establecimiento</option>
                                            {establecimientos.map(e => (
                                                <option key={e.id} value={e.id}>{e.nombre}</option>
                                            ))}
                                        </Form.Select>
                                    </Form.Group>
                                    <Form.Group className="mb-3">
                                        <Form.Label className="fw-bold">Rodeo/Lote</Form.Label>
                                        <Form.Select name="rodeoLoteId" value={form.rodeoLoteId ?? ''} onChange={handleChange}>
                                            <option value="">Sin rodeo</option>
                                            {rodeoLotes.filter(l => l.establecimientoId === Number(form.establecimientoId)).map(l => (
                                                <option key={l.id} value={l.id}>{l.nombre}</option>
                                            ))}
                                        </Form.Select>
                                    </Form.Group>
                                    <ul className="list-unstyled detail-list mt-4">
                                        <li><strong>Estado Reproductivo:</strong> <span className={`badge ${animal.estadoReproductivo === 'GESTANTE' ? 'bg-success' : animal.estadoReproductivo === 'CELO' ? 'bg-danger' : animal.estadoReproductivo === 'VACIA' ? 'bg-secondary' : 'bg-info'}`}>{animal.estadoReproductivo || '—'}</span></li>
                                        <li><strong>Último Parto:</strong> {formatFecha(animal.fechaUltimoParto)}</li>
                                        <li><strong>Última Inseminación:</strong> {formatFecha(animal.fechaUltimaInseminacion)}</li>
                                        <li><strong>Fecha Alta en VigIA:</strong> {formatFecha(animal.fechaAlta)}</li>
                                    </ul>
                                </Col>
                            </Row>
                        </Form>
                    ) : (
                        <Row className="g-4">
                            <Col md={6}>
                                <h4 className="section-title">Información General</h4>
                                <ul className="list-unstyled detail-list">
                                    <li><strong>Identificador Oficial:</strong> {animal.idCaravanaOficial || '—'}</li>
                                    <li><strong>Nombre/Alias:</strong> {animal.alias || '—'}</li>
                                    <li><strong>Fecha de Nacimiento:</strong> {formatFecha(animal.fechaNacimiento)}</li>
                                    <li><strong>Raza:</strong> {animal.raza || '—'}</li>
                                    <li><strong>Estado Actual:</strong> <span className={`badge ${animal.estadoActual === 'ACTIVO' ? 'bg-success' : animal.estadoActual === 'CON_ALERTA' ? 'bg-warning text-dark' : 'bg-secondary'}`}>{animal.estadoActual}</span></li>
                                </ul>
                            </Col>
                            <Col md={6}>
                                <h4 className="section-title">Ubicación y Reproducción</h4>
                                <ul className="list-unstyled detail-list">
                                    <li><strong>Establecimiento:</strong> #{animal.establecimientoId}</li>
                                    <li><strong>Rodeo/Lote:</strong> {animal.rodeoLoteId ? `#${String(animal.rodeoLoteId).padStart(2, '0')}` : '—'}</li>
                                    <li><strong>Estado Reproductivo:</strong> <span className={`badge ${animal.estadoReproductivo === 'GESTANTE' ? 'bg-success' : animal.estadoReproductivo === 'CELO' ? 'bg-danger' : animal.estadoReproductivo === 'VACIA' ? 'bg-secondary' : 'bg-info'}`}>{animal.estadoReproductivo || '—'}</span></li>
                                    <li><strong>Último Parto:</strong> {formatFecha(animal.fechaUltimoParto)}</li>
                                    <li><strong>Última Inseminación:</strong> {formatFecha(animal.fechaUltimaInseminacion)}</li>
                                    <li><strong>Fecha Alta en VigIA:</strong> {formatFecha(animal.fechaAlta)}</li>
                                </ul>
                            </Col>
                        </Row>
                    )}
                </Card.Body>
            </Card>
        </Container>
        </div>
    );
}
