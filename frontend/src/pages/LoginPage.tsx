import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import { loginRequest } from '../api/authApi';
import { useAuthStore } from '../store/authStore';
import logoImg from '../assets/logo.png';
import loginImage from '../assets/login_image.jpg';
import './LoginPage.css';

const backendBaseUrl = (import.meta.env.VITE_API_URL || '').replace(/\/api\/v1\/?$/, '');
const googleOAuthUrl = `${backendBaseUrl}/oauth2/authorization/google`;

const schema = z.object({
  email: z.string().email('Ingresá un email válido').min(1, 'El email es requerido'),
  password: z.string().min(1, 'La contraseña es requerida'),
});

type FormData = z.infer<typeof schema>;

export default function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const login = useAuthStore((s) => s.login);

  const ssoPendiente = searchParams.get('sso_pendiente') === '1';
  const ssoError = searchParams.get('sso_error');

  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [isLocked, setIsLocked] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const onSubmit = async (data: FormData) => {
    setServerError(null);
    setIsLocked(false);
    setIsLoading(true);

    try {
      const response = await loginRequest(data.email, data.password);
      login(response.token, response.email, response.rol);
      navigate('/');
    } catch (error) {
      if (axios.isAxiosError(error)) {
        if (error.response?.status === 423) {
          setIsLocked(true);
        } else if (error.response?.status === 403) {
          setServerError('La cuenta no está activa. Contactá al administrador.');
        } else if (error.response?.status === 401) {
          setServerError('Credenciales incorrectas. Verificá tu email y contraseña.');
        } else {
          setServerError('Ocurrió un error. Intentá de nuevo más tarde.');
        }
      } else {
        setServerError('No se pudo conectar al servidor.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="login-wrapper">
      {/* ── Panel izquierdo: imagen ── */}
      <div className="login-left">
        <img src={loginImage} alt="Campo agrícola" />
        <div className="login-left-overlay" />
        <div className="login-left-tagline">
          <div className="login-left-tagline-box">
            <p>Lo que no se vigila,</p>
            <p>se pierde</p>
          </div>
        </div>
      </div>

      {/* ── Panel derecho: formulario ── */}
      <div className="login-right">
        <img src={logoImg} alt="VigIA" className="login-logo" />

        <h1 className="login-title">Iniciá sesión en VigIA</h1>

        {/* Alerta de cuenta pendiente de aprobación (SSO) */}
        {ssoPendiente && (
          <div className="login-alert alert-locked" role="alert">
            <span>⏳</span>
            <span>
              Tu cuenta de Google fue registrada correctamente pero está <strong>pendiente de aprobación</strong>.
              Un administrador debe asignarte un rol antes de que puedas acceder.
            </span>
          </div>
        )}

        {/* Alerta de error SSO */}
        {ssoError && (
          <div className="login-alert alert-error" role="alert">
            <span>⚠️</span>
            <span>{ssoError === '1' ? 'Hubo un problema al iniciar sesión con Google. Intentá de nuevo.' : ssoError}</span>
          </div>
        )}

        {/* Alerta de cuenta bloqueada */}
        {isLocked && (
          <div className="login-alert alert-locked" role="alert">
            <span>🔒</span>
            <span>
              Tu cuenta fue bloqueada por múltiples intentos fallidos. Revisá tu correo o
              contactá al administrador.
            </span>
          </div>
        )}

        {/* Alerta de error general */}
        {serverError && (
          <div className="login-alert alert-error" role="alert">
            <span>⚠️</span>
            <span>{serverError}</span>
          </div>
        )}

        <form className="login-form" onSubmit={handleSubmit(onSubmit)} noValidate>
          {/* Email */}
          <div className="login-field">
            <label htmlFor="email">Email</label>
            <div className="login-input-wrapper">
              <input
                id="email"
                type="email"
                placeholder="correo@electronico.com"
                autoComplete="email"
                className={`login-input${errors.email ? ' input-error' : ''}`}
                {...register('email')}
              />
              {!errors.email && (
                <span className="input-icon">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10" />
                    <path d="M9 12l2 2 4-4" />
                  </svg>
                </span>
              )}
            </div>
            {errors.email && (
              <span className="login-error-msg">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
                  <circle cx="12" cy="12" r="10" />
                  <path fill="white" d="M12 8v4m0 4h.01" stroke="white" strokeWidth="2" strokeLinecap="round" />
                </svg>
                {errors.email.message}
              </span>
            )}
          </div>

          {/* Contraseña */}
          <div className="login-field">
            <label htmlFor="password">Contraseña</label>
            <div className="login-input-wrapper">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                placeholder="Contraseña"
                autoComplete="current-password"
                className={`login-input${errors.password ? ' input-error' : ''}`}
                {...register('password')}
              />
              <span className="input-icon">
                <button
                  type="button"
                  className="toggle-password"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                >
                  {showPassword ? (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
                      <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
                      <line x1="1" y1="1" x2="23" y2="23" />
                    </svg>
                  ) : (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                      <circle cx="12" cy="12" r="3" />
                    </svg>
                  )}
                </button>
              </span>
            </div>
            {errors.password && (
              <span className="login-error-msg">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
                  <circle cx="12" cy="12" r="10" />
                  <path fill="white" d="M12 8v4m0 4h.01" stroke="white" strokeWidth="2" strokeLinecap="round" />
                </svg>
                {errors.password.message}
              </span>
            )}
          </div>

          {/* Botón */}
          <button type="submit" className="login-btn" disabled={isLoading} id="btn-login">
            {isLoading ? (
              <>
                <span className="spinner" />
                Ingresando...
              </>
            ) : (
              'Iniciar Sesión'
            )}
          </button>

          <div className="login-divider">
            <span>o continuá con</span>
          </div>

          <a
            href={googleOAuthUrl}
            className="login-sso-btn"
            id="btn-google-sso"
          >
            <svg width="20" height="20" viewBox="0 0 24 24">
              <path
                fill="#4285F4"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                fill="#34A853"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                fill="#FBBC05"
                d="M5.84 14.1c-.22-.66-.35-1.36-.35-2.1s.13-1.44.35-2.1V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.62z"
              />
              <path
                fill="#EA4335"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
              />
            </svg>
            Iniciar sesión con Google
          </a>

          <div className="login-link-container">
            <span style={{ color: '#9aabb8', fontSize: '14px', textAlign: 'center', display: 'block', padding: '0 20px' }}>
              Para registrarte en la plataforma, utilizá el enlace de invitación enviado a tu correo electrónico.
            </span>
          </div>
        </form>
      </div>
    </div>
  );
}
