import { useState, useEffect } from 'react';
import { Container, Spinner, Alert, Button, Form, Modal } from 'react-bootstrap';
import { crearCaravana, getCaravanas } from '../api/caravanasApi';
import type { CaravanaRequest, CaravanaResponse } from '../api/caravanasApi';
import { getEstablecimientos } from '../api/establecimientosApi';
import type { EstablecimientoResponse } from '../api/establecimientosApi';
import './CaravanasPage.css';

function estadoBadgeClass(estado: string) {
    if (estado === 'DISPONIBLE') return 'estado-caravana-disponible';
    if (estado === 'VINCULADO') return 'estado-caravana-vinculado';
    return 'estado-caravana-inactivo';
}

function formatFecha(iso: string | null) {
    if (!iso) return '—';
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
}

export default function CaravanasPage() {
    const [establecimientos, setEstablecimientos] = useState<EstablecimientoResponse[]>([]);
    const [establecimientoId, setEstablecimientoId] = useState<number | null>(null);

    const [caravanas, setCaravanas] = useState<CaravanaResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [searchTerm, setSearchTerm] = useState('');

    const [showModal, setShowModal] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);
    const [formSuccess, setFormSuccess] = useState<string | null>(null);
    const [form, setForm] = useState<Partial<CaravanaRequest>>({ dev_eui: '', idCaravanaOficial: '', alias: '' });

    useEffect(() => {
        (async () => {
            try {
                const data = await getEstablecimientos();
                const activos = data.filter(e => e.activo);
                setEstablecimientos(activos);
                if (activos.length > 0) {
                    setEstablecimientoId(activos[0].id);
                } else {
                    setLoading(false);
                }
            } catch (err) {
                const e = err as { response?: { data?: { message?: string } } };
                setError(e.response?.data?.message || 'Error al cargar establecimientos.');
                setLoading(false);
            }
        })();
    }, []);

    useEffect(() => {
        if (!establecimientoId) return;
        (async () => {
            try {
                setLoading(true);
                setError(null);
                const data = await getCaravanas(establecimientoId);
                setCaravanas(data);
            } catch (err) {
                const e = err as { response?: { data?: { message?: string } } };
                setError(e.response?.data?.message || 'Error al cargar las caravanas.');
            } finally {
                setLoading(false);
            }
        })();
    }, [establecimientoId]);

    const openModal = () => {
        setForm({ dev_eui: '', idCaravanaOficial: '', alias: '' });
        setFormError(null);
        setFormSuccess(null);
        setShowModal(true);
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        const { name, value } = e.target;
        setForm(prev => ({ ...prev, [name]: value || null }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!establecimientoId) {
            setFormError('No hay establecimiento seleccionado.');
            return;
        }
        if (!form.dev_eui) {
            setFormError('El Dev EUI es obligatorio.');
            return;
        }
        setFormError(null);
        setFormSuccess(null);
        setSubmitting(true);
        try {
            const nuevo = await crearCaravana({ ...form, dev_eui: form.dev_eui, establecimientoId } as CaravanaRequest);
            setCaravanas(prev => [nuevo, ...prev]);
            setFormSuccess('Caravana registrada exitosamente.');
            setTimeout(() => {
                setShowModal(false);
                setFormSuccess(null);
            }, 1200);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormError(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al registrar la caravana.');
        } finally {
            setSubmitting(false);
        }
    };

    const filteredCaravanas = caravanas.filter(c => {
        if (!searchTerm) return true;
        const q = searchTerm.toLowerCase();
        return (
            (c.codigo?.toLowerCase().includes(q) ?? false) ||
            (c.alias?.toLowerCase().includes(q) ?? false) ||
            c.devEui.toLowerCase().includes(q)
        );
    });

    return (
        <div className="vigia-page-container">
            <Container>
                <div className="vigia-page-header">
                    <h2 className="vigia-page-title">Caravanas</h2>
                    <Button className="btn-vigia-success text-nowrap" onClick={openModal}>
                        <i className="bi bi-plus-lg me-2" /> Nueva caravana
                    </Button>
                </div>

                <div className="d-flex flex-column flex-md-row justify-content-between mb-4 gap-3">
                    <Form.Control
                        type="text"
                        placeholder="Buscar por código, alias o Dev EUI..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        style={{ maxWidth: '350px' }}
                    />
                    <Form.Select
                        value={establecimientoId ?? ''}
                        onChange={(e) => setEstablecimientoId(Number(e.target.value))}
                        style={{ maxWidth: '280px' }}
                    >
                        {establecimientos.map(e => (
                            <option key={e.id} value={e.id}>{e.nombre}</option>
                        ))}
                    </Form.Select>
                </div>

                {error && <Alert variant="danger">{error}</Alert>}

                {loading ? (
                    <div className="text-center mt-5">
                        <Spinner animation="border" style={{ color: '#1E3A5F' }} />
                    </div>
                ) : (
                    <div className="vigia-table-wrapper">
                        <table className="vigia-table">
                            <thead>
                                <tr>
                                    <th>Código</th>
                                    <th>Alias</th>
                                    <th className="col-mobile-hide">Dev EUI</th>
                                    <th className="text-center">Estado</th>
                                    <th className="col-mobile-hide">Fecha de alta</th>
                                    <th>Animal vinculado</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredCaravanas.length === 0 ? (
                                    <tr>
                                        <td colSpan={6} className="text-center py-4 text-muted">
                                            No se encontraron caravanas para este establecimiento.
                                        </td>
                                    </tr>
                                ) : (
                                    filteredCaravanas.map(c => (
                                        <tr key={c.id}>
                                            <td className="fw-medium">{c.codigo || '—'}</td>
                                            <td>{c.alias || '—'}</td>
                                            <td className="col-mobile-hide">{c.devEui}</td>
                                            <td className="text-center">
                                                <span className={`estado-badge-caravana ${estadoBadgeClass(c.estadoSensor)}`}>{c.estadoSensor}</span>
                                            </td>
                                            <td className="col-mobile-hide">{formatFecha(c.fechaAlta)}</td>
                                            <td>{c.animalAlias || '—'}</td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                )}
            </Container>

            <Modal show={showModal} onHide={() => setShowModal(false)} centered dialogClassName="modal-nuevo-animal">
                <Modal.Header closeButton>
                    <Modal.Title>Nueva Caravana</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {formError && <Alert variant="danger">{formError}</Alert>}
                    {formSuccess && <Alert variant="success">{formSuccess}</Alert>}
                    <Form onSubmit={handleSubmit} id="form-nueva-caravana">
                        <Form.Group className="mb-3">
                            <Form.Label>Dev EUI*</Form.Label>
                            <Form.Control name="dev_eui" type="text" placeholder="Ej: 0004A30B00C1F5E2" value={form.dev_eui ?? ''} onChange={handleChange} required />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Código</Form.Label>
                            <Form.Control name="idCaravanaOficial" type="text" placeholder="Ej: CAR-0145 (opcional)" value={form.idCaravanaOficial ?? ''} onChange={handleChange} />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Alias</Form.Label>
                            <Form.Control name="alias" type="text" placeholder="Opcional" value={form.alias ?? ''} onChange={handleChange} />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Establecimiento*</Form.Label>
                            <Form.Select
                                value={establecimientoId ?? ''}
                                onChange={(e) => setEstablecimientoId(Number(e.target.value))}
                            >
                                {establecimientos.map(e => (
                                    <option key={e.id} value={e.id}>{e.nombre}</option>
                                ))}
                            </Form.Select>
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button type="submit" form="form-nueva-caravana" disabled={submitting} className="btn-guardar-animal">
                        {submitting ? <Spinner as="span" animation="border" size="sm" /> : 'Guardar'}
                    </Button>
                </Modal.Footer>
            </Modal>
        </div>
    );
}
