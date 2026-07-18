import { useState, useEffect } from 'react';
import { Container, Button, Modal, Form, Spinner, Alert, Badge } from 'react-bootstrap';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  getInvitacionesProductor,
  crearInvitacionProductor,
} from '../api/invitacionesApi';
import type { InvitacionResponse } from '../api/invitacionesApi';
import { getEstablecimientos } from '../api/establecimientosApi';
import type { EstablecimientoResponse } from '../api/establecimientosApi';
import './InvitacionesPage.css';

const schema = z.object({
  email: z.string().email('Debe ser un email válido'),
  establecimientoId: z.number({ message: 'Seleccione un establecimiento' }).min(1, 'Seleccione un establecimiento'),
});

type FormData = z.infer<typeof schema>;

const ESTADO_CONFIG: Record<string, { label: string; className: string }> = {
  PENDIENTE: { label: 'PENDIENTE', className: 'badge-pendiente' },
  USADA: { label: 'USADA', className: 'badge-usada' },
  EXPIRADA: { label: 'EXPIRADA', className: 'badge-expirada' },
};

export default function InvitacionesPage() {
  const [invitaciones, setInvitaciones] = useState<InvitacionResponse[]>([]);
  const [establecimientos, setEstablecimientos] = useState<EstablecimientoResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const [emailFilter, setEmailFilter] = useState('');
  const [estadoFilter, setEstadoFilter] = useState('');
  const [establecimientoFilter, setEstablecimientoFilter] = useState('');
  const [sortField, setSortField] = useState<string | null>(null);
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

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

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const [inv, est] = await Promise.all([
          getInvitacionesProductor(),
          getEstablecimientos(),
        ]);
        setInvitaciones(inv);
        setEstablecimientos(est);
      } catch (err) {
        const axiosError = err as { response?: { data?: { message?: string } } };
        setError(axiosError.response?.data?.message || 'Error al cargar datos');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const onSubmit = async (data: FormData) => {
    try {
      setSubmitting(true);
      setError(null);
      const nueva = await crearInvitacionProductor(data);
      if (nueva.estado === 'USADA') {
        setSuccessMsg('El usuario ya estaba registrado y fue asociado al establecimiento. Se le envió un email de notificación.');
      } else {
        setSuccessMsg(`Invitación enviada correctamente a ${nueva.emailDestino}.`);
      }
      // Recargar la lista de invitaciones
      const invActualizadas = await getInvitacionesProductor();
      setInvitaciones(invActualizadas);
      handleCloseModal();
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      setError(axiosError.response?.data?.message || 'Error al generar invitación');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    reset();
    setError(null);
  };

  return (
    <div className="vigia-page-container">
      <Container>
        <div className="vigia-page-header">
          <h2 className="vigia-page-title">Productores Invitados</h2>
          <Button
            className="btn-vigia-success"
            onClick={() => setShowModal(true)}
          >
            <i className="bi bi-person-plus-fill me-2"></i> Nuevo
          </Button>
        </div>

        {successMsg && (
          <Alert variant="success" dismissible onClose={() => setSuccessMsg(null)}>
            {successMsg}
          </Alert>
        )}
        {error && !showModal && (
          <Alert variant="danger" dismissible onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {/* Filtros */}
        <div className="d-flex flex-column flex-md-row mb-4 gap-3">
          <Form.Control
            type="text"
            placeholder="Buscar por email..."
            value={emailFilter}
            onChange={(e) => setEmailFilter(e.target.value)}
            style={{ flex: 1, maxWidth: '300px' }}
          />
          <Form.Select 
            value={estadoFilter} 
            onChange={(e) => setEstadoFilter(e.target.value)}
            style={{ flex: 1, maxWidth: '200px' }}
          >
            <option value="">Todos los estados</option>
            <option value="PENDIENTE">PENDIENTE</option>
            <option value="USADA">USADA</option>
            <option value="EXPIRADA">EXPIRADA</option>
          </Form.Select>
          <Form.Select 
            value={establecimientoFilter} 
            onChange={(e) => setEstablecimientoFilter(e.target.value)}
            style={{ flex: 1, maxWidth: '250px' }}
          >
            <option value="">Todos los establecimientos</option>
            {establecimientos.map(e => (
              <option key={e.id} value={e.nombre}>{e.nombre}</option>
            ))}
          </Form.Select>
        </div>

        {/* Tabla */}
        {loading ? (
          <div className="text-center mt-5">
            <Spinner animation="border" style={{ color: '#1E3A5F' }} />
          </div>
        ) : (
          <div className="vigia-table-wrapper">
            <table className="vigia-table">
              <thead>
                <tr>
                  <th onClick={() => handleSort('nombreCompleto')} style={{cursor: 'pointer'}}>Nombre Completo {sortField === 'nombreCompleto' && (sortDirection === 'asc' ? '↑' : '↓')}</th>
                  <th onClick={() => handleSort('emailDestino')} style={{cursor: 'pointer'}}>Email {sortField === 'emailDestino' && (sortDirection === 'asc' ? '↑' : '↓')}</th>
                  <th onClick={() => handleSort('establecimiento')} style={{cursor: 'pointer'}}>Establecimiento Asociado {sortField === 'establecimiento' && (sortDirection === 'asc' ? '↑' : '↓')}</th>
                  <th onClick={() => handleSort('estado')} style={{cursor: 'pointer'}}>Invitación {sortField === 'estado' && (sortDirection === 'asc' ? '↑' : '↓')}</th>
                </tr>
              </thead>
              <tbody>
                {(() => {
                  const filteredInvitaciones = invitaciones.filter(inv => {
                      const matchesEmail = emailFilter === '' || inv.emailDestino.toLowerCase().includes(emailFilter.toLowerCase());
                      const matchesEstado = estadoFilter === '' || inv.estado === estadoFilter;
                      const matchesEstablecimiento = establecimientoFilter === '' || (inv.establecimiento && inv.establecimiento === establecimientoFilter);
                      return matchesEmail && matchesEstado && matchesEstablecimiento;
                  });

                  const sortedInvitaciones = [...filteredInvitaciones].sort((a, b) => {
                      if (!sortField) return 0;
                      let valA = a[sortField as keyof InvitacionResponse] as string | number | null;
                      let valB = b[sortField as keyof InvitacionResponse] as string | number | null;

                      if (valA === null || valA === undefined) valA = '';
                      if (valB === null || valB === undefined) valB = '';

                      if (valA < valB) return sortDirection === 'asc' ? -1 : 1;
                      if (valA > valB) return sortDirection === 'asc' ? 1 : -1;
                      return 0;
                  });

                  if (sortedInvitaciones.length === 0) {
                    return (
                      <tr>
                        <td colSpan={4} className="text-center text-muted py-4">
                          No se encontraron invitaciones que coincidan con la búsqueda.
                        </td>
                      </tr>
                    );
                  }

                  return sortedInvitaciones.map((inv, idx) => {
                    const estadoConf = ESTADO_CONFIG[inv.estado] ?? { label: inv.estado, className: '' };
                    return (
                      <tr key={inv.id ?? idx}>
                        <td>
                          <div className="productor-info">
                            <div className="productor-avatar">
                              {inv.nombreCompleto
                                ? inv.nombreCompleto.charAt(0).toUpperCase()
                                : inv.emailDestino.charAt(0).toUpperCase()}
                            </div>
                            <span>{inv.nombreCompleto ?? '—'}</span>
                          </div>
                        </td>
                        <td>
                          <a href={`mailto:${inv.emailDestino}`} className="email-link">
                            {inv.emailDestino}
                          </a>
                        </td>
                        <td>
                          <span className="establecimiento-tag">
                            {inv.establecimiento ?? '—'}
                          </span>
                        </td>
                        <td>
                          <Badge className={`estado-badge ${estadoConf.className}`}>
                            {estadoConf.label}
                          </Badge>
                        </td>
                      </tr>
                    );
                  });
                })()}
              </tbody>
            </table>
          </div>
        )}
      </Container>

      {/* Modal Nueva Invitación */}
      <Modal show={showModal} onHide={handleCloseModal} centered dialogClassName="modal-productor">
        <Modal.Header closeButton>
          <Modal.Title>Nuevo Productor</Modal.Title>
        </Modal.Header>
        <Form onSubmit={handleSubmit(onSubmit)}>
          <Modal.Body>
            {error && <Alert variant="danger" className="mb-3">{error}</Alert>}

            <Form.Group className="mb-3">
              <Form.Label>Email del Productor</Form.Label>
              <div className="email-input-wrapper">
                <Form.Control
                  type="email"
                  placeholder="correo@electronico.com"
                  {...register('email')}
                  isInvalid={!!errors.email}
                  style={{ paddingRight: '2.5rem' }}
                />
                <button
                  type="button"
                  className="email-clear-btn"
                  onClick={() => reset({ email: '', establecimientoId: 0 })}
                  tabIndex={-1}
                >
                  ×
                </button>
                <Form.Control.Feedback type="invalid">
                  {errors.email?.message}
                </Form.Control.Feedback>
              </div>
            </Form.Group>

            <Form.Group className="mb-4">
              <Form.Label>Nombre de Establecimiento</Form.Label>
              <Form.Select
                {...register('establecimientoId', { valueAsNumber: true })}
                isInvalid={!!errors.establecimientoId}
                defaultValue=""
              >
                <option value="" disabled>Seleccioná una opción</option>
                {establecimientos.filter(e => e.activo).map(e => (
                  <option key={e.id} value={e.id}>{e.nombre}</option>
                ))}
              </Form.Select>
              <Form.Control.Feedback type="invalid">
                {errors.establecimientoId?.message}
              </Form.Control.Feedback>
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <button type="submit" className="btn-generar" disabled={submitting}>
              {submitting ? <Spinner size="sm" animation="border" /> : 'Generar Invitación'}
            </button>
          </Modal.Footer>
        </Form>
      </Modal>
    </div>
  );
}
