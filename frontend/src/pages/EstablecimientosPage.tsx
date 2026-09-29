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

// Schema para configuración de anestro en el modal de detalle
const anestroSchema = z.object({
  diasDeteccionAnestro: z
    .number({ message: 'Debe ser un número entero' })
    .int('Debe ser un número entero')
    .min(1, 'Mínimo 1 día')
    .max(365, 'Máximo 365 días'),
  severidadAnestro: z.enum(['BAJA', 'MEDIA', 'ALTA']),
});
type AnestroFormData = z.infer<typeof anestroSchema>;

const SEVERIDAD_CFG: Record<string, { label: string; color: string; bg: string; icon: string }> = {
  BAJA:  { label: 'Baja',  color: '#2B7295', bg: '#e8f4f8', icon: 'bi-bell' },
  MEDIA: { label: 'Media', color: '#c47c0a', bg: '#fef3c7', icon: 'bi-bell-fill' },
  ALTA:  { label: 'Alta',  color: '#c0392b', bg: '#fee2e2', icon: 'bi-bell-slash-fill' },
};

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

  // Estado para configuración de anestro en el modal de detalle
  const [savingAnestro, setSavingAnestro] = useState(false);
  const [anestroSaved, setAnestroSaved] = useState(false);
  const [errorAnestro, setErrorAnestro] = useState<string | null>(null);

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const { register: registerUpdate, handleSubmit: handleSubmitUpdate, reset: resetUpdate, formState: { errors: errorsUpdate } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const {
    register: registerAnestro,
    handleSubmit: handleSubmitAnestro,
    reset: resetAnestro,
    watch: watchAnestro,
    setValue: setAnestroValue,
    formState: { errors: errorsAnestro, isDirty: isAnestroD },
  } = useForm<AnestroFormData>({
    resolver: zodResolver(anestroSchema),
    defaultValues: { diasDeteccionAnestro: 45, severidadAnestro: 'MEDIA' },
  });

  const sevActual = watchAnestro('severidadAnestro');

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

  const onSaveAnestro = async (data: AnestroFormData) => {
    if (!establecimientoDetail) return;
    setSavingAnestro(true);
    setErrorAnestro(null);
    try {
      const actualizado = await updateEstablecimiento(establecimientoDetail.id, {
        nombre: establecimientoDetail.nombre,
        latitud: establecimientoDetail.latitud,
        longitud: establecimientoDetail.longitud,
        diasDeteccionAnestro: data.diasDeteccionAnestro,
        severidadAnestro: data.severidadAnestro,
      });
      setEstablecimientoDetail(actualizado);
      setEstablecimientos(prev => prev.map(e => e.id === actualizado.id ? actualizado : e));
      resetAnestro({ diasDeteccionAnestro: actualizado.diasDeteccionAnestro, severidadAnestro: actualizado.severidadAnestro as 'BAJA' | 'MEDIA' | 'ALTA' });
      setAnestroSaved(true);
      setTimeout(() => setAnestroSaved(false), 2500);
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      setErrorAnestro(axiosError.response?.data?.message || 'Error al guardar');
    } finally {
      setSavingAnestro(false);
    }
  };

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
    <div className="vigia-page-container">
      <Container>
        <div className="vigia-page-header">
          <h2 className="vigia-page-title">Establecimientos</h2>
          <div className="d-flex gap-3">
            <Form.Control
              type="text"
              placeholder="Buscar por nombre o #id..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              style={{ maxWidth: '300px' }}
            />
            {rol === 'ADMINISTRADOR' && (
              <Button className="btn-vigia-success text-nowrap" onClick={handleShowModal}>
                <i className="bi bi-plus-lg me-2" /> Nuevo
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
                  <div className={`establecimiento-card ${!est.activo ? 'establecimiento-card--inactivo' : ''}`}>

                    {/* Imagen satelital */}
                    <div className="est-card-map">
                      <img
                        src={`https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/export?bbox=${est.longitud - 0.04},${est.latitud - 0.025},${est.longitud + 0.04},${est.latitud + 0.025}&bboxSR=4326&size=600,220&imageSR=102100&format=jpg&transparent=false&f=image`}
                        alt={`Vista satelital de ${est.nombre}`}
                        className="est-card-map-img"
                        loading="lazy"
                      />
                      {/* Overlay degradado para que el nombre se lea bien */}
                      <div className="est-card-map-overlay" />
                      {/* Badge de estado sobre la imagen */}
                      <span className={`est-status-badge ${est.activo ? 'est-status-badge--active' : 'est-status-badge--inactive'}`}>
                        <span className="est-status-dot" />
                        {est.activo ? 'Activo' : 'Inactivo'}
                      </span>
                      {/* Acciones de editar */}
                      <div className="est-card-top-actions">
                        <button
                          className="est-icon-btn"
                          title="Editar establecimiento"
                          onClick={() => {
                            setEstablecimientoEdit(est);
                            resetUpdate({
                              nombre: est.nombre,
                              latitud: est.latitud,
                              longitud: est.longitud,
                            });
                            setShowUpdateModal(true);
                          }}
                        >
                          <i className="bi bi-pencil-fill" />
                        </button>
                      </div>
                    </div>

                    {/* Info de la card */}
                    <div className="est-card-body">
                      <div className="est-card-info">
                        <span className="est-card-id">#{est.id}</span>
                        <h4 className="est-card-name">{est.nombre}</h4>
                        <p className="est-card-coords">
                          <i className="bi bi-geo-alt-fill me-1" />
                          {est.latitud.toFixed(4)}, {est.longitud.toFixed(4)}
                        </p>
                      </div>

                      {/* Botones */}
                      <div className="est-card-actions">
                        <button
                          className="est-btn est-btn-detail"
                          onClick={async () => {
                            setEstablecimientoDetail(est);
                            setShowDetailModal(true);
                            setErrorAnestro(null);
                            setAnestroSaved(false);
                            resetAnestro({
                              diasDeteccionAnestro: est.diasDeteccionAnestro ?? 45,
                              severidadAnestro: (est.severidadAnestro as 'BAJA' | 'MEDIA' | 'ALTA') ?? 'MEDIA',
                            });
                            setLoadingLotes(true);
                            setErrorLote(null);
                            try {
                              const allLotes = await getRodeoLotes(est.id);
                              setLotesDetalle(allLotes);
                            } catch {
                              setErrorLote('Error al cargar lotes');
                            } finally {
                              setLoadingLotes(false);
                            }
                          }}
                        >
                          Ver detalle
                        </button>
                        {est.activo ? (
                          <button className="est-btn est-btn-danger" onClick={() => handleDesactivar(est.id)}>
                            Desactivar
                          </button>
                        ) : (
                          <button className="est-btn est-btn-success" onClick={() => handleActivar(est.id)}>
                            Activar
                          </button>
                        )}
                      </div>
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
                  isInvalid={!!errorsUpdate.latitud}
                />
                <Form.Control.Feedback type="invalid">
                  {errorsUpdate.latitud?.message}
                </Form.Control.Feedback>
              </Form.Group>

              <Form.Group className="mb-3">
                <Form.Label>Longitud</Form.Label>
                <Form.Control
                  type="number"
                  step="any"
                  placeholder="Ej: -58.3816"
                  {...registerUpdate('longitud', { valueAsNumber: true })}
                  isInvalid={!!errorsUpdate.longitud}
                />
                <Form.Control.Feedback type="invalid">
                  {errorsUpdate.longitud?.message}
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
                attribution='Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community'
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
              />
              <Marker position={[establecimientoDetail.latitud, establecimientoDetail.longitud]}>
                <Popup>Establecimiento: {establecimientoDetail.nombre}</Popup>
              </Marker>
            </MapContainer>

            {(rol === 'PRODUCTOR' || rol === 'VETERINARIO') && (
              <>
                  <hr />
                  <div className="d-flex align-items-center gap-2 mb-3">
                    <i className="bi bi-gear-fill" style={{ color: '#1E3A5F' }} />
                    <h5 className="mb-0" style={{ color: '#1E3A5F' }}>Configuración de Anestro</h5>
                  </div>
                  {errorAnestro && <Alert variant="danger" className="py-2">{errorAnestro}</Alert>}
                  {anestroSaved && <Alert variant="success" className="py-2">✓ Guardado correctamente</Alert>}
                  <form onSubmit={handleSubmitAnestro(onSaveAnestro)} noValidate>

                    {/* Fila 1: input días + info box al lado */}
                    <div className="d-flex gap-3 align-items-center mb-3">

                      {/* Input días */}
                      <div style={{ flexShrink: 0 }}>
                        <label className="form-label fw-bold mb-1" style={{ fontSize: '0.95rem', color: '#1E3A5F' }}>Días sin ciclo</label>
                        <div className="input-group input-group-sm" style={{ maxWidth: 170 }}>
                          <input
                            type="number"
                            className={`form-control ${errorsAnestro.diasDeteccionAnestro ? 'is-invalid' : ''}`}
                            min={1} max={365}
                            placeholder="45"
                            {...registerAnestro('diasDeteccionAnestro', { valueAsNumber: true })}
                          />
                          <span className="input-group-text">días</span>
                        </div>
                        {errorsAnestro.diasDeteccionAnestro && (
                          <div className="text-danger mt-1" style={{ fontSize: '0.75rem' }}>{errorsAnestro.diasDeteccionAnestro.message}</div>
                        )}
                      </div>

                      {/* Info box al lado, mismo alto que el input */}
                      <div className="est-anestro-info" style={{ flex: 1 }}>
                        <i className="bi bi-info-circle-fill" style={{ color: '#3B8D99', flexShrink: 0 }} />
                        <p className="mb-0" style={{ fontSize: '0.78rem', color: '#4b5563', lineHeight: 1.4 }}>
                          Días sin ciclo reproductivo antes de generar una alerta. Defecto: <strong>45 días</strong>.
                        </p>
                      </div>
                    </div>

                    {/* Fila 2: toggle severidad */}
                    <div className="mb-3">
                      <label className="form-label fw-bold mb-1" style={{ fontSize: '0.95rem', color: '#1E3A5F' }}>Prioridad de la alerta</label>
                      <div className="est-anestro-toggle">
                        {(['BAJA', 'MEDIA', 'ALTA'] as const).map(nivel => {
                          const cfg = SEVERIDAD_CFG[nivel];
                          const isActive = sevActual === nivel;
                          return (
                            <button
                              key={nivel}
                              type="button"
                              className={`est-anestro-pill ${isActive ? 'est-anestro-pill--active' : ''}`}
                              style={isActive ? { backgroundColor: cfg.bg, color: cfg.color, borderColor: cfg.color } : {}}
                              onClick={() => setAnestroValue('severidadAnestro', nivel, { shouldDirty: true })}
                            >
                              <i className={`bi ${cfg.icon}`} style={isActive ? { color: cfg.color } : {}} />
                              {cfg.label}
                            </button>
                          );
                        })}
                      </div>
                    </div>

                    {/* 4. Guardar */}
                    <div className="d-flex justify-content-end">
                      <button
                        type="submit"
                        className="btn btn-sm"
                        style={{ background: '#1E3A5F', color: '#fff', fontWeight: 600, borderRadius: 8, padding: '0.4rem 1.2rem' }}
                        disabled={savingAnestro || !isAnestroD}
                      >
                        {savingAnestro ? <Spinner size="sm" animation="border" /> : 'Guardar Cambios'}
                      </button>
                    </div>
                  </form>
                </>
            )}

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
