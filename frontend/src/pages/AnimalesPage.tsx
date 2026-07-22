import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Container, Spinner, Alert, Modal, Form, Button } from 'react-bootstrap';
import { cargarAnimalesExcel, cargarEstadosExcel, crearAnimal, getAnimales, desactivarAnimal } from '../api/animalesApi';
import type { AnimalResponse, AnimalRequest, CargaMasivaResponse, CargaMasivaEstadoResponse } from '../api/animalesApi';
import { getRodeoLotes } from '../api/rodeoLotesApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import './AnimalesPage.css';
import './EstablecimientosPage.css';
import { crearRodeo, type RodeoRequest } from '../api/rodeoApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';

function formatFecha(iso: string | null) {
    if (!iso) return '—';
    const [y, m, d] = iso.split('-');
    if (d) {
        return `${d.substring(0, 2)}/${m}/${y}`;
    }
    return `${m}/${y}`;
}

function estadoClass(e: string) {
    if (e === 'ACTIVO') return 'badge-activo';
    if (e === 'CON_ALERTA') return 'badge-con-alerta';
    return 'badge-inactivo';
}

export default function AnimalesPage() {
    const navigate = useNavigate();
    const selectedId = useEstablecimientoStore((s) => s.selectedId);
    const [animales, setAnimales] = useState<AnimalResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [activeTab, setActiveTab] = useState<'general' | 'celo' | 'salud'>('general');
    const [searchTerm, setSearchTerm] = useState('');

    const [showModal, setShowModal] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);
    const [formSuccess, setFormSuccess] = useState<string | null>(null);
    const [form, setForm] = useState<Partial<AnimalRequest>>({ idCaravanaOficial: '', alias: '', fechaNacimiento: '', raza: '', rodeoLoteId: null, estadoActual: 'ACTIVO' });
    const [rodeoLotes, setRodeoLotes] = useState<RodeoLoteResponse[]>([]);

    const [createRodeoModal, setCreateRodeoModal] = useState(false);
    const [submittingRodeo, setSubmittingRodeo] = useState(false);
    const [formErrorRodeo, setFormErrorRodeo] = useState<string | null>(null);
    const [formRodeo, setFormRodeo] = useState<Partial<RodeoRequest>>({ nombre: '' });

    const [uploadModal, setUploadModal] = useState(false);
    const [submittingUpload, setSubmittingUpload] = useState(false);
    const [formErrorUpload, setFormErrorUpload] = useState<string | null>(null);
    const [fileUpload, setFileUpload] = useState<File | null>(null);
    const [reporteCarga, setReporteCarga] = useState<CargaMasivaResponse | null>(null);

    const [uploadEstadosModal, setUploadEstadosModal] = useState(false);
    const [submittingUploadEstados, setSubmittingUploadEstados] = useState(false);
    const [formErrorUploadEstados, setFormErrorUploadEstados] = useState<string | null>(null);
    const [fileUploadEstados, setFileUploadEstados] = useState<File | null>(null);
    const [reporteCargaEstados, setReporteCargaEstados] = useState<CargaMasivaEstadoResponse | null>(null);

    const [sortField, setSortField] = useState<string | null>(null);
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

    const fetchAnimales = async () => {
        try {
            const data = await getAnimales(selectedId || undefined);
            setAnimales(data);
        } catch (err) {
            console.error(err);
        }
    };

    const handleDarDeBaja = async (id: number) => {
        if (!window.confirm("¿Estás seguro de que querés dar de baja a este animal?")) return;
        try {
            await desactivarAnimal(id);
            setAnimales(prev => prev.filter(a => a.id !== id));
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } } };
            alert(e.response?.data?.message || 'Error al dar de baja el animal.');
        }
    };

    useEffect(() => {
        (async () => {
            try {
                if (!selectedId) { setError('No tenés establecimientos asociados.'); return; }
                const animales = await getAnimales(selectedId);
                setAnimales(animales);
            } catch (err) {
                const e = err as { response?: { data?: { message?: string } } };
                setError(e.response?.data?.message || 'Error al cargar animales.');
            } finally {
                setLoading(false);
            }
        })();
    }, [selectedId]);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        const { name, value } = e.target;
        setForm(prev => ({ ...prev, [name]: value || null }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedId) {
            setFormError('No hay establecimiento seleccionado.');
            return;
        }
        if (!form.idCaravanaOficial && !form.alias) {
            setFormError('Ingresá al menos el identificador o el nombre del animal.');
            return;
        }
        setFormError(null);
        setFormSuccess(null);
        setSubmitting(true);
        try {
            const nuevo = await crearAnimal({ ...form, establecimientoId: selectedId } as AnimalRequest);
            setAnimales(prev => [nuevo, ...prev]);
            setFormSuccess('Animal registrado exitosamente.');
            setTimeout(() => {
                setShowModal(false);
                setFormSuccess(null);
            }, 1200);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormError(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al crear el animal.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleChangeRodeo = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        const { name, value } = e.target;
        setFormRodeo(prev => ({ ...prev, [name]: value || null }));
    };

    const handleSubmitRodeo = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedId) return;
        setFormErrorRodeo(null);
        setSubmittingRodeo(true);
        try {
            const nuevo = await crearRodeo({ ...formRodeo, establecimientoId: selectedId } as RodeoRequest);
            setRodeoLotes(prev => [nuevo, ...prev]);
            setCreateRodeoModal(false);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormErrorRodeo(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al crear el rodeo.');
        } finally {
            setSubmittingRodeo(false);
        }
    };

    const handleChangeUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        setFileUpload(file ?? null);
    };

    const handleSubmitUpload = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedId) {
            setFormErrorUpload('No hay establecimiento seleccionado.');
            return;
        }
        setFormErrorUpload(null);
        setSubmittingUpload(true);
        try {
            const result = await cargarAnimalesExcel(fileUpload as File, selectedId);
            setReporteCarga(result);
            setAnimales(prev => [...prev, ...result.animales]);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormErrorUpload(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al cargar los animales.');
        } finally {
            setSubmittingUpload(false);
        }
    };

    const handleCloseUploadModal = () => {
        setUploadModal(false);
        setReporteCarga(null);
        setFileUpload(null);
        setFormErrorUpload(null);
    };

    const handleChangeUploadEstados = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        setFileUploadEstados(file ?? null);
    };

    const handleSubmitUploadEstados = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedId) {
            setFormErrorUploadEstados('No hay establecimiento seleccionado.');
            return;
        }
        if (!fileUploadEstados) return;
        setFormErrorUploadEstados(null);
        setSubmittingUploadEstados(true);
        try {
            const result = await cargarEstadosExcel(fileUploadEstados, selectedId);
            setReporteCargaEstados(result);
            await fetchAnimales();
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormErrorUploadEstados(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al cargar los estados.');
        } finally {
            setSubmittingUploadEstados(false);
        }
    };

    const handleCloseUploadEstadosModal = () => {
        setUploadEstadosModal(false);
        setReporteCargaEstados(null);
        setFileUploadEstados(null);
        setFormErrorUploadEstados(null);
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

    const filteredAnimales = animales.filter(a => {
        // Filter by selected establecimiento
        if (selectedId && a.establecimientoId !== selectedId) return false;
        if (activeTab === 'salud' && a.estadoActual !== 'CON_ALERTA') return false;
        if (!searchTerm) return true;
        const q = searchTerm.toLowerCase();
        return (
            (a.idCaravanaOficial?.toLowerCase().includes(q) ?? false) ||
            (a.alias?.toLowerCase().includes(q) ?? false) ||
            (a.rodeoLoteNombre?.toLowerCase().includes(q) ?? false) ||
            (a.estadoActual?.toLowerCase().includes(q) ?? false) ||
            (a.estadoReproductivo?.toLowerCase().includes(q) ?? false)
        );
    });

    const sortedAnimales = [...filteredAnimales].sort((a, b) => {
        if (!sortField) return 0;
        let valA = a[sortField as keyof AnimalResponse] as string | number | null;
        let valB = b[sortField as keyof AnimalResponse] as string | number | null;
        if (valA === null || valA === undefined) valA = '';
        if (valB === null || valB === undefined) valB = '';

        if (valA < valB) return sortDirection === 'asc' ? -1 : 1;
        if (valA > valB) return sortDirection === 'asc' ? 1 : -1;
        return 0;
    });

    const openModal = async () => {
        setForm({ idCaravanaOficial: '', alias: '', fechaNacimiento: '', raza: '', rodeoLoteId: null, estadoActual: 'ACTIVO' });
        setFormError(null);
        setFormSuccess(null);
        try {
            const lotes = await getRodeoLotes(selectedId || undefined);
            setRodeoLotes(lotes.filter(l => l.activo));
        } catch { setRodeoLotes([]); }
        setShowModal(true);
    };

    return (
        <div className="vigia-page-container">
            <Container>
                <div className="vigia-page-header">
                    <div className="d-flex align-items-center gap-2">
                        <h2 className="vigia-page-title">Animales</h2>
                        <button className={`btn-filter${activeTab === 'general' ? ' active' : ''}`} onClick={() => setActiveTab('general')}>
                            <i className="bi bi-list" /> General
                        </button>
                        <button className={`btn-filter${activeTab === 'celo' ? ' active' : ''}`} onClick={() => setActiveTab('celo')}>
                            <i className="bi bi-search" /> Celo
                        </button>
                        <button className={`btn-filter${activeTab === 'salud' ? ' active' : ''}`} onClick={() => setActiveTab('salud')}>
                            <i className="bi bi-heart" /> Salud
                        </button>
                    </div>
                    <div className="d-flex align-items-center gap-2">
                        <Button className="btn-vigia-success" onClick={() => setCreateRodeoModal(true)}>
                            <i className="bi bi-plus-lg" /> Rodeo
                        </Button>
                        <Button className="btn-vigia-success" onClick={() => { setUploadEstadosModal(true) }}>
                            <i className="bi bi-upload" /> Cargar Estados
                        </Button>
                        <Button className="btn-vigia-success" onClick={() => { setUploadModal(true) }}>
                            <i className="bi bi-upload" /> Cargar Animales
                        </Button>
                        <Button className="btn-vigia-success" onClick={openModal}>
                            <i className="bi bi-plus-lg" /> Animal
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
                        <div className="vigia-table-wrapper">
                            <table className="vigia-table">
                                <thead>
                                    <tr>
                                        <th onClick={() => handleSort('idCaravanaOficial')} style={{ cursor: 'pointer' }}>
                                            <div className="d-flex align-items-center justify-content-start">
                                                Identificador <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'idCaravanaOficial' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                            </div>
                                        </th>
                                        <th onClick={() => handleSort('alias')} style={{ cursor: 'pointer' }}>
                                            <div className="d-flex align-items-center justify-content-start">
                                                Nombre <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'alias' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                            </div>
                                        </th>
                                        {activeTab === 'celo' ? (
                                            <>
                                                <th onClick={() => handleSort('estadoReproductivo')} style={{ cursor: 'pointer' }}>
                                                    <div className="d-flex align-items-center justify-content-start">
                                                        Estado Reproductivo <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px' }}>{sortField === 'estadoReproductivo' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                    </div>
                                                </th>
                                                <th onClick={() => handleSort('fechaUltimoParto')} style={{ cursor: 'pointer' }}>
                                                    <div className="d-flex align-items-center justify-content-start">
                                                        Fecha Último Parto <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px' }}>{sortField === 'fechaUltimoParto' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                    </div>
                                                </th>
                                                <th onClick={() => handleSort('fechaUltimaInseminacion')} style={{ cursor: 'pointer' }}>
                                                    <div className="d-flex align-items-center justify-content-start">
                                                        Fecha Última Inseminación <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px' }}>{sortField === 'fechaUltimaInseminacion' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                    </div>
                                                </th>
                                            </>
                                        ) : (
                                            <>
                                                <th onClick={() => handleSort('fechaNacimiento')} style={{ cursor: 'pointer' }}>
                                                    <div className="d-flex align-items-center justify-content-start">
                                                        Fecha Nacimiento <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'fechaNacimiento' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                    </div>
                                                </th>
                                                <th onClick={() => handleSort('raza')} style={{ cursor: 'pointer' }}>
                                                    <div className="d-flex align-items-center justify-content-start">
                                                        Raza <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'raza' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                    </div>
                                                </th>
                                                <th onClick={() => handleSort('rodeoLoteNombre')} style={{ cursor: 'pointer' }}>
                                                    <div className="d-flex align-items-center justify-content-start">
                                                        Rodeo <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'rodeoLoteNombre' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                    </div>
                                                </th>
                                                <th onClick={() => handleSort('estadoActual')} style={{ cursor: 'pointer' }}>
                                                    <div className="d-flex align-items-center justify-content-center">
                                                        Estado <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'estadoActual' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                    </div>
                                                </th>
                                                <th onClick={() => handleSort('estadoReproductivo')} style={{ cursor: 'pointer' }}>
                                                    <div className="d-flex align-items-center justify-content-center">
                                                        Reproductivo <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'estadoReproductivo' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                    </div>
                                                </th>
                                            </>
                                        )}
                                        <th className="text-center">Acciones</th>
                                    </tr >
                                </thead >
                                <tbody>
                                    {sortedAnimales.length === 0 ? (
                                        <tr>
                                            <td colSpan={8} className="text-center py-4 text-muted">
                                                No se encontraron animales.
                                            </td>
                                        </tr >
                                    ) : (
                                        sortedAnimales.map(a => (
                                            <tr key={a.id} className="animal-row">
                                                <td className="fw-medium">{a.idCaravanaOficial || '—'}</td>
                                                <td>{a.alias || '—'}</td>
                                                {activeTab === 'celo' ? (
                                                    <>
                                                        <td className="text-center">
                                                            {a.estadoReproductivo ? (
                                                                <span className={`badge ${a.estadoReproductivo === 'GESTANTE' ? 'bg-success' :
                                                                    a.estadoReproductivo === 'CELO' ? 'bg-danger' :
                                                                        a.estadoReproductivo === 'VACIA' ? 'bg-secondary' : 'bg-info'
                                                                    }`}>{a.estadoReproductivo}</span>
                                                            ) : '—'}
                                                        </td>
                                                        <td>{formatFecha(a.fechaUltimoParto ?? null)}</td>
                                                        <td>{formatFecha(a.fechaUltimaInseminacion ?? null)}</td>
                                                    </>
                                                ) : (
                                                    <>
                                                        <td>{formatFecha(a.fechaNacimiento)}</td>
                                                        <td>{a.raza || '—'}</td>
                                                        <td>{a.rodeoLoteNombre || '—'}</td>
                                                        <td className="text-center"><span className={`estado-badge ${estadoClass(a.estadoActual)}`}>{a.estadoActual}</span></td>
                                                        <td className="text-center">
                                                            {a.estadoReproductivo ? (
                                                                <span className={`badge ${a.estadoReproductivo === 'GESTANTE' ? 'bg-success' :
                                                                    a.estadoReproductivo === 'CELO' ? 'bg-danger' :
                                                                        a.estadoReproductivo === 'VACIA' ? 'bg-secondary' : 'bg-info'
                                                                    }`}>{a.estadoReproductivo}</span>
                                                            ) : '—'}
                                                        </td>
                                                    </>
                                                )}
                                                <td className="text-center">
                                                    <div className="d-flex justify-content-center gap-2">
                                                        <Button variant="outline-primary" size="sm" onClick={() => navigate(`/animales/${a.id}`)}>
                                                            <i className="bi bi-eye"></i> Ver
                                                        </Button>
                                                        <Button variant="outline-danger" size="sm" onClick={() => handleDarDeBaja(a.id)}>
                                                            <i className="bi bi-trash"></i>
                                                        </Button>
                                                    </div>
                                                </td>
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
                    {formSuccess && <Alert variant="success">{formSuccess}</Alert>}
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
                            <Form.Label>Lote*</Form.Label>
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

            <Modal show={createRodeoModal} onHide={() => setCreateRodeoModal(false)} centered dialogClassName="modal-nuevo-animal">
                <Modal.Header closeButton>
                    <Modal.Title>Nuevo Rodeo</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {formErrorRodeo && <Alert variant="danger">{formErrorRodeo}</Alert>}
                    <Form onSubmit={handleSubmitRodeo} id="form-nuevo-rodeo">
                        <Form.Group className="mb-3">
                            <Form.Label>Nombre*</Form.Label>
                            <Form.Control name="nombre" type="text" placeholder="Ej: #F4829" value={formRodeo.nombre ?? ''} onChange={handleChangeRodeo} required />
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button type="submit" form="form-nuevo-rodeo" disabled={submittingRodeo} className="btn-guardar-animal">
                        {submittingRodeo ? <Spinner as="span" animation="border" size="sm" /> : 'Guardar'}
                    </Button>
                </Modal.Footer>
            </Modal>

            <Modal show={uploadModal} onHide={handleCloseUploadModal} centered dialogClassName="modal-nuevo-animal">
                <Modal.Header closeButton>
                    <Modal.Title>Cargar Animales con Excel</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {formErrorUpload && <Alert variant="danger">{formErrorUpload}</Alert>}
                    {reporteCarga ? (
                        <div>
                            <Alert variant={reporteCarga.fallidos > 0 ? "warning" : "success"}>
                                {reporteCarga.fallidos > 0 ? "Carga fallida. Se encontraron errores y no se guardó ningún registro." : "¡Carga procesada correctamente!"}
                            </Alert>
                            <ul>
                                <li><strong>Procesados:</strong> {reporteCarga.procesados}</li>
                                <li><strong>Exitosos:</strong> {reporteCarga.exitosos}</li>
                                <li><strong>Fallidos:</strong> {reporteCarga.fallidos}</li>
                            </ul>
                            {reporteCarga.errores && reporteCarga.errores.length > 0 && (
                                <div className="mt-3">
                                    <h6 className="fw-bold">Detalle de errores:</h6>
                                    <div style={{ maxHeight: '150px', overflowY: 'auto' }}>
                                        <ul className="text-danger mb-0" style={{ fontSize: '0.9em' }}>
                                            {reporteCarga.errores.map((err, i) => (
                                                <li key={i}>{err}</li>
                                            ))}
                                        </ul>
                                    </div>
                                </div>
                            )}
                        </div>
                    ) : (
                        <Form onSubmit={handleSubmitUpload} id="form-cargar-animales">
                            <div className="mb-3 text-white" style={{ fontSize: '0.9em' }}>
                                <p className="mb-1">El archivo debe contener las siguientes columnas en este orden:</p>
                                <ul>
                                    <li><strong>IdCaravana</strong> (al menos IdCaravana o Alias debe estar presente)</li>
                                    <li><strong>Alias</strong> (al menos IdCaravana o Alias debe estar presente)</li>
                                    <li><strong>FechaNacimiento</strong> (opcional, formato YYYY-MM-DD)</li>
                                    <li><strong>Raza</strong> (opcional)</li>
                                    <li><strong>RodeoLote</strong> (opcional, nombre exacto del lote/rodeo, se creará si no existe)</li>
                                </ul>
                                <div className="d-flex gap-3 mb-3">
                                    <a href="/template_animales.csv" download className="d-inline-block" style={{ color: '#8CC63F', fontWeight: '600' }}>
                                        <i className="bi bi-filetype-csv me-1" /> Descargar template CSV
                                    </a>
                                    <a href="/template_animales.xlsx" download className="d-inline-block" style={{ color: '#8CC63F', fontWeight: '600' }}>
                                        <i className="bi bi-filetype-xlsx me-1" /> Descargar template Excel
                                    </a>
                                </div>
                            </div>
                            <Form.Group controlId="formUploadFile" className="mb-3">
                                <Form.Label>Archivo*</Form.Label>
                                <Form.Control name="file" type="file" accept=".xlsx,.xls,.csv" onChange={handleChangeUpload} required />
                            </Form.Group>
                        </Form>
                    )}
                </Modal.Body>
                <Modal.Footer>
                    {reporteCarga ? (
                        <Button onClick={handleCloseUploadModal} className="btn-guardar-animal">
                            Cerrar
                        </Button>
                    ) : (
                        <Button type="submit" form="form-cargar-animales" disabled={submittingUpload} className="btn-guardar-animal">
                            {submittingUpload ? <Spinner as="span" animation="border" size="sm" /> : 'Subir Archivo'}
                        </Button>
                    )}
                </Modal.Footer>
            </Modal>
            <Modal show={uploadEstadosModal} onHide={handleCloseUploadEstadosModal} centered dialogClassName="modal-nuevo-animal">
                <Modal.Header closeButton>
                    <Modal.Title>Cargar Estados Reproductivos</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {formErrorUploadEstados && <Alert variant="danger">{formErrorUploadEstados}</Alert>}
                    {reporteCargaEstados ? (
                        <div>
                            <Alert variant={reporteCargaEstados.fallidos > 0 ? "warning" : "success"}>Carga procesada</Alert>
                            <ul>
                                <li><strong>Procesados:</strong> {reporteCargaEstados.procesados}</li>
                                <li><strong>Exitosos:</strong> {reporteCargaEstados.exitosos}</li>
                                <li><strong>Fallidos:</strong> {reporteCargaEstados.fallidos}</li>
                            </ul>
                            {reporteCargaEstados.errores && reporteCargaEstados.errores.length > 0 && (
                                <div style={{ maxHeight: '150px', overflowY: 'auto' }}>
                                    <strong>Errores:</strong>
                                    <ul className="mb-0 text-danger" style={{ fontSize: '0.9em' }}>
                                        {reporteCargaEstados.errores.map((e, idx) => (
                                            <li key={idx}>Fila {e.fila}: {e.error}</li>
                                        ))}
                                    </ul>
                                </div>
                            )}
                        </div>
                    ) : (
                        <Form onSubmit={handleSubmitUploadEstados} id="form-cargar-estados">
                            <div className="mb-3 text-white" style={{ fontSize: '0.9em' }}>
                                <p className="mb-1">El archivo debe contener las siguientes columnas en este orden:</p>
                                <ul>
                                    <li><strong>IdCaravana</strong> (al menos IdCaravana o Alias debe estar presente)</li>
                                    <li><strong>Alias</strong> (al menos IdCaravana o Alias debe estar presente)</li>
                                    <li><strong>Estado</strong> (VACIA, GESTANTE, CELO, ANESTRO)</li>
                                    <li><strong>FechaUltimoParto</strong> (opcional, formato YYYY-MM-DD)</li>
                                    <li><strong>FechaUltimaInseminacion</strong> (opcional, formato YYYY-MM-DD)</li>
                                </ul>
                                <div className="d-flex gap-3 mb-3">
                                    <a href="/template_estados.csv" download className="d-inline-block" style={{ color: '#8CC63F', fontWeight: '600' }}>
                                        <i className="bi bi-filetype-csv me-1" /> Descargar template CSV
                                    </a>
                                    <a href="/template_estados.xlsx" download className="d-inline-block" style={{ color: '#8CC63F', fontWeight: '600' }}>
                                        <i className="bi bi-filetype-xlsx me-1" /> Descargar template Excel
                                    </a>
                                </div>
                            </div>
                            <Form.Group className="mb-3">
                                <Form.Label>Archivo*</Form.Label>
                                <Form.Control name="file" type="file" accept=".xlsx,.xls,.csv" onChange={handleChangeUploadEstados} required />
                            </Form.Group>
                        </Form>
                    )}
                </Modal.Body>
                <Modal.Footer>
                    {reporteCargaEstados ? (
                        <Button onClick={handleCloseUploadEstadosModal} className="btn-guardar-animal">
                            Cerrar
                        </Button>
                    ) : (
                        <Button type="submit" form="form-cargar-estados" disabled={submittingUploadEstados} className="btn-guardar-animal">
                            {submittingUploadEstados ? <Spinner as="span" animation="border" size="sm" /> : 'Subir Archivo'}
                        </Button>
                    )}
                </Modal.Footer>
            </Modal>
        </div >
    );
}
