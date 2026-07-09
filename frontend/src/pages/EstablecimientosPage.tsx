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
import './EstablecimientosPage.css';
import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet';

const schema = z.object({
  nombre: z.string().min(1, 'El nombre es requerido'),
  latitud: z.number({ message: 'Debe ser un número válido' }),
  longitud: z.number({ message: 'Debe ser un número válido' }),
});

type FormData = z.infer<typeof schema>;

export default function EstablecimientosPage() {
  const [establecimientos, setEstablecimientos] = useState<EstablecimientoResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [showModal, setShowModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [showUpdateModal, setShowUpdateModal] = useState(false);
  const [submittingUpdate, setSubmittingUpdate] = useState(false);
  const [establecimientoEdit, setEstablecimientoEdit] = useState<EstablecimientoResponse | null>(null);

  const [showDetailModal, setShowDetailModal] = useState(false);
  const [establecimientoDetail, setEstablecimientoDetail] = useState<EstablecimientoResponse | null>(null);

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
        <div className="d-flex justify-content-between align-items-center mb-4">
          <h2 className="establecimientos-title">Establecimientos</h2>
          <Button className="btn-vigia-primary" onClick={handleShowModal}>
            + Nuevo
          </Button>
        </div>

        {error && <Alert variant="danger">{error}</Alert>}

        {loading ? (
          <div className="text-center mt-5">
            <Spinner animation="border" variant="primary" />
          </div>
        ) : (
          <Row className="g-4">
            {establecimientos.map((est) => (
              <Col key={est.id} xs={12} md={6} lg={4}>
                <div className="establecimiento-card">
                  <div className={`status-indicator ${est.activo ? 'status-active' : 'status-inactive'}`} title={est.activo ? 'Activo' : 'Inactivo'} />
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
                    <Button variant="light" className="btn-vigia-primary w-100" onClick={() => {
                      setShowDetailModal(true);
                      setEstablecimientoDetail(est);
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
            {establecimientos.length === 0 && !loading && (
              <Col>
                <p className="text-muted">No hay establecimientos registrados.</p>
              </Col>
            )}
          </Row>
        )}
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
        <Modal show={showDetailModal} onHide={() => setShowDetailModal(false)} centered>
          <Modal.Header closeButton style={{ backgroundColor: '#1E3A5F', color: 'white' }}>
            <Modal.Title>Detalle establecimiento</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <p>Nombre: {establecimientoDetail.nombre}</p>
            <div className="location pb-2">
              <i className="bi bi-geo-alt-fill me-2"></i>
              Lat: {establecimientoDetail.latitud}, Lon: {establecimientoDetail.longitud}
            </div>
            <MapContainer
              center={[establecimientoDetail.latitud, establecimientoDetail.longitud]}
              zoom={14}
              scrollWheelZoom={false}
              className="establishment-map"
            >
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              <Marker position={[establecimientoDetail.latitud, establecimientoDetail.longitud]}>
                <Popup>Establecimiento: {establecimientoDetail.nombre}</Popup>
              </Marker>
            </MapContainer>
          </Modal.Body>
        </Modal>
      )}
    </div>
  );
}
