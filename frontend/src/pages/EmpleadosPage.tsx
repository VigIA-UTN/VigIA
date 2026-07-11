import { useState, useEffect } from 'react';
import { Container, Spinner, Alert, Button, Form } from 'react-bootstrap';
import { getEmpleados, removerEmpleado } from '../api/usuariosApi';
import type { UsuarioResponse } from '../api/usuariosApi';
import './EmpleadosPage.css';

export default function EmpleadosPage() {
    const [empleados, setEmpleados] = useState<UsuarioResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [togglingId, setTogglingId] = useState<number | null>(null);

    const [searchTerm, setSearchTerm] = useState('');
    const [roleFilter, setRoleFilter] = useState('');

    useEffect(() => {
        const fetchUsuarios = async () => {
            try {
                setLoading(true);
                setError(null);
                const data = await getEmpleados();
                setEmpleados(data);
            } catch (err) {
                const axiosError = err as { response?: { data?: { message?: string } } };
                setError(axiosError.response?.data?.message || 'Error al cargar empleados');
            } finally {
                setLoading(false);
            }
        };

        fetchUsuarios();
    }, []);

    const handleRemoverEmpleado = async (id: number, establecimientoId: number) => {
        if (!window.confirm('¿Estás seguro de que querés quitar a este empleado del establecimiento?')) {
            return;
        }

        try {
            setTogglingId(id);
            await removerEmpleado(id, establecimientoId);
            setEmpleados(prev => prev.filter(e => !(e.id === id && e.establecimientoId === establecimientoId)));
        } catch (err) {
            const axiosError = err as { response?: { data?: { message?: string } } };
            alert(axiosError.response?.data?.message || 'Error al remover el empleado');
        } finally {
            setTogglingId(null);
        }
    };

    const filteredEmpleados = empleados.filter(e => {
        const matchesSearch = searchTerm === '' ||
            `${e.nombre} ${e.apellido}`.toLowerCase().includes(searchTerm.toLowerCase()) ||
            e.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
            (e.establecimiento && e.establecimiento.toLowerCase().includes(searchTerm.toLowerCase()));

        const matchesRole = roleFilter === '' || e.rol === roleFilter;

        return matchesSearch && matchesRole;
    });

    return (
        <div className="empleados-container">
            <Container>
                <h2 className="empleados-title">Empleados</h2>

                <div className="d-flex flex-column flex-md-row justify-content-between mb-4 gap-3">
                    <Form.Control
                        type="text"
                        placeholder="Buscar por nombre, email o establecimiento..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        style={{ maxWidth: '400px' }}
                    />
                    <Form.Select
                        value={roleFilter}
                        onChange={(e) => setRoleFilter(e.target.value)}
                        style={{ maxWidth: '250px' }}
                    >
                        <option value="">Todos los roles</option>
                        <option value="PRODUCTOR">Productor</option>
                        <option value="VETERINARIO">Veterinario</option>
                        <option value="CAMPERO">Campero</option>
                    </Form.Select>
                </div>

                {error && <Alert variant="danger">{error}</Alert>}

                {loading ? (
                    <div className="text-center mt-5">
                        <Spinner animation="border" style={{ color: '#1E3A5F' }} />
                    </div>
                ) : (
                    <div className="empleados-table-wrapper">
                        <table className="empleados-table">
                            <thead>
                                <tr>
                                    <th>Nombre Completo</th>
                                    <th>Email</th>
                                    <th>Rol</th>
                                    <th>Establecimiento</th>
                                    <th>Acciones</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredEmpleados.length === 0 ? (
                                    <tr>
                                        <td colSpan={5} className="text-center py-4 text-muted">
                                            No se encontraron empleados que coincidan con la búsqueda.
                                        </td>
                                    </tr>
                                ) : (
                                    filteredEmpleados.map((u) => (
                                        <tr key={`${u.id}-${u.establecimientoId}`}>
                                            <td className="fw-medium text-dark">{u.nombre} {u.apellido}</td>
                                            <td>
                                                <a href={`mailto:${u.email}`} className="text-decoration-none text-muted">
                                                    {u.email}
                                                </a>
                                            </td>
                                            <td>{u.rol}</td>
                                            <td>{u.establecimiento}</td>
                                            <td>
                                                <Button
                                                    variant="outline-danger"
                                                    size="sm"
                                                    className="border-0 fw-semibold"
                                                    onClick={() => u.establecimientoId && handleRemoverEmpleado(u.id, u.establecimientoId)}
                                                    disabled={togglingId === u.id}
                                                >
                                                    {togglingId === u.id ? (
                                                        <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" />
                                                    ) : (
                                                        'Quitar'
                                                    )}
                                                </Button>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                )}
            </Container>
        </div>
    );
}
