import { useState, useEffect } from 'react';
import { Container, Spinner, Alert, Button, Form, Modal } from 'react-bootstrap';
import { crearCaravana, getCaravanas, darDeBajaCaravana, reactivarCaravana } from '../api/caravanasApi';
import type { CaravanaRequest, CaravanaResponse, EstadoSensor } from '../api/caravanasApi';
import { getEstablecimientos } from '../api/establecimientosApi';
import type { EstablecimientoResponse } from '../api/establecimientosApi';
import { ESTADO_SENSOR_LABEL, formatBateria, formatUltimaSenial } from '../utils/caravanaTelemetria';
import './CaravanasPage.css';

function estadoBadgeClass(estado: string) {
    if (estado === 'DISPONIBLE') return 'estado-caravana-disponible';
    if (estado === 'VINCULADO') return 'estado-caravana-vinculado';
    return 'estado-caravana-inactivo';
}

export default function CaravanasPage() {
    const [establecimientos, setEstablecimientos] = useState<EstablecimientoResponse[]>([]);
    const [establecimientoId, setEstablecimientoId] = useState<number | null>(null);
    const [verTodosLosEstablecimientos, setVerTodosLosEstablecimientos] = useState(false);

    const [caravanas, setCaravanas] = useState<CaravanaResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [searchTerm, setSearchTerm] = useState('');
    const [codigoFiltro, setCodigoFiltro] = useState('');
    const [estadoFiltro, setEstadoFiltro] = useState<EstadoSensor | ''>('');

    const [showModal, setShowModal] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);
    const [formSuccess, setFormSuccess] = useState<string | null>(null);
    const [form, setForm] = useState<Partial<CaravanaRequest>>({ dev_eui: '', idCaravanaOficial: '', alias: '' });

    const [bajaTarget, setBajaTarget] = useState<CaravanaResponse | null>(null);
    const [reactivarTarget, setReactivarTarget] = useState<CaravanaResponse | null>(null);
    const [accionSubmitting, setAccionSubmitting] = useState(false);
    const [accionError, setAccionError] = useState<string | null>(null);

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

    // Debounce del buscador por código antes de pegarle al server.
    useEffect(() => {
        const timeout = setTimeout(() => setCodigoFiltro(searchTerm.trim()), 350);
        return () => clearTimeout(timeout);
    }, [searchTerm]);

    useEffect(() => {
        if (!verTodosLosEstablecimientos && !establecimientoId) return;
        (async () => {
            try {
                setLoading(true);
                setError(null);
                const data = await getCaravanas(
                    verTodosLosEstablecimientos ? null : establecimientoId,
                    estadoFiltro || undefined,
                    codigoFiltro || undefined,
                );
                setCaravanas(data);
            } catch (err) {
                const e = err as { response?: { data?: { message?: string } } };
                setError(e.response?.data?.message || 'Error al cargar las caravanas.');
            } finally {
                setLoading(false);
            }
        })();
    }, [establecimientoId, verTodosLosEstablecimientos, estadoFiltro, codigoFiltro]);

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

    const handleConfirmBaja = async () => {
        if (!bajaTarget) return;
        setAccionSubmitting(true);
        setAccionError(null);
        try {
            const actualizada = await darDeBajaCaravana(bajaTarget.id);
            setCaravanas(prev => prev.map(c => c.id === actualizada.id ? actualizada : c));
            setBajaTarget(null);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setAccionError(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al dar de baja la caravana.');
        } finally {
            setAccionSubmitting(false);
        }
    };

    const handleConfirmReactivar = async () => {
        if (!reactivarTarget) return;
        setAccionSubmitting(true);
        setAccionError(null);
        try {
            const actualizada = await reactivarCaravana(reactivarTarget.id);
            setCaravanas(prev => prev.map(c => c.id === actualizada.id ? actualizada : c));
            setReactivarTarget(null);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setAccionError(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al reactivar la caravana.');
        } finally {
            setAccionSubmitting(false);
        }
    };

    const totalPorEstado = caravanas.reduce<Record<EstadoSensor, number>>((acc, c) => {
        acc[c.estadoSensor] = (acc[c.estadoSensor] ?? 0) + 1;
        return acc;
    }, { DISPONIBLE: 0, VINCULADO: 0, INACTIVO: 0 });

    return (
        <div className="vigia-page-container">
            <Container>
                <div className="vigia-page-header">
                    <h2 className="vigia-page-title">Caravanas</h2>
                    <Button className="btn-vigia-success text-nowrap" onClick={openModal}>
                        <i className="bi bi-plus-lg me-2" /> Nueva caravana
                    </Button>
                </div>

                <div className="d-flex flex-column flex-md-row justify-content-between mb-2 gap-3">
                    <Form.Control
                        type="text"
                        placeholder="Buscar por código..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        style={{ maxWidth: '350px' }}
                    />
                    <div className="d-flex gap-3">
                        <Form.Select
                            value={estadoFiltro}
                            onChange={(e) => setEstadoFiltro(e.target.value as EstadoSensor | '')}
                            style={{ maxWidth: '200px' }}
                        >
                            <option value="">Estado: Todos</option>
                            <option value="DISPONIBLE">LIBRE</option>
                            <option value="VINCULADO">ASIGNADA</option>
                            <option value="INACTIVO">BAJA</option>
                        </Form.Select>
                        <Form.Select
                            value={verTodosLosEstablecimientos ? 'ALL' : (establecimientoId ?? '')}
                            onChange={(e) => {
                                if (e.target.value === 'ALL') {
                                    setVerTodosLosEstablecimientos(true);
                                } else {
                                    setVerTodosLosEstablecimientos(false);
                                    setEstablecimientoId(Number(e.target.value));
                                }
                            }}
                            style={{ maxWidth: '280px' }}
                        >
                            <option value="ALL">Todos los establecimientos</option>
                            {establecimientos.map(e => (
                                <option key={e.id} value={e.id}>{e.nombre}</option>
                            ))}
                        </Form.Select>
                    </div>
                </div>

                <div className="mb-4 text-muted" style={{ fontSize: '0.85rem' }}>
                    {caravanas.length} caravanas · {totalPorEstado.DISPONIBLE} LIBRE · {totalPorEstado.VINCULADO} ASIGNADA · {totalPorEstado.INACTIVO} BAJA
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
                                    <th className="col-mobile-hide">Establecimiento</th>
                                    <th className="text-center">Estado</th>
                                    <th>Batería</th>
                                    <th className="col-mobile-hide">Última señal</th>
                                    <th>Animal vinculado</th>
                                    <th className="text-center">Acciones</th>
                                </tr>
                            </thead>
                            <tbody>
                                {caravanas.length === 0 ? (
                                    <tr>
                                        <td colSpan={8} className="text-center py-4 text-muted">
                                            No se encontraron caravanas para este establecimiento.
                                        </td>
                                    </tr>
                                ) : (
                                    caravanas.map(c => {
                                        const bateria = formatBateria(c.bateria);
                                        const senial = formatUltimaSenial(c.ultimaSenial);
                                        const establecimientoNombre = c.establecimientoNombre ?? '—';
                                        return (
                                            <tr key={c.id}>
                                                <td className="fw-medium" style={{ textDecoration: c.estadoSensor === 'INACTIVO' ? 'line-through' : 'none' }}>{c.codigo || '—'}</td>
                                                <td>{c.alias || '—'}</td>
                                                <td className="col-mobile-hide">{establecimientoNombre}</td>
                                                <td className="text-center">
                                                    <div className={`estado-badge-caravana ${estadoBadgeClass(c.estadoSensor)}`}>{ESTADO_SENSOR_LABEL[c.estadoSensor]}</div>
                                                    <div style={{ fontSize: '0.7rem', color: '#9aa5b1' }}>{c.estadoSensor}</div>
                                                </td>
                                                <td style={{ color: bateria.amber ? '#8a6a00' : undefined }}>
                                                    {bateria.amber && <span className="bateria-dot" />}
                                                    {bateria.text}
                                                </td>
                                                <td className="col-mobile-hide" title={senial.title} style={{ color: senial.amber ? '#8a6a00' : undefined }}>
                                                    {senial.text}
                                                </td>
                                                <td>{c.animalAlias || '—'}</td>
                                                <td className="text-center">
                                                    {c.estadoSensor === 'INACTIVO' ? (
                                                        <Button size="sm" className="btn-accion-caravana btn-accion-reactivar" onClick={() => { setAccionError(null); setReactivarTarget(c); }}>
                                                            Reactivar
                                                        </Button>
                                                    ) : (
                                                        <Button size="sm" className="btn-accion-caravana btn-accion-baja" onClick={() => { setAccionError(null); setBajaTarget(c); }}>
                                                            Dar de baja
                                                        </Button>
                                                    )}
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

            <Modal show={!!bajaTarget} onHide={() => setBajaTarget(null)} centered dialogClassName="modal-nuevo-animal">
                <Modal.Header closeButton>
                    <Modal.Title>Dar de baja la caravana</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {accionError && <Alert variant="danger">{accionError}</Alert>}
                    <p className="text-center mb-2" style={{ color: '#9fb6cf', fontSize: '0.85rem' }}>
                        {bajaTarget?.codigo}{bajaTarget?.animalAlias ? ` · vinculada a ${bajaTarget.animalAlias}` : ''}
                    </p>
                </Modal.Body>
                <Modal.Footer className="gap-3">
                    <Button className="btn-guardar-animal" style={{ flex: 1 }} onClick={() => setBajaTarget(null)} disabled={accionSubmitting}>
                        Cancelar
                    </Button>
                    <Button className="btn-confirmar-desvincular" style={{ flex: 1 }} onClick={handleConfirmBaja} disabled={accionSubmitting}>
                        {accionSubmitting ? <Spinner as="span" animation="border" size="sm" /> : 'Dar de baja'}
                    </Button>
                </Modal.Footer>
            </Modal>

            <Modal show={!!reactivarTarget} onHide={() => setReactivarTarget(null)} centered dialogClassName="modal-nuevo-animal">
                <Modal.Header closeButton>
                    <Modal.Title>Reactivar la caravana</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {accionError && <Alert variant="danger">{accionError}</Alert>}
                    <p className="text-center mb-2" style={{ color: '#9fb6cf', fontSize: '0.85rem' }}>{reactivarTarget?.codigo}</p>
                    <div className="modal-nota-baja mb-0">
                        La caravana vuelve a <strong>LIBRE</strong> y queda lista para vincular. <strong>No</strong> se restaura el animal que tenía antes de la baja: eso se hace de nuevo desde Animales.
                    </div>
                </Modal.Body>
                <Modal.Footer className="gap-3">
                    <Button className="btn-guardar-animal" style={{ flex: 1 }} onClick={() => setReactivarTarget(null)} disabled={accionSubmitting}>
                        Cancelar
                    </Button>
                    <Button className="btn-confirmar-reactivar" style={{ flex: 1 }} onClick={handleConfirmReactivar} disabled={accionSubmitting}>
                        {accionSubmitting ? <Spinner as="span" animation="border" size="sm" /> : 'Reactivar'}
                    </Button>
                </Modal.Footer>
            </Modal>
        </div>
    );
}
