import { useEffect, useState } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { Container, Button, Spinner, Alert, Card, Row, Col, Form, Badge } from 'react-bootstrap';
import {
    getAnimalById,
    updateAnimal,
    ESTADO_SALUD_LABEL,
    type AnimalResponse,
    type AnimalRequest,
} from '../api/animalesApi';
import { getRodeoLotes, type RodeoLoteResponse } from '../api/rodeoLotesApi';
import { useAuthStore } from '../store/authStore';
import { formatBateria, formatUltimaSenial } from '../utils/caravanaTelemetria';
import './AnimalDetailPage.css';
import './AnimalesPage.css';
import ActividadTab from './ActividadTab';
import ReproduccionTab from './ReproduccionTab';
import HistorialClinicoTab from './HistorialClinicoTab';
import AnimalAlertasSanitarias from '../components/AnimalAlertasSanitarias';
import AnimalIndicadoresSalud from '../components/AnimalIndicadoresSalud';
import CohesionIndiceCard from '../components/CohesionIndiceCard';
import IntervencionFormPanel from '../components/IntervencionFormPanel';
import type { AlertaResponse } from '../api/alertaApi';
import { formatFechaLocal, hoyIso } from '../utils/fechas';

type Tab = 'actividad' | 'ficha' | 'reproduccion' | 'historial';
const TABS: Tab[] = ['actividad', 'ficha', 'reproduccion', 'historial'];

function estadoSaludBadge(estado: string | null | undefined) {
    if (!estado) return <span className="badge bg-secondary">—</span>;
    const map: Record<string, string> = {
        SANO: 'bg-success',
        EN_OBSERVACION: 'bg-warning text-dark',
        ENFERMO: 'bg-danger',
    };
    return <Badge bg="" className={`${map[estado] || 'bg-secondary'}`}>{ESTADO_SALUD_LABEL[estado] ?? estado}</Badge>;
}

function CaravanaVinculadaInfo({ animal }: { animal: AnimalResponse }) {
    const navigate = useNavigate();
    if (!animal.caravanaId) return <>—</>;
    const bateria = formatBateria(animal.caravanaBateria);
    const senial = formatUltimaSenial(animal.caravanaUltimaSenial);

    const hasBateria = bateria.text !== '—';
    const hasSenial = senial.text !== '—';

    return (
        <div className="d-flex align-items-center justify-content-between mt-1 px-3 py-2 bg-light rounded border border-light-subtle w-100">
            <div className="d-flex align-items-center gap-2">
                <span className="fw-medium text-dark text-nowrap">
                    <i className="bi bi-tag-fill text-primary me-2"></i>
                    {animal.caravanaAlias || animal.caravanaDevEui}
                </span>
                <Badge bg={animal.caravanaEstado === 'VINCULADO' ? 'success' : 'secondary'} className="fw-normal">
                    {animal.caravanaEstado}
                </Badge>
            </div>
            <div className="d-flex align-items-center gap-2">
                {(hasBateria || hasSenial) ? (
                    <>
                        {hasBateria && (
                            <span style={{ fontSize: '0.85rem', color: bateria.amber ? '#8a6a00' : '#2f7d3b', whiteSpace: 'nowrap' }} title="Batería">
                                {bateria.amber && <span className="bateria-dot d-inline-block me-1" />}
                                <i className={`bi bi-battery-${bateria.amber ? 'half' : 'full'} me-1`} />
                                {bateria.text}
                            </span>
                        )}
                        {hasSenial && (
                            <span className="text-muted" style={{ fontSize: '0.85rem', whiteSpace: 'nowrap' }} title={senial.title}>
                                <i className="bi bi-reception-4 me-1"></i>
                                {senial.text}
                            </span>
                        )}
                    </>
                ) : (
                    <span className="text-muted" style={{ fontSize: '0.8rem', fontStyle: 'italic', whiteSpace: 'nowrap' }}>
                        Sin datos
                    </span>
                )}
                <div className="border-start border-secondary-subtle ps-2 ms-0">
                    <Button variant="link" className="p-0 text-decoration-none fw-medium text-nowrap" onClick={() => navigate('/animales')} style={{ fontSize: '0.85rem' }}>
                        Gestionar
                    </Button>
                </div>
            </div>
        </div>
    );
}

