import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { loginRequest } from '../api/authApi';
import { useAuthStore } from '../store/authStore';
import logoImg from '../assets/logo.png';
import loginImage from '../assets/login_image.jpg';
import './LoginPage.css';

const schema = z.object({
  email: z.string().email('Ingresá un email válido').min(1, 'El email es requerido'),
  password: z.string().min(1, 'La contraseña es requerida'),
});

type FormData = z.infer<typeof schema>;

export default function LoginPage() {
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);

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
          <div className="login-link-container">
            <Link to="/register" className='login-link'>¿Contas con una de invitación? Registrate aquí</Link>
          </div>
        </form>
      </div>
    </div>
  );
}
