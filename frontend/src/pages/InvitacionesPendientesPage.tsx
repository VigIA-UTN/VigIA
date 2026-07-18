import { useEffect, useState } from 'react';
import { 
  listarMisInvitacionesRequest, 
  aceptarInvitacionRequest, 
  rechazarInvitacionRequest, 
  type InvitacionResponse 
} from '../api/invitacionesApi';
import { useAuthStore } from '../store/authStore';
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
        alert('¡Invitación aceptada con éxito! Como tus permisos han cambiado, por favor iniciá sesión nuevamente.');
        useAuthStore.getState().logout();
        window.location.href = '/login';
      } else {
        fetchInvitaciones();
      }
    } catch (err) {
      const data = (err as { response?: { data?: { message?: string } | string } }).response?.data;
      const errorMessage = typeof data === 'string' ? data : data?.message;
      alert(errorMessage || 'Error al aceptar invitación.');
    }
  };

  const handleRechazar = async (id: number) => {
    if (!confirm('¿Seguro que querés rechazar esta invitación?')) return;
    try {
      await rechazarInvitacionRequest(id);
      fetchInvitaciones();
    } catch (err) {
      const errorResponse = (err as { response?: { data?: string } }).response;
      alert(errorResponse?.data || 'Error al rechazar invitación.');
    }
  };

  return (
    <>
      <div className="invitaciones-pendientes-container">
        <div className="invitaciones-pendientes-header">
          <h2>Mis Invitaciones Pendientes</h2>
        </div>

        {error && <div className="alert alert-danger">{error}</div>}

        {loading ? (
          <div className="text-center mt-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">Cargando...</span>
            </div>
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
                  <p><strong>Fecha:</strong> {new Date(inv.fechaCreacion || '').toLocaleDateString()}</p>
                </div>
                <div className="invitacion-actions">
                  <button 
                    className="btn-rechazar"
                    onClick={() => inv.id && handleRechazar(inv.id)}
                  >
                    Rechazar
                  </button>
                  <button 
                    className="btn-aceptar"
                    onClick={() => inv.id && handleAceptar(inv.id)}
                  >
                    Aceptar Invitación
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
}
