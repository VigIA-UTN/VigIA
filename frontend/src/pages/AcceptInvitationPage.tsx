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
          <p>Se ha vinculado exitosamente tu usuario al establecimiento.</p>
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
          <span className="establecimiento-name">{invitationData.establecimiento}</span>
        </h2>

        {error && <div className="error-message">⚠️ {error}</div>}

        {isAuthenticated ? (
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
          <>
            <p className="login-hint" style={{ marginBottom: '24px', fontSize: '1.1rem', color: '#c8d9ed', textAlign: 'center' }}>
              Completá tus datos abajo para registrarte y aceptar la invitación.
            </p>

            <form onSubmit={handleSubmit(onSubmit)} className="invitation-form" noValidate>
              <div className="form-group">
                <label>Correo</label>
                <input type="text" value={invitationData.email} disabled className="disabled-input" />
              </div>

              <div className="form-group">
                <label>Rol</label>
                <input type="text" value={invitationData.rol} disabled className="disabled-input" />
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
