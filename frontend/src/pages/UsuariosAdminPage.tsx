import { useState, useEffect } from 'react';
import { Container, Spinner, Alert, Button, Form } from 'react-bootstrap';
import { getTodosUsuarios, toggleEstadoUsuario } from '../api/usuariosApi';
import type { UsuarioResponse } from '../api/usuariosApi';
import './UsuariosAdminPage.css';

export default function UsuariosAdminPage() {
  const [usuarios, setUsuarios] = useState<UsuarioResponse[]>([]);
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
        const data = await getTodosUsuarios();
        setUsuarios(data);
      } catch (err) {
        const axiosError = err as { response?: { data?: { message?: string } } };
        setError(axiosError.response?.data?.message || 'Error al cargar usuarios');
      } finally {
        setLoading(false);
      }
    };

    fetchUsuarios();
  }, []);

  const handleToggleEstado = async (id: number) => {
    try {
      setTogglingId(id);
      await toggleEstadoUsuario(id);
      setUsuarios(prev => prev.map(u => u.id === id ? { ...u, activo: !u.activo } : u));
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      alert(axiosError.response?.data?.message || 'Error al cambiar el estado del usuario');
    } finally {
      setTogglingId(null);
    }
  };

  const filteredUsuarios = usuarios.filter(u => {
    const matchesSearch = searchTerm === '' || 
        `${u.nombre} ${u.apellido}`.toLowerCase().includes(searchTerm.toLowerCase()) ||
        u.email.toLowerCase().includes(searchTerm.toLowerCase());
    
    const matchesRole = roleFilter === '' || u.rol === roleFilter;

    return matchesSearch && matchesRole;
  });

  return (
    <div className="usuarios-admin-container">
      <Container>
        <h2 className="usuarios-admin-title">Usuarios</h2>

        <div className="d-flex flex-column flex-md-row justify-content-between mb-4 gap-3">
          <Form.Control
            type="text"
            placeholder="Buscar por nombre o email..."
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
          <div className="usuarios-table-wrapper">
            <table className="usuarios-table">
              <thead>
                <tr>
                  <th>Nombre Completo</th>
                  <th>Email</th>
                  <th>Rol</th>
                  <th>Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {filteredUsuarios.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="text-center py-4 text-muted">
                      No se encontraron usuarios que coincidan con la búsqueda.
                    </td>
                  </tr>
                ) : (
                  filteredUsuarios.map((u) => (
                    <tr key={u.id}>
                      <td className="fw-medium text-dark">{u.nombre} {u.apellido}</td>
                      <td>
                        <a href={`mailto:${u.email}`} className="text-decoration-none text-muted">
                          {u.email}
                        </a>
                      </td>
                      <td>{u.rol}</td>
                      <td>
                        <span className={`estado-badge ${u.activo ? 'badge-activo' : 'badge-inactivo'}`}>
                          {u.activo ? 'ACTIVO' : 'INACTIVO'}
                        </span>
                      </td>
                      <td>
                        <Button
                          variant={u.activo ? 'outline-danger' : 'outline-success'}
                          size="sm"
                          className="border-0 fw-semibold"
                          onClick={() => handleToggleEstado(u.id)}
                          disabled={togglingId === u.id}
                        >
                          {togglingId === u.id ? (
                            <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" />
                          ) : (
                            u.activo ? 'Desactivar' : 'Activar'
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
