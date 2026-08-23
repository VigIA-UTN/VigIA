import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';

export default function OAuth2CallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);

  useEffect(() => {
    const token = searchParams.get('token');
    const email = searchParams.get('email');
    const rol = searchParams.get('rol');
    const error = searchParams.get('error');

    if (error) {
      navigate(`/login?sso_error=${encodeURIComponent(error)}`, { replace: true });
      return;
    }

    if (token && email) {
      login(token, email, rol || 'PENDIENTE');

      if (rol === 'ADMINISTRADOR') {
        navigate('/usuarios', { replace: true });
      } else if (rol === 'PENDIENTE' || !rol) {
        navigate('/invitaciones', { replace: true });
      } else {
        navigate('/dashboard', { replace: true });
      }
    } else {
      navigate('/login?sso_error=1', { replace: true });
    }
  }, [searchParams, login, navigate]);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      height: '100vh',
      backgroundColor: '#f8fafc',
      fontFamily: 'system-ui, -apple-system, sans-serif'
    }}>
      <h2>Iniciando sesión con SSO...</h2>
      <p style={{ color: '#64748b' }}>Por favor espere mientras procesamos su autenticación.</p>
    </div>
  );
}
