import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import { validateCodeRequest, registerRequest } from '../api/authApi';
import { aceptarInvitacionPorTokenRequest } from '../api/invitacionesApi';
import { useAuthStore } from '../store/authStore';
import logoImg from '../assets/logo.png';
import loginImage from '../assets/login_image.jpg';
import './AcceptInvitationPage.css';

const backendBaseUrl = (import.meta.env.VITE_API_URL || '').replace(/\/api\/v1\/?$/, '');
const googleOAuthUrl = `${backendBaseUrl}/oauth2/authorization/google`;

const schema = z.object({
  nombre: z.string().min(1, 'El nombre es requerido'),
  apellido: z.string().min(1, 'El apellido es requerido'),
  password: z.string()
    .min(8, 'Debe tener al menos 8 caracteres')
    .max(32, 'No puede tener más de 32 caracteres')
    .regex(/[A-Z]/, 'Debe contener al menos una mayúscula')
    .regex(/[a-z]/, 'Debe contener al menos una minúscula')
    .regex(/[0-9\W_]/, 'Debe contener al menos un número o símbolo'),
});

type FormData = z.infer<typeof schema>;

export default function AcceptInvitationPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const login = useAuthStore((s) => s.login);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const loggedInEmail = useAuthStore((s) => s.email);
  const logout = useAuthStore((s) => s.logout);

  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const [invitationData, setInvitationData] = useState<{ email: string; rol: string; establecimiento: string } | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  useEffect(() => {
    const validateToken = async () => {
      if (!token) {
        setError('No se proporcionó ningún token de invitación.');
        setIsLoading(false);
        return;
      }

      try {
        const data = await validateCodeRequest(token);
        setInvitationData(data);
      } catch (err) {
        if (axios.isAxiosError(err)) {
          if (err.response?.status === 400 || err.response?.status === 404) {
            const data = err.response.data;
            const msg = typeof data === 'string' ? data : (data?.message || 'El enlace de invitación es inválido o ha expirado.');
            setError(msg);
          } else {
            setError('Error al validar la invitación. Intentá de nuevo más tarde.');
          }
        } else {
          setError('No se pudo conectar al servidor.');
        }
      } finally {
        setIsLoading(false);
      }
    };
    validateToken();
  }, [token]);

  const handleAcceptToken = async () => {
    if (!token) return;
    setIsSubmitting(true);
    try {
      await aceptarInvitacionPorTokenRequest(token);
      setSuccess(true);
      setTimeout(() => navigate('/'), 2000);
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const data = err.response?.data;
        const msg = typeof data === 'string' ? data : (data?.message || 'Error al procesar tu invitación. Quizás ya fue usada o está expirada.');
        setError(msg);
      } else {
        setError('Error al conectar con el servidor.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const onSubmit = async (data: FormData) => {
    if (!token || !invitationData) return;
    setIsSubmitting(true);
    setError(null);
    try {
      const response = await registerRequest(
        data.nombre,
        data.apellido,
        invitationData.email,
        data.password,
        token
      );
      login(response.token, response.email, response.rol);
      navigate('/');
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const data = err.response?.data;
        const msg = typeof data === 'string' ? data : (data?.message || 'Error al completar el registro.');
        setError(msg);
      } else {
        setError('Error inesperado.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="accept-invitation-container">
        <div className="background-image" style={{ backgroundImage: `url(${loginImage})` }}></div>
        <div className="background-overlay"></div>
        <div className="accept-invitation-card loading-card">
          <div className="spinner" />
          <p>Procesando invitación...</p>
        </div>
      </div>
    );
  }

  if (success) {
    return (
      <div className="accept-invitation-container">
        <div className="background-image" style={{ backgroundImage: `url(${loginImage})` }}></div>
        <div className="background-overlay"></div>
        <div className="accept-invitation-card">
          <img src={logoImg} alt="VigIA Logo" className="logo" />
          <h2 style={{ color: '#28a745' }}>¡Invitación Aceptada!</h2>
          <p>Se ha vinculado exitosamente tu usuario {invitationData?.establecimiento ? 'al establecimiento' : 'al sistema'}.</p>
          <p>Redirigiendo al inicio...</p>
        </div>
      </div>
    );
  }

  if (error || !invitationData) {
    return (
      <div className="accept-invitation-container">
        <div className="background-image" style={{ backgroundImage: `url(${loginImage})` }}></div>
        <div className="background-overlay"></div>
        <div className="accept-invitation-card error-card">
          <img src={logoImg} alt="VigIA Logo" className="logo" />
          <h2>Algo salió mal</h2>
          <div className="error-message">⚠️ {error}</div>
          <button className="back-btn" onClick={() => navigate('/login')}>Ir al Login</button>
        </div>
      </div>
    );
  }

  return (
    <div className="accept-invitation-container">
      <div className="background-image" style={{ backgroundImage: `url(${loginImage})` }}></div>
      <div className="background-overlay"></div>
      <div className="accept-invitation-card">
        <img src={logoImg} alt="VigIA Logo" className="logo" />
        <h2 className="title">
          Te han invitado a unirte a<br />
          <span className="establecimiento-name">{invitationData.establecimiento || 'VigIA'}</span>
        </h2>

        {error && <div className="error-message">⚠️ {error}</div>}

        {isAuthenticated ? (
          loggedInEmail?.toLowerCase() === invitationData.email.toLowerCase() ? (
            <div className="invitation-form" style={{ textAlign: 'center' }}>
              <p style={{ marginBottom: '24px', fontSize: '1.1rem', color: '#c8d9ed' }}>
                Ya iniciaste sesión como <strong>{loggedInEmail}</strong>. ¿Qué deseas hacer con esta invitación?
              </p>
              <div style={{ display: 'flex', gap: '16px', justifyContent: 'center' }}>
                <button 
                  className="submit-btn" 
                  onClick={handleAcceptToken}
                  disabled={isSubmitting}
                  style={{ flex: 1 }}
                >
                  {isSubmitting ? 'Procesando...' : 'Aceptar Invitación'}
                </button>
                <button 
                  className="back-btn" 
                  onClick={() => navigate('/')}
                  disabled={isSubmitting}
                  style={{ flex: 1, margin: 0, backgroundColor: '#e74c3c', color: 'white' }}
                >
                  Rechazar o Ignorar
                </button>
              </div>
            </div>
          ) : (
            <div className="invitation-form" style={{ textAlign: 'center' }}>
              <p style={{ marginBottom: '24px', fontSize: '1.1rem', color: '#c8d9ed' }}>
                Estás logueado como <strong>{loggedInEmail}</strong>, pero esta invitación fue enviada a <strong>{invitationData.email}</strong>.
              </p>
              <p style={{ marginBottom: '24px', fontSize: '1.1rem', color: '#c8d9ed' }}>
                Para poder aceptarla, debés cerrar tu sesión actual.
              </p>
              <button 
                className="submit-btn" 
                onClick={() => logout()}
                style={{ backgroundColor: '#e74c3c', marginTop: '10px' }}
              >
                Cerrar sesión
              </button>
            </div>
          )
        ) : (
          <>
            <p className="login-hint" style={{ marginBottom: '24px', fontSize: '1.1rem', color: '#c8d9ed', textAlign: 'center' }}>
              Registrate con Google o completá tus datos abajo para aceptar la invitación.
            </p>

            <a
              href={googleOAuthUrl}
              className="login-sso-btn"
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px', backgroundColor: 'white', color: '#333', padding: '12px', borderRadius: '8px', textDecoration: 'none', fontWeight: 'bold', border: '1px solid #ddd' }}
            >
              <svg width="20" height="20" viewBox="0 0 24 24">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                <path fill="#FBBC05" d="M5.84 14.1c-.22-.66-.35-1.36-.35-2.1s.13-1.44.35-2.1V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.62z"/>
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
              </svg>
              Registrarse con Google
            </a>

            <div style={{ display: 'flex', alignItems: 'center', margin: '20px 0' }}>
              <div style={{ flex: 1, height: '1px', backgroundColor: '#334155' }}></div>
              <span style={{ padding: '0 10px', color: '#94a3b8', fontSize: '0.9rem' }}>o continuá con correo</span>
              <div style={{ flex: 1, height: '1px', backgroundColor: '#334155' }}></div>
            </div>

            <form onSubmit={handleSubmit(onSubmit)} className="invitation-form" noValidate>
              <div className="form-group">
                <label>Correo</label>
                <input type="text" value={invitationData.email} disabled className="disabled-input" />
              </div>

              <div className="form-group">
                <label>Rol</label>
                <input type="text" value={invitationData.rol ? invitationData.rol.charAt(0).toUpperCase() + invitationData.rol.slice(1).toLowerCase() : ''} disabled className="disabled-input" />
              </div>

              <div className="form-group">
                <label>Nombre</label>
                <div className="input-wrapper">
                  <input
                    type="text"
                    placeholder="Tu nombre"
                    className={errors.nombre ? 'input-error' : ''}
                    {...register('nombre')}
                  />
                  {errors.nombre && <span className="error-text">{errors.nombre.message}</span>}
                </div>
              </div>

              <div className="form-group">
                <label>Apellido</label>
                <div className="input-wrapper">
                  <input
                    type="text"
                    placeholder="Tu apellido"
                    className={errors.apellido ? 'input-error' : ''}
                    {...register('apellido')}
                  />
                  {errors.apellido && <span className="error-text">{errors.apellido.message}</span>}
                </div>
              </div>

              <div className="form-group">
                <label>Contraseña</label>
                <div className="input-wrapper">
                  <input
                    type="password"
                    placeholder="Tu contraseña"
                    className={errors.password ? 'input-error' : ''}
                    {...register('password')}
                  />
                  {errors.password && <span className="error-text">{errors.password.message}</span>}
                </div>
              </div>

              <button type="submit" className="submit-btn" disabled={isSubmitting}>
                {isSubmitting ? (
                  <><span className="spinner small" /> Confirmando...</>
                ) : (
                  'Registrarme y Aceptar'
                )}
              </button>
            </form>
          </>
        )}

        <a href="mailto:soporte@vigia.dev" className="help-link">¿Algún dato es erroneo?</a>

      </div>
    </div>
  );
}
