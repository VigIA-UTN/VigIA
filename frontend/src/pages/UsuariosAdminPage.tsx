import { useState, useEffect } from 'react';
import { Container, Spinner, Alert, Button, Form } from 'react-bootstrap';
import { getTodosUsuarios, toggleEstadoUsuario } from '../api/usuariosApi';
import type { UsuarioResponse } from '../api/usuariosApi';
import { showAlert } from '../store/dialogStore';
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
      showAlert(axiosError.response?.data?.message || 'Error al cambiar el estado del usuario', { variant: 'error' });
    } finally {
      setTogglingId(null);
    }
  };

  const [sortField, setSortField] = useState<string | null>(null);
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

  const handleSort = (field: string) => {
      if (sortField === field) {
          if (sortDirection === 'asc') {
              setSortDirection('desc');
          } else {
              setSortField(null);
              setSortDirection('asc');
          }
      } else {
          setSortField(field);
          setSortDirection('asc');
      }
  };

  const filteredUsuarios = usuarios.filter(u => {
    const matchesSearch = searchTerm === '' || 
        `${u.nombre} ${u.apellido}`.toLowerCase().includes(searchTerm.toLowerCase()) ||
        u.email.toLowerCase().includes(searchTerm.toLowerCase());
    
    const matchesRole = roleFilter === '' || u.rol === roleFilter;

    return matchesSearch && matchesRole;
  });

  const sortedUsuarios = [...filteredUsuarios].sort((a, b) => {
      if (!sortField) return 0;
      let valA = a[sortField as keyof UsuarioResponse] as string | number | null;
      let valB = b[sortField as keyof UsuarioResponse] as string | number | null;

      if (sortField === 'nombre') {
          valA = `${a.nombre} ${a.apellido}`;
          valB = `${b.nombre} ${b.apellido}`;
      }

      if (valA === null || valA === undefined) valA = '';
      if (valB === null || valB === undefined) valB = '';

      if (valA < valB) return sortDirection === 'asc' ? -1 : 1;
      if (valA > valB) return sortDirection === 'asc' ? 1 : -1;
      return 0;
  });

  return (
    <div className="vigia-page-container">
      <Container>
        <div className="vigia-page-header">
          <h2 className="vigia-page-title">Usuarios</h2>
        </div>

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
            <option value="ADMINISTRADOR">Administrador</option>
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
          <div className="vigia-table-wrapper">
            <table className="vigia-table">
              <thead>
                <tr>
                  <th onClick={() => handleSort('nombre')} style={{cursor: 'pointer'}}>Nombre Completo {sortField === 'nombre' && (sortDirection === 'asc' ? '↑' : '↓')}</th>
                  <th onClick={() => handleSort('email')} style={{cursor: 'pointer'}}>Email {sortField === 'email' && (sortDirection === 'asc' ? '↑' : '↓')}</th>
                  <th onClick={() => handleSort('rol')} style={{cursor: 'pointer'}}>Rol {sortField === 'rol' && (sortDirection === 'asc' ? '↑' : '↓')}</th>
                  <th onClick={() => handleSort('activo')} style={{cursor: 'pointer'}}>Estado {sortField === 'activo' && (sortDirection === 'asc' ? '↑' : '↓')}</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {sortedUsuarios.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="text-center py-4 text-muted">
                      No se encontraron usuarios que coincidan con la búsqueda.
                    </td>
                  </tr>
                ) : (
                  sortedUsuarios.map((u) => (
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
