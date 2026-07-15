import { useState, useEffect } from 'react';
import { Container, Spinner, Alert, Modal, Form, Button } from 'react-bootstrap';
import { crearAnimal, getAnimales } from '../api/animalesApi';
import type { AnimalResponse, AnimalRequest } from '../api/animalesApi';
import { getEstablecimientos } from '../api/establecimientosApi';
import { getRodeoLotes } from '../api/rodeoLotesApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import './AnimalesPage.css';
import './EstablecimientosPage.css';

function formatFecha(iso: string | null) {
    if (!iso) return '—';
    const [y, m] = iso.split('-');
    return `${m}/${y}`;
}

function estadoClass(e: string) {
    if (e === 'ACTIVO') return 'badge-activo';
    if (e === 'CON_ALERTA') return 'badge-con-alerta';
    return 'badge-inactivo';
}

export default function AnimalesPage() {
    const [animales, setAnimales] = useState<AnimalResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [estId, setEstId] = useState<number | null>(null);

    const [filterCelo, setFilterCelo] = useState(false);
    const [filterSalud, setFilterSalud] = useState(false);
    const [searchTerm, setSearchTerm] = useState('');

    const [showModal, setShowModal] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);
    const [form, setForm] = useState<Partial<AnimalRequest>>({ idCaravanaOficial: '', alias: '', fechaNacimiento: '', raza: '', rodeoLoteId: null, estadoActual: 'ACTIVO' });
    const [rodeoLotes, setRodeoLotes] = useState<RodeoLoteResponse[]>([]);

    useEffect(() => {
        (async () => {
            try {
                const establecimientos = await getEstablecimientos();
                if (!establecimientos.length) { setError('No tenés establecimientos asociados.'); return; }
                const id = establecimientos[0].id;
                setEstId(id);
                const animales = await getAnimales();
                setAnimales(animales);
            } catch (err) {
                const e = err as { response?: { data?: { message?: string } } };
                setError(e.response?.data?.message || 'Error al cargar animales.');
            } finally {
                setLoading(false);
            }
        })();
    }, []);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        const { name, value } = e.target;
        setForm(prev => ({ ...prev, [name]: value || null }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!estId) return;
        setFormError(null);
        setSubmitting(true);
        try {
            const nuevo = await crearAnimal({ ...form, establecimientoId: estId } as AnimalRequest);
            setAnimales(prev => [nuevo, ...prev]);
            setShowModal(false);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormError(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al crear el animal.');
        } finally {
            setSubmitting(false);
        }
    };

    const filteredAnimales = animales.filter(a => {
        if (!searchTerm) return true;
        const q = searchTerm.toLowerCase();
        return (
            String(a.id).includes(q) ||
            (a.alias?.toLowerCase().includes(q) ?? false) ||
            (a.rodeoLoteId !== null && String(a.rodeoLoteId).includes(q))
        );
    });

    const openModal = async () => {
        setForm({ idCaravanaOficial: '', alias: '', fechaNacimiento: '', raza: '', rodeoLoteId: null, estadoActual: 'ACTIVO' });
        setFormError(null);
        try {
            const lotes = await getRodeoLotes();
            setRodeoLotes(lotes.filter(l => l.activo));
        } catch { setRodeoLotes([]); }
        setShowModal(true);
    };

    return (
        <div className="animales-container">
            <Container>
                <div className="animales-header">
                    <div className="animales-header-left">
                        <h2 className="animales-title mb-0">Animales</h2>
                        <button className={`btn-filter${filterCelo ? ' active' : ''}`} onClick={() => setFilterCelo(f => !f)}>
                            <i className="bi bi-search" /> Celo
                        </button>
                        <button className={`btn-filter${filterSalud ? ' active' : ''}`} onClick={() => setFilterSalud(f => !f)}>
                            <i className="bi bi-heart" /> Salud
                        </button>
                    </div>
                    <div className="animales-header-right">
                        <Button className="btn-vigia-primary" onClick={() => { }}>
                            <i className="bi bi-upload" /> Cargar CSV
                        </Button>
                        <Button className="btn-vigia-primary" onClick={openModal}>
                            <i className="bi bi-plus-lg" /> Nuevo
                        </Button>
                    </div>
                </div>

                <div className="mb-3 mt-3">
                    <Form.Control
                        type="text"
                        placeholder="Buscar por identificador, nombre o lote..."
                        value={searchTerm}
                        onChange={e => setSearchTerm(e.target.value)}
                        style={{ maxWidth: '400px' }}
                    />
                </div>

                {error && <Alert variant="danger" className="mt-3">{error}</Alert>}

                {
                    loading ? (
                        <div className="text-center mt-5">
                            <Spinner animation="border" style={{ color: '#1E3A5F' }} />
                        </div>
                    ) : (
                        <div className="animales-table-wrapper">
                            <table className="animales-table">
                                <thead>
                                    <tr>
                                        <th>Identificador</th>
                                        <th>Nombre</th>
                                        <th>Fecha Nacimiento</th>
                                        <th>Raza</th>
                                        <th>Rodeo</th>
                                        <th>Estado</th>
                                    </tr >
                                </thead >
                                <tbody>
                                    {filteredAnimales.length === 0 ? (
                                        <tr>
                                            <td colSpan={6} className="text-center py-4 text-muted">
                                                No se encontraron animales.
                                            </td>
                                        </tr >
                                    ) : (
                                        filteredAnimales.map(a => (
                                            <tr key={a.id}>
                                                <td className="fw-medium">{a.idCaravanaOficial || '—'}</td>
                                                <td>{a.alias || '—'}</td>
                                                <td>{formatFecha(a.fechaNacimiento)}</td>
                                                <td>{a.raza || '—'}</td>
                                                <td>{a.rodeoLoteId ? `#${String(a.rodeoLoteId).padStart(2, '0')}` : '—'}</td>
                                                <td><span className={`estado-badge ${estadoClass(a.estadoActual)}`}>{a.estadoActual}</span></td>
                                            </tr>
                                        ))
                                    )}
                                </tbody >
                            </table >
                        </div >
                    )
                }
            </Container >

            <Modal show={showModal} onHide={() => setShowModal(false)} centered dialogClassName="modal-nuevo-animal">
                <Modal.Header closeButton>
                    <Modal.Title>Nuevo Animal</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {formError && <Alert variant="danger">{formError}</Alert>}
                    <Form onSubmit={handleSubmit} id="form-nuevo-animal">
                        <Form.Group className="mb-3">
                            <Form.Label>Identificador*</Form.Label>
                            <Form.Control name="idCaravanaOficial" type="text" placeholder="Ej: #F4829" value={form.idCaravanaOficial ?? ''} onChange={handleChange} required />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Nombre</Form.Label>
                            <Form.Control name="alias" type="text" placeholder="Ej: Lola" value={form.alias ?? ''} onChange={handleChange} />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Fecha de Nacimiento*</Form.Label>
                            <Form.Control name="fechaNacimiento" type="date" value={form.fechaNacimiento ?? ''} onChange={handleChange} required />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Raza</Form.Label>
                            <Form.Control name="raza" type="text" placeholder="Ej: Holstein" value={form.raza ?? ''} onChange={handleChange} />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>Lote</Form.Label>
                            <Form.Select
                                name="rodeoLoteId"
                                value={form.rodeoLoteId ?? ''}
                                onChange={handleChange}
                            >
                                <option value="">Seleccioná una opción</option>
                                {rodeoLotes.map(l => (
                                    <option key={l.id} value={l.id}>{l.nombre}</option>
                                ))}
                            </Form.Select>
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button type="submit" form="form-nuevo-animal" disabled={submitting} className="btn-guardar-animal">
                        {submitting ? <Spinner as="span" animation="border" size="sm" /> : 'Guardar'}
                    </Button>
                </Modal.Footer>
            </Modal>
        </div >
    );
}
