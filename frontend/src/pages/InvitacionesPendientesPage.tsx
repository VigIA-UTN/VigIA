import { useEffect, useState } from 'react';
import { 
  listarMisInvitacionesRequest, 
  aceptarInvitacionRequest, 
  rechazarInvitacionRequest, 
  type InvitacionResponse 
} from '../api/invitacionesApi';
import { useAuthStore } from '../store/authStore';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { showAlert, showConfirm } from '../store/dialogStore';
import { getEstablecimientos } from '../api/establecimientosApi';
import { Container, Button, Alert, Spinner } from 'react-bootstrap';
import './InvitacionesPendientesPage.css';

export default function InvitacionesPendientesPage() {
  const [invitaciones, setInvitaciones] = useState<InvitacionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchInvitaciones = async () => {
    try {
      setLoading(true);
      const data = await listarMisInvitacionesRequest();
      setInvitaciones(data);
    } catch (err) {
      const errorResponse = (err as { response?: { data?: { message?: string } } }).response;
      setError(errorResponse?.data?.message || 'Error al cargar las invitaciones.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchInvitaciones();
  }, []);

  const handleAceptar = async (id: number) => {
    try {
      await aceptarInvitacionRequest(id);
      const currentRol = useAuthStore.getState().rol;
      if (currentRol === 'PENDIENTE') {
        await showAlert('¡Invitación aceptada con éxito! Como tus permisos han cambiado, por favor iniciá sesión nuevamente.', {
          variant: 'success',
          title: 'Invitación aceptada',
          confirmText: 'Ir al login',
        });
        useAuthStore.getState().logout();
        window.location.href = '/login';
      } else {
        await fetchInvitaciones();
        const ests = await getEstablecimientos();
        useEstablecimientoStore.getState().setEstablecimientos(ests);
      }
    } catch (err) {
      const data = (err as { response?: { data?: { message?: string } | string } }).response?.data;
      const errorMessage = typeof data === 'string' ? data : data?.message;
      showAlert(errorMessage || 'Error al aceptar invitación.', { variant: 'error' });
    }
  };

  const handleRechazar = async (id: number) => {
    const confirmado = await showConfirm('¿Seguro que querés rechazar esta invitación?', {
      title: 'Rechazar invitación',
      confirmText: 'Sí, rechazar',
      danger: true,
    });
    if (!confirmado) return;
    try {
      await rechazarInvitacionRequest(id);
      fetchInvitaciones();
    } catch (err) {
      const errorResponse = (err as { response?: { data?: string } }).response;
      showAlert(errorResponse?.data || 'Error al rechazar invitación.', { variant: 'error' });
    }
  };

  return (
    <>
      <div className="vigia-page-container">
        <Container>
          <div className="vigia-page-header">
            <h2 className="vigia-page-title">Mis Invitaciones Pendientes</h2>
          </div>

          {error && <Alert variant="danger">{error}</Alert>}

          {loading ? (
            <div className="text-center mt-5">
              <Spinner animation="border" style={{ color: '#1E3A5F' }} />
            </div>
          ) : invitaciones.length === 0 ? (
            <div className="empty-state">
              <h4>No tenés invitaciones pendientes.</h4>
              <p>Cuando un Productor te invite a un establecimiento, aparecerá acá.</p>
            </div>
          ) : (
            <div className="invitaciones-list">
              {invitaciones.map((inv) => (
                <div key={inv.id} className="invitacion-card">
                  <div className="invitacion-info">
                    <h4>{inv.establecimiento}</h4>
                    <p><strong>Rol sugerido:</strong> {inv.rol}</p>
                    <p><strong>Enviado por:</strong> {inv.nombreCompleto}</p>
                    <p><strong>Fecha de envío:</strong> {new Date(inv.fechaCreacion || '').toLocaleDateString()}</p>
                    {inv.fechaExpiracion && (
                      <p><strong>Vence el:</strong> {new Date(inv.fechaExpiracion).toLocaleDateString()}</p>
                    )}
                  </div>
                  <div className="invitacion-actions">
                    <Button 
                      variant="outline-danger"
                      onClick={() => inv.id && handleRechazar(inv.id)}
                      className="w-100 mb-2 fw-bold"
                    >
                      Rechazar
                    </Button>
                    <Button 
                      variant="success"
                      onClick={() => inv.id && handleAceptar(inv.id)}
                      className="w-100 fw-bold"
                      style={{ backgroundColor: '#8CC63F', borderColor: '#8CC63F' }}
                    >
                      Aceptar Invitación
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Container>
      </div>
    </>
  );
}
