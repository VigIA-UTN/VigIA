import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { registerRequest } from '../api/authApi';
import { useAuthStore } from '../store/authStore';
import logoImg from '../assets/logo.png';
import loginImage from '../assets/login_image.jpg';
import './RegisterPage.css';

const schema = z.object({
    nombre: z.string().min(1, 'El nombre es requerido'),
    apellido: z.string().min(1, 'El apellido es requerido'),
    email: z.string().email('Ingresá un email válido').min(1, 'El email es requerido'),
    password: z.string().min(1, 'La contraseña es requerida'),
    token: z.string().min(1, 'El código de invitación es requerido'),
});

type FormData = z.infer<typeof schema>;

export default function RegisterPage() {
    const navigate = useNavigate();
    const login = useAuthStore((s) => s.login);

    const [showPassword, setShowPassword] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [serverError, setServerError] = useState<string | null>(null);

    const {
        register,
        handleSubmit,
        formState: { errors },
    } = useForm<FormData>({
        resolver: zodResolver(schema),
    });

    const onSubmit = async (data: FormData) => {
        setServerError(null);
        setIsLoading(true);

        try {
            const response = await registerRequest(data.nombre, data.apellido, data.email, data.password, data.token);
            console.log(response);
            login(response.token, response.email, response.rol);
            navigate('/');
        } catch (error) {
            if (axios.isAxiosError(error)) {
                console.log(error);
                if (error.response?.status === 400) {
                    setServerError('Credenciales incorrectas. Verificá tu email y código de invitación.');
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
        <div className="register-wrapper">
            {/* ── Panel izquierdo: imagen ── */}
            <div className="register-left">
                <img src={loginImage} alt="Campo agrícola" />
                <div className="register-left-overlay" />
                <div className="register-left-tagline">
                    <div className="register-left-tagline-box">
                        <p>Lo que no se vigila,</p>
                        <p>se pierde</p>
                    </div>
                </div>
            </div>

            {/* ── Panel derecho: formulario ── */}
            <div className="register-right">
                <img src={logoImg} alt="VigIA" className="register-logo" />

                <h1 className="register-title">Registrate en VigIA</h1>

                {/* Alerta de error general */}
                {serverError && (
                    <div className="register-alert alert-error" role="alert">
                        <span>⚠️</span>
                        <span>{serverError}</span>
                    </div>
                )}

                <form className="register-form" onSubmit={handleSubmit(onSubmit)} noValidate>
                    {/* Nombre */}
                    <div className="register-field">
                        <label htmlFor="nombre">Nombre</label>
                        <div className="register-input-wrapper">
                            <input
                                id="nombre"
                                type="text"
                                placeholder="Nombre"
                                autoComplete="nombre"
                                className={`register-input${errors.nombre ? ' input-error' : ''}`}
                                {...register('nombre')}
                            />
                            {!errors.nombre && (
                                <span className="input-icon">
                                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                        <circle cx="12" cy="12" r="10" />
                                        <path d="M9 12l2 2 4-4" />
                                    </svg>
                                </span>
                            )}
                        </div>
                        {errors.nombre && (
                            <span className="register-error-msg">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
                                    <circle cx="12" cy="12" r="10" />
                                    <path fill="white" d="M12 8v4m0 4h.01" stroke="white" strokeWidth="2" strokeLinecap="round" />
                                </svg>
                                {errors.nombre.message}
                            </span>
                        )}
                    </div>
                    {/* Apellido */}
                    <div className="register-field">
                        <label htmlFor="apellido">Apellido</label>
                        <div className="register-input-wrapper">
                            <input
                                id="apellido"
                                type="text"
                                placeholder="Apellido"
                                autoComplete="apellido"
                                className={`register-input${errors.apellido ? ' input-error' : ''}`}
                                {...register('apellido')}
                            />
                            {!errors.apellido && (
                                <span className="input-icon">
                                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                        <circle cx="12" cy="12" r="10" />
                                        <path d="M9 12l2 2 4-4" />
                                    </svg>
                                </span>
                            )}
                        </div>
                        {errors.apellido && (
                            <span className="register-error-msg">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
                                    <circle cx="12" cy="12" r="10" />
                                    <path fill="white" d="M12 8v4m0 4h.01" stroke="white" strokeWidth="2" strokeLinecap="round" />
                                </svg>
                                {errors.apellido.message}
                            </span>
                        )}
                    </div>

                    {/* Email */}
                    <div className="register-field">
                        <label htmlFor="email">Email</label>
                        <div className="register-input-wrapper">
                            <input
                                id="email"
                                type="email"
                                placeholder="correo@electronico.com"
                                autoComplete="email"
                                className={`register-input${errors.email ? ' input-error' : ''}`}
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
                            <span className="register-error-msg">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
                                    <circle cx="12" cy="12" r="10" />
                                    <path fill="white" d="M12 8v4m0 4h.01" stroke="white" strokeWidth="2" strokeLinecap="round" />
                                </svg>
                                {errors.email.message}
                            </span>
                        )}
                    </div>

                    {/* Contraseña */}
                    <div className="register-field">
                        <label htmlFor="password">Contraseña</label>
                        <div className="register-input-wrapper">
                            <input
                                id="password"
                                type={showPassword ? 'text' : 'password'}
                                placeholder="Contraseña"
                                autoComplete="current-password"
                                className={`register-input${errors.password ? ' input-error' : ''}`}
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
                            <span className="register-error-msg">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
                                    <circle cx="12" cy="12" r="10" />
                                    <path fill="white" d="M12 8v4m0 4h.01" stroke="white" strokeWidth="2" strokeLinecap="round" />
                                </svg>
                                {errors.password.message}
                            </span>
                        )}
                    </div>
                    <div className="register-field">
                        <label htmlFor="invitationCode">Código invitación</label>
                        <div className="register-input-wrapper">
                            <input
                                id="invitationCode"
                                type="text"
                                placeholder="Código de invitación"
                                autoComplete="invitationCode"
                                className={`register-input${errors.token ? ' input-error' : ''}`}
                                {...register('token')}
                            />
                            {!errors.token && (
                                <span className="input-icon">
                                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                        <circle cx="12" cy="12" r="10" />
                                        <path d="M9 12l2 2 4-4" />
                                    </svg>
                                </span>
                            )}
                        </div>
                        {errors.token && (
                            <span className="register-error-msg">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
                                    <circle cx="12" cy="12" r="10" />
                                    <path fill="white" d="M12 8v4m0 4h.01" stroke="white" strokeWidth="2" strokeLinecap="round" />
                                </svg>
                                {errors.token.message}
                            </span>
                        )}
                    </div>

                    <button type="submit" className="register-btn" disabled={isLoading} id="btn-register">
                        {isLoading ? (
                            <>
                                <span className="spinner" />
                                Registrándose...
                            </>
                        ) : (
                            'Registrarse'
                        )}
                    </button>
                    <div className="register-link-container">
                        <Link to="/login" className='register-link'>¿Ya tenes cuenta? Inicia sesión</Link>
                    </div>
                </form>
            </div>
        </div>
    );
}
