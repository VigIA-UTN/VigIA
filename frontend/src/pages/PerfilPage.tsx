import { useState, useEffect } from 'react';
import { Spinner, Alert, Form, Button } from 'react-bootstrap';
import { getMiPerfil, updateMiPerfil, cambiarPassword, type UsuarioResponse } from '../api/usuariosApi';
import './PerfilPage.css';

export default function PerfilPage() {
    const [loading, setLoading] = useState(true);
    const [savingPerfil, setSavingPerfil] = useState(false);
    const [savingPassword, setSavingPassword] = useState(false);

    const [error, setError] = useState<string | null>(null);
    const [successMsg, setSuccessMsg] = useState<string | null>(null);
    const [pwdError, setPwdError] = useState<string | null>(null);
    const [pwdSuccess, setPwdSuccess] = useState<string | null>(null);

    const [perfil, setPerfil] = useState<UsuarioResponse | null>(null);

    // Form fields
    const [nombre, setNombre] = useState('');
    const [apellido, setApellido] = useState('');

    // Password fields
    const [passwordActual, setPasswordActual] = useState('');
    const [nuevaPassword, setNuevaPassword] = useState('');
    const [confirmarPassword, setConfirmarPassword] = useState('');

    useEffect(() => {
        const fetchPerfil = async () => {
            try {
                const data = await getMiPerfil();
                setPerfil(data);
                setNombre(data.nombre || '');
                setApellido(data.apellido || '');
            } catch (err: unknown) {
                const error = err as { response?: { data?: { message?: string } } };
                setError(error.response?.data?.message || 'Error al cargar tu perfil.');
            } finally {
                setLoading(false);
            }
        };

        fetchPerfil();
    }, []);

    const handleGuardarPerfil = async (e: React.FormEvent) => {
        e.preventDefault();
        setSavingPerfil(true);
        setError(null);
        setSuccessMsg(null);
        try {
            const data = await updateMiPerfil({ nombre, apellido });
            setPerfil(data);
            setSuccessMsg('Perfil actualizado correctamente.');
            setTimeout(() => setSuccessMsg(null), 3000);
        } catch (err: unknown) {
            const error = err as { response?: { data?: { message?: string } } };
            setError(error.response?.data?.message || 'Error al actualizar perfil.');
        } finally {
            setSavingPerfil(false);
        }
    };

    const handleCambiarPassword = async (e: React.FormEvent) => {
        e.preventDefault();
        setPwdError(null);
        setPwdSuccess(null);

        if (nuevaPassword !== confirmarPassword) {
            setPwdError('Las contraseñas no coinciden.');
            return;
        }

        setSavingPassword(true);
        try {
            await cambiarPassword({ passwordActual, nuevaPassword });
            setPwdSuccess('Contraseña cambiada exitosamente.');
            setPasswordActual('');
            setNuevaPassword('');
            setConfirmarPassword('');
            setTimeout(() => setPwdSuccess(null), 3000);
        } catch (err: unknown) {
            const error = err as { response?: { data?: { message?: string } } };
            setPwdError(error.response?.data?.message || 'Error al cambiar contraseña.');
        } finally {
            setSavingPassword(false);
        }
    };

    if (loading) {
        return (
            <div className="perfil-page d-flex justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
                <Spinner animation="border" style={{ color: '#1E3A5F' }} />
            </div>
        );
    }

    if (!perfil) {
        return (
            <div className="perfil-page">
                <Alert variant="danger">No se pudo cargar la información del perfil.</Alert>
            </div>
        );
    }

    const isSsoUser = perfil.proveedorAuth && perfil.proveedorAuth !== 'LOCAL';

    return (
        <div className="perfil-page">
            <div className="perfil-container">
                <h2 className="perfil-title">Mi Perfil</h2>
                <p className="perfil-subtitle">Gestioná tu información personal y de seguridad.</p>

                <div className="perfil-grid">
                    {/* Tarjeta de Datos Personales */}
                    <div className="perfil-card">
                        <div className="perfil-card-header">
                            <i className="bi bi-person-circle" />
                            <h5>Datos Personales</h5>
                        </div>
                        <div className="perfil-card-body">
                            {error && <Alert variant="danger" className="py-2 text-sm">{error}</Alert>}
                            {successMsg && <Alert variant="success" className="py-2 text-sm">{successMsg}</Alert>}

                            <Form onSubmit={handleGuardarPerfil}>
                                <Form.Group className="mb-3">
                                    <Form.Label>Correo Electrónico</Form.Label>
                                    <Form.Control
                                        type="email"
                                        value={perfil.email}
                                        disabled
                                        className="bg-light"
                                    />
                                    <Form.Text className="text-muted">
                                        El correo está asociado a tu cuenta y no puede modificarse.
                                    </Form.Text>
                                </Form.Group>

                                <div className="row">
                                    <div className="col-md-6 mb-3">
                                        <Form.Group>
                                            <Form.Label>Nombre</Form.Label>
                                            <Form.Control
                                                type="text"
                                                value={nombre}
                                                onChange={e => setNombre(e.target.value)}
                                                required
                                            />
                                        </Form.Group>
                                    </div>
                                    <div className="col-md-6 mb-3">
                                        <Form.Group>
                                            <Form.Label>Apellido</Form.Label>
                                            <Form.Control
                                                type="text"
                                                value={apellido}
                                                onChange={e => setApellido(e.target.value)}
                                            />
                                        </Form.Group>
                                    </div>
                                </div>

                                <Form.Group className="mb-3">
                                    <Form.Label>Rol en VigIA</Form.Label>
                                    <Form.Control
                                        type="text"
                                        value={perfil.rol}
                                        disabled
                                        className="bg-light fw-bold"
                                        style={{ color: '#1E3A5F' }}
                                    />
                                </Form.Group>

                                <div className="d-flex justify-content-end mt-4">
                                    <Button type="submit" className="btn-vigia-primary" disabled={savingPerfil}>
                                        {savingPerfil ? <Spinner size="sm" animation="border" /> : 'Guardar Cambios'}
                                    </Button>
                                </div>
                            </Form>
                        </div>
                    </div>

                    {/* Tarjeta de Seguridad */}
                    <div className="perfil-card">
                        <div className="perfil-card-header">
                            <i className="bi bi-shield-lock" />
                            <h5>Seguridad</h5>
                        </div>
                        <div className="perfil-card-body">
                            {isSsoUser ? (
                                <div className="text-center py-4">
                                    <i className="bi bi-google mb-3" style={{ fontSize: '2.5rem', color: '#DB4437' }}></i>
                                    <h6>Inicio de sesión con Google</h6>
                                    <p className="text-muted text-sm mt-2">
                                        Ingresaste mediante SSO (Google). La gestión de tu contraseña se realiza directamente desde tu cuenta de Google.
                                    </p>
                                </div>
                            ) : (
                                <>
                                    {pwdError && <Alert variant="danger" className="py-2 text-sm">{pwdError}</Alert>}
                                    {pwdSuccess && <Alert variant="success" className="py-2 text-sm">{pwdSuccess}</Alert>}
                                    <Form onSubmit={handleCambiarPassword}>
                                        <Form.Group className="mb-3">
                                            <Form.Label>Contraseña Actual</Form.Label>
                                            <Form.Control
                                                type="password"
                                                value={passwordActual}
                                                onChange={e => setPasswordActual(e.target.value)}
                                                required
                                            />
                                        </Form.Group>

                                        <Form.Group className="mb-3">
                                            <Form.Label>Nueva Contraseña</Form.Label>
                                            <Form.Control
                                                type="password"
                                                value={nuevaPassword}
                                                onChange={e => setNuevaPassword(e.target.value)}
                                                required
                                            />
                                            <Form.Text className="text-muted text-sm d-block mt-1">
                                                Mínimo 8 caracteres, incluyendo mayúscula, minúscula y un número o símbolo.
                                            </Form.Text>
                                        </Form.Group>

                                        <Form.Group className="mb-3">
                                            <Form.Label>Confirmar Nueva Contraseña</Form.Label>
                                            <Form.Control
                                                type="password"
                                                value={confirmarPassword}
                                                onChange={e => setConfirmarPassword(e.target.value)}
                                                required
                                            />
                                        </Form.Group>

                                        <div className="d-flex justify-content-end mt-4">
                                            <Button type="submit" variant="vigia-outline" disabled={savingPassword}>
                                                {savingPassword ? <Spinner size="sm" animation="border" /> : 'Actualizar Contraseña'}
                                            </Button>
                                        </div>
                                    </Form>
                                </>
                            )}
                        </div>
                    </div>
                </div>


            </div>
        </div>
    );
}
