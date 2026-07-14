import { useState, useEffect } from 'react';
import { Container, Row, Col, Button, Modal, Form, Spinner, Alert } from 'react-bootstrap';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  getEstablecimientos,
  createEstablecimiento,
  desactivarEstablecimiento,
  updateEstablecimiento,
  activarEstablecimiento
} from '../api/establecimientosApi';
import type { EstablecimientoResponse } from '../api/establecimientosApi';
import { getRodeoLotes, crearRodeoLote } from '../api/rodeoLotesApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import './EstablecimientosPage.css';
import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet';
import { useAuthStore } from '../store/authStore';

const schema = z.object({
  nombre: z.string().min(1, 'El nombre es requerido'),
  latitud: z.number({ message: 'Debe ser un número válido' }),
  longitud: z.number({ message: 'Debe ser un número válido' }),
});

type FormData = z.infer<typeof schema>;

export default function EstablecimientosPage() {
  const rol = useAuthStore(state => state.rol);
  const [establecimientos, setEstablecimientos] = useState<EstablecimientoResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');

  const [showModal, setShowModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [showUpdateModal, setShowUpdateModal] = useState(false);
  const [submittingUpdate, setSubmittingUpdate] = useState(false);
  const [establecimientoEdit, setEstablecimientoEdit] = useState<EstablecimientoResponse | null>(null);

  const [showDetailModal, setShowDetailModal] = useState(false);
  const [establecimientoDetail, setEstablecimientoDetail] = useState<EstablecimientoResponse | null>(null);

  const [lotesDetalle, setLotesDetalle] = useState<RodeoLoteResponse[]>([]);
  const [loadingLotes, setLoadingLotes] = useState(false);
  const [nuevoLoteNombre, setNuevoLoteNombre] = useState('');
  const [creandoLote, setCreandoLote] = useState(false);
  const [errorLote, setErrorLote] = useState<string | null>(null);

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const { register: registerUpdate, handleSubmit: handleSubmitUpdate, reset: resetUpdate, formState: { errors: errorsUpdate } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  useEffect(() => {
    const fetchEstablecimientos = async () => {
      try {
        const data = await getEstablecimientos();
        setEstablecimientos(data);
      } catch (err) {
        const axiosError = err as { response?: { data?: { message?: string } } };
        setError(axiosError.response?.data?.message || 'Error al cargar establecimientos');
      } finally {
        setLoading(false);
      }
    };

    fetchEstablecimientos();
  }, []);

  const onSubmit = async (data: FormData) => {
    try {
      setSubmitting(true);
      setError(null);
      const nuevo = await createEstablecimiento(data);
      setEstablecimientos([...establecimientos, nuevo]);
      handleCloseModal();
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      setError(axiosError.response?.data?.message || 'Error al crear establecimiento');
    } finally {
      setSubmitting(false);
    }
  };

  const onUpdate = async (data: FormData) => {
    try {
      setSubmittingUpdate(true);
      setError(null);
      if (!establecimientoEdit) return;
      const actualizado = await updateEstablecimiento(establecimientoEdit.id, data);
      setEstablecimientos(establecimientos.map(e => e.id === actualizado.id ? actualizado : e));
      setShowUpdateModal(false);
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      setError(axiosError.response?.data?.message || 'Error al actualizar establecimiento');
    } finally {
      setSubmittingUpdate(false);
    }
  }

  const handleDesactivar = async (id: number) => {
    if (!window.confirm('¿Estás seguro de que deseas desactivar este establecimiento?')) return;
    try {
      const actualizado = await desactivarEstablecimiento(id);
      setEstablecimientos(establecimientos.map(e => e.id === id ? actualizado : e));
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      alert(axiosError.response?.data?.message || 'Error al desactivar establecimiento');
    }
  };

  const handleActivar = async (id: number) => {
    try {
      const actualizado = await activarEstablecimiento(id);
      setEstablecimientos(establecimientos.map(e => e.id === id ? actualizado : e));
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      alert(axiosError.response?.data?.message || 'Error al activar el establecimiento');
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    reset();
  };

  const handleCloseUpdateModal = () => {
    setShowUpdateModal(false);
    setEstablecimientoEdit(null);
    resetUpdate();
  };

  const handleShowModal = () => setShowModal(true);

  return (
    <div className="establecimientos-container">
      <Container>
        <div className="d-flex flex-column flex-md-row justify-content-between align-items-md-center mb-4 gap-3">
          <h2 className="establecimientos-title mb-0">Establecimientos</h2>
          <div className="d-flex gap-3">
            <Form.Control
              type="text"
              placeholder="Buscar por nombre o #id..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              style={{ maxWidth: '300px' }}
            />
            {rol === 'ADMINISTRADOR' && (
              <Button className="btn-vigia-primary text-nowrap" onClick={handleShowModal}>
                + Nuevo
              </Button>
            )}
          </div>
        </div>

        {error && <Alert variant="danger">{error}</Alert>}

        {loading ? (
          <div className="text-center mt-5">
            <Spinner animation="border" variant="primary" />
          </div>
        ) : (() => {
          const filtered = establecimientos.filter(est => {
            if (searchTerm === '') return true;
            const searchLower = searchTerm.toLowerCase();
            return est.nombre.toLowerCase().includes(searchLower) || `#${est.id}`.includes(searchLower);
          });
          return (
            <Row className="g-4">
              {filtered.map((est) => (
                <Col key={est.id} xs={12} md={6} lg={4}>
                  <div className="establecimiento-card">
                    <div className={`status-indicator ${est.activo ? 'status-active' : 'status-inactive'}`} title={est.activo ? 'Activo' : 'Inactivo'} />
                    <div className="card-id">#{est.id}</div>
                    <h4>{est.nombre}</h4>
                    <i
                      className="bi bi-pencil-square edit-icon"
                      onClick={() => {
                        setEstablecimientoEdit(est);
                        resetUpdate({
                          nombre: est.nombre,
                          latitud: est.latitud,
                          longitud: est.longitud,
                        });
                        setShowUpdateModal(true);
                      }}
                    ></i>
                    <div className="location">
                      <i className="bi bi-geo-alt-fill me-2"></i>
                      Lat: {est.latitud}, Lon: {est.longitud}
                    </div>

                    <div className="card-actions">
                      <Button variant="light" className="btn-vigia-primary w-100" onClick={async () => {
                        setEstablecimientoDetail(est);
                        setShowDetailModal(true);
                        setLoadingLotes(true);
                        setErrorLote(null);
                        try {
                          const allLotes = await getRodeoLotes();
                          setLotesDetalle(allLotes.filter(l => l.establecimientoId === est.id));
                        } catch {
                          setErrorLote('Error al cargar lotes');
                        } finally {
                          setLoadingLotes(false);
                        }
                      }}>
                        Ver detalle
                      </Button>
                      {est.activo ? (
                        <Button variant="light" className="btn-vigia-danger w-100" onClick={() => handleDesactivar(est.id)}>
                          Desactivar
                        </Button>
                      ) : (
                        <Button variant="light" className="btn-vigia-danger w-100" onClick={() => handleActivar(est.id)}>
                          Activar
                        </Button>
                      )}
                    </div>
                  </div>
                </Col>
              ))}
              {filtered.length === 0 && (
                <Col>
                  <p className="text-muted">No se encontraron establecimientos.</p>
                </Col>
              )}
            </Row>
          );
        })()}
      </Container>

      {/* Modal Alta */}
      <Modal show={showModal} onHide={handleCloseModal} centered>
        <Modal.Header closeButton style={{ backgroundColor: '#1E3A5F', color: 'white' }}>
          <Modal.Title>Crear establecimiento</Modal.Title>
        </Modal.Header>
        <Form onSubmit={handleSubmit(onSubmit)}>
          <Modal.Body>
            <Form.Group className="mb-3">
              <Form.Label>Nombre</Form.Label>
              <Form.Control
                type="text"
                placeholder="Ej: Tres Arroyos"
                {...register('nombre')}
                isInvalid={!!errors.nombre}
              />
              <Form.Control.Feedback type="invalid">
                {errors.nombre?.message}
              </Form.Control.Feedback>
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Latitud</Form.Label>
              <Form.Control
                type="number"
                step="any"
                placeholder="Ej: -34.6037"
                {...register('latitud', { valueAsNumber: true })}
                isInvalid={!!errors.latitud}
              />
              <Form.Control.Feedback type="invalid">
                {errors.latitud?.message}
              </Form.Control.Feedback>
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Longitud</Form.Label>
              <Form.Control
                type="number"
                step="any"
                placeholder="Ej: -58.3816"
                {...register('longitud', { valueAsNumber: true })}
                isInvalid={!!errors.longitud}
              />
              <Form.Control.Feedback type="invalid">
                {errors.longitud?.message}
              </Form.Control.Feedback>
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" onClick={handleCloseModal}>
              Cancelar
            </Button>
            <Button type="submit" className="btn-vigia-primary" disabled={submitting}>
              {submitting ? <Spinner size="sm" animation="border" /> : 'Confirmar'}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>

      {/* Modal Editar */}
      {establecimientoEdit && (
        <Modal show={showUpdateModal} onHide={handleCloseUpdateModal} centered>
          <Modal.Header closeButton style={{ backgroundColor: '#1E3A5F', color: 'white' }}>
            <Modal.Title>Actualizar establecimiento</Modal.Title>
          </Modal.Header>
          <Form onSubmit={handleSubmitUpdate(onUpdate)}>
            <Modal.Body>
              <Form.Group className="mb-3">
                <Form.Label>Nombre</Form.Label>
                <Form.Control
                  type="text"
                  placeholder="Ej: Tres Arroyos"
                  {...registerUpdate('nombre')}
                  isInvalid={!!errorsUpdate.nombre}
                />
                <Form.Control.Feedback type="invalid">
                  {errorsUpdate.nombre?.message}
                </Form.Control.Feedback>
              </Form.Group>

              <Form.Group className="mb-3">
                <Form.Label>Latitud</Form.Label>
                <Form.Control
                  type="number"
                  step="any"
                  placeholder="Ej: -34.6037"
                  {...registerUpdate('latitud', { valueAsNumber: true })}
                  isInvalid={!!errors.latitud}
                />
                <Form.Control.Feedback type="invalid">
                  {errors.latitud?.message}
                </Form.Control.Feedback>
              </Form.Group>

              <Form.Group className="mb-3">
                <Form.Label>Longitud</Form.Label>
                <Form.Control
                  type="number"
                  step="any"
                  placeholder="Ej: -58.3816"
                  {...registerUpdate('longitud', { valueAsNumber: true })}
                  isInvalid={!!errors.longitud}
                />
                <Form.Control.Feedback type="invalid">
                  {errors.longitud?.message}
                </Form.Control.Feedback>
              </Form.Group>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onClick={handleCloseUpdateModal}>
                Cancelar
              </Button>
              <Button type="submit" className="btn-vigia-primary" disabled={submittingUpdate}>
                {submittingUpdate ? <Spinner size="sm" animation="border" /> : 'Confirmar'}
              </Button>
            </Modal.Footer>
          </Form>
        </Modal>
      )}

      {/* Modal Detalle */}
      {establecimientoDetail && (
        <Modal show={showDetailModal} onHide={() => setShowDetailModal(false)} centered size="lg">
          <Modal.Header closeButton style={{ backgroundColor: '#1E3A5F', color: 'white' }}>
            <Modal.Title>Detalle establecimiento</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <p><strong>Nombre:</strong> {establecimientoDetail.nombre}</p>
            <div className="location pb-2">
              <i className="bi bi-geo-alt-fill me-2"></i>
              <strong>Ubicación:</strong> Lat: {establecimientoDetail.latitud}, Lon: {establecimientoDetail.longitud}
            </div>
            <MapContainer
              center={[establecimientoDetail.latitud, establecimientoDetail.longitud]}
              zoom={14}
              scrollWheelZoom={false}
              className="establishment-map"
              style={{ height: '300px', marginBottom: '20px' }}
            >
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              <Marker position={[establecimientoDetail.latitud, establecimientoDetail.longitud]}>
                <Popup>Establecimiento: {establecimientoDetail.nombre}</Popup>
              </Marker>
            </MapContainer>

            <hr />
            <div className="d-flex justify-content-between align-items-center mb-3">
              <h5 className="mb-0">Lotes</h5>
            </div>

            {errorLote && <Alert variant="danger">{errorLote}</Alert>}

            <Form onSubmit={async (e) => {
              e.preventDefault();
              if (!nuevoLoteNombre.trim()) return;
              try {
                setCreandoLote(true);
                setErrorLote(null);
                const nuevo = await crearRodeoLote(nuevoLoteNombre, establecimientoDetail.id);
                setLotesDetalle([...lotesDetalle, nuevo]);
                setNuevoLoteNombre('');
              } catch (err) {
                const axiosError = err as { response?: { data?: string } };
                setErrorLote(axiosError.response?.data || 'Error al crear lote');
              } finally {
                setCreandoLote(false);
              }
            }} className="mb-3 d-flex gap-2">
              <Form.Control
                type="text"
                placeholder="Nombre del nuevo lote"
                value={nuevoLoteNombre}
                onChange={e => setNuevoLoteNombre(e.target.value)}
                disabled={creandoLote}
              />
              <Button type="submit" className="btn-vigia-primary text-nowrap" disabled={creandoLote || !nuevoLoteNombre.trim()}>
                {creandoLote ? <Spinner size="sm" animation="border" /> : '+ Nuevo Lote'}
              </Button>
            </Form>

            {loadingLotes ? (
              <div className="text-center py-3"><Spinner animation="border" variant="primary" /></div>
            ) : lotesDetalle.length === 0 ? (
              <p className="text-muted text-center py-2">No hay lotes en este establecimiento.</p>
            ) : (
              <ul className="list-group">
                {lotesDetalle.map(lote => (
                  <li key={lote.id} className="list-group-item d-flex justify-content-between align-items-center">
                    <div>
                      <span className="fw-semibold">{lote.nombre}</span>
                      {lote.activo ? (
                        <span className="badge bg-success ms-2">Activo</span>
                      ) : (
                        <span className="badge bg-secondary ms-2">Inactivo</span>
                      )}
                    </div>
                    <span className="text-muted small">Alta: {new Date(lote.fechaAlta).toLocaleDateString()}</span>
                  </li>
                ))}
              </ul>
            )}
          </Modal.Body>
        </Modal>
      )}
    </div>
  );
}
