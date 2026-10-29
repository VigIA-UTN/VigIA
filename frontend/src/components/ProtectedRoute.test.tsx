import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import ProtectedRoute from '../components/ProtectedRoute';
import { useAuthStore } from '../store/authStore';
import { resetAuthStore } from '../test/mocks/zustand';

// ── Mocks de dependencias pesadas ─────────────────────────────────────────────

// Navbar dispara muchos efectos internos (WebSocket, etc.) que no son relevantes
// para las pruebas de enrutamiento. Se reemplaza por un stub.
vi.mock('../components/Navbar', () => ({
  default: () => <nav data-testid="navbar">NavBar</nav>,
}));

// El timer de inactividad registra listeners de DOM y llama navigate(); se suprime
// porque probaríamos el hook en su propio test suite, no aquí.
vi.mock('../hooks/useInactivityTimer', () => ({
  useInactivityTimer: () => undefined,
}));

// ── Helper de render ──────────────────────────────────────────────────────────

interface RenderOptions {
  initialEntries?: string[];
  roles?: string[];
}

/**
 * Renderiza <ProtectedRoute> dentro de un MemoryRouter con rutas ficticias para
 * interceptar las redirecciones que produce el componente.
 */
function renderProtectedRoute(
  { initialEntries = ['/dashboard'], roles }: RenderOptions = {},
) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        {/* Ruta protegida */}
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute roles={roles}>
              <p data-testid="protected-content">Contenido protegido</p>
            </ProtectedRoute>
          }
        />
        {/* Destinos de redirección */}
        <Route path="/login" element={<p data-testid="login-page">Login</p>} />
        <Route path="/" element={<p data-testid="home-page">Inicio</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('ProtectedRoute', () => {
  beforeEach(() => {
    // Limpia el estado persistido en localStorage entre tests
    resetAuthStore();
    // Fuerza el estado inicial de Zustand (sin sesión)
    useAuthStore.setState({
      isAuthenticated: false,
      token: null,
      email: null,
      rol: null,
      lastActivity: 0,
    });
  });

  it('redirige a /login cuando no hay sesión activa', () => {
    renderProtectedRoute();
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
  });

  it('muestra el contenido protegido cuando el usuario está autenticado', () => {
    useAuthStore.setState({
      isAuthenticated: true,
      token: 'fake-token',
      email: 'usuario@test.com',
      rol: 'ADMIN',
      lastActivity: Date.now(),
    });

    renderProtectedRoute();
    expect(screen.getByTestId('protected-content')).toBeInTheDocument();
    expect(screen.queryByTestId('login-page')).not.toBeInTheDocument();
  });

  it('muestra el navbar cuando el usuario está autenticado', () => {
    useAuthStore.setState({
      isAuthenticated: true,
      token: 'fake-token',
      email: 'usuario@test.com',
      rol: 'VETERINARIO',
      lastActivity: Date.now(),
    });

    renderProtectedRoute();
    expect(screen.getByTestId('navbar')).toBeInTheDocument();
  });

  it('redirige a / cuando el rol del usuario no está en la lista permitida', () => {
    useAuthStore.setState({
      isAuthenticated: true,
      token: 'fake-token',
      email: 'empleado@test.com',
      rol: 'EMPLEADO',
      lastActivity: Date.now(),
    });

    // Solo admins pueden entrar
    renderProtectedRoute({ roles: ['ADMIN'] });
    expect(screen.getByTestId('home-page')).toBeInTheDocument();
    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
  });

  it('permite el acceso cuando el rol del usuario está en la lista permitida', () => {
    useAuthStore.setState({
      isAuthenticated: true,
      token: 'fake-token',
      email: 'vet@test.com',
      rol: 'VETERINARIO',
      lastActivity: Date.now(),
    });

    renderProtectedRoute({ roles: ['ADMIN', 'VETERINARIO'] });
    expect(screen.getByTestId('protected-content')).toBeInTheDocument();
  });

  it('permite el acceso cuando no se especifican roles (ruta abierta a cualquier autenticado)', () => {
    useAuthStore.setState({
      isAuthenticated: true,
      token: 'fake-token',
      email: 'user@test.com',
      rol: 'EMPLEADO',
      lastActivity: Date.now(),
    });

    // Sin prop `roles` → cualquier autenticado puede entrar
    renderProtectedRoute({ roles: undefined });
    expect(screen.getByTestId('protected-content')).toBeInTheDocument();
  });
});