export default function AnimalDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const rol = useAuthStore(state => state.rol);
    const [searchParams] = useSearchParams();

    const [animal, setAnimal] = useState<AnimalResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [isEditing, setIsEditing] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);

    const [form, setForm] = useState<Partial<AnimalRequest>>({});
    const [rodeoLotes, setRodeoLotes] = useState<RodeoLoteResponse[]>([]);

    // Tab state — 'actividad' is the default; ?tab= lets other screens deep-link a tab
    const [activeTab, setActiveTab] = useState<Tab>(() => {
        const t = searchParams.get('tab') as Tab | null;
        return t && TABS.includes(t) ? t : 'actividad';
    });

    // NUEVO: para manejar el modal de intervención desde la pestaña actividad
    const [isIntervencionOpen, setIsIntervencionOpen] = useState(false);
    const [intervencionAlerta, setIntervencionAlerta] = useState<AlertaResponse | null>(null);

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
                console.log("animal", data)

                if (rol === 'PRODUCTOR') {
                    const lotes = await getRodeoLotes(data.establecimientoId);
                    // El lote actual se mantiene seleccionable aunque esté inactivo
                    setRodeoLotes(lotes.filter(l => l.activo || l.id === data.rodeoLoteId));
                }

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
        if (!id || !form.establecimientoId || !form.estadoActual) return;
        if (!form.idCaravanaOficial?.trim()) {
            setFormError('Ingresá el identificador del animal.');
            return;
        }
        if (!form.fechaNacimiento) {
            setFormError('Ingresá la fecha de nacimiento.');
            return;
        }
        if (form.fechaNacimiento > hoyIso()) {
            setFormError('La fecha de nacimiento no puede ser futura.');
            return;
        }
        if (!form.rodeoLoteId) {
            setFormError('Seleccioná un lote.');
            return;
        }
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
                    <button
                        className={`animal-tab-btn${activeTab === 'reproduccion' ? ' active' : ''}`}
                        onClick={() => { setActiveTab('reproduccion'); setIsEditing(false); }}
                    >
                        Reproducción
                    </button>
                    <button
                        className={`animal-tab-btn${activeTab === 'historial' ? ' active' : ''}`}
                        onClick={() => { setActiveTab('historial'); setIsEditing(false); }}
                    >
                        Historial clínico
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
                                } Durante esta etapa la caravana está recolectando datos base del comportamiento del animal, por lo tanto <strong>no se generarán alertas sanitarias</strong> temporariamente.
                            </p>
                        </div>
                    </Alert>
                )}

                {/* ── Tab: Actividad ── */}
                {activeTab === 'actividad' && (
                    <div key="actividad" className="tab-content-animated">
                        <ActividadTab animalId={animal.id} establecimientoId={animal.establecimientoId} />
                        <CohesionIndiceCard animalId={animal.id} enCalibracion={false} />
                        <AnimalAlertasSanitarias animalId={animal.id} establecimientoId={animal.establecimientoId} onOpenIntervencion={(alerta) => {
                            setIntervencionAlerta(alerta);
                            setIsIntervencionOpen(true);
                        }} />
                        {isIntervencionOpen && intervencionAlerta && (
                            <IntervencionFormPanel
                                alerta={intervencionAlerta}
                                mode="fixed"
                                onClose={() => setIsIntervencionOpen(false)}
                                onSuccess={() => setIsIntervencionOpen(false)}
                            />
                        )}
                    </div>
                )}

                {/* ── Tab: Reproducción ── */}
                {activeTab === 'reproduccion' && (
                    <div key="reproduccion" className="tab-content-animated">
                        <ReproduccionTab key={animal.id} animal={animal} rol={rol} onAnimalUpdated={setAnimal} />
                    </div>
                )}

                {/* ── Tab: Historial clínico ── */}
                {activeTab === 'historial' && (
                    <div key="historial" className="tab-content-animated">
                        <HistorialClinicoTab key={animal.id} animalId={animal.id} rol={rol} onAnimalUpdated={setAnimal} />
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
                                                <Form.Label className="fw-bold">Identificador Oficial*</Form.Label>
                                                <Form.Control name="idCaravanaOficial" value={form.idCaravanaOficial ?? ''} onChange={handleChange} required />
                                            </Form.Group>
                                            <Form.Group className="mb-3">
                                                <Form.Label className="fw-bold">Nombre/Alias</Form.Label>
                                                <Form.Control name="alias" value={form.alias ?? ''} onChange={handleChange} />
                                            </Form.Group>
                                            <Form.Group className="mb-3">
                                                <Form.Label className="fw-bold">Fecha de Nacimiento*</Form.Label>
                                                <Form.Control type="date" name="fechaNacimiento" max={hoyIso()} value={form.fechaNacimiento ?? ''} onChange={handleChange} required />
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
                                                <Form.Label className="fw-bold">Rodeo/Lote*</Form.Label>
                                                <Form.Select name="rodeoLoteId" value={form.rodeoLoteId ?? ''} onChange={handleChange} required>
                                                    <option value="">Seleccioná una opción</option>
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
                                                <li className="flex-column align-items-stretch border-bottom-0 pb-0 mt-3">
                                                    <div className="w-100 mb-1"><strong>Caravana vinculada:</strong></div>
                                                    <div className="w-100"><CaravanaVinculadaInfo animal={animal} /></div>
                                                </li>
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
                                                <li><strong>Estado de Salud:</strong> {estadoSaludBadge(animal.estadoSalud)}</li>
                                                <li className="flex-column align-items-stretch border-bottom-0 pb-0 mt-3">
                                                    <div className="w-100 mb-1"><strong>Caravana vinculada:</strong></div>
                                                    <div className="w-100"><CaravanaVinculadaInfo animal={animal} /></div>
                                                </li>
                                            </ul>
                                        </Col>
                                    </Row>
                                </>
                            )}
                        </Card.Body>
                    </Card>

                    {/* ── Indicadores de Salud ── */}
                    <AnimalIndicadoresSalud animalId={animal.id} />

                </div>)}
            </Container>
        </div>
    );
}
