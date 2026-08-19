import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import MapPage from './pages/MapPage';
import ProtectedRoute from './components/ProtectedRoute';
import EstablecimientosPage from './pages/EstablecimientosPage';
import InvitacionesPage from './pages/InvitacionesPage';
import InvitacionesEmpleadosPage from './pages/InvitacionesEmpleadosPage';
import InvitacionesPendientesPage from './pages/InvitacionesPendientesPage';
import RegisterPage from './pages/RegisterPage';
import AcceptInvitationPage from './pages/AcceptInvitationPage';
import OAuth2CallbackPage from './pages/OAuth2CallbackPage';
import UsuariosAdminPage from './pages/UsuariosAdminPage';
import { useAuthStore } from './store/authStore';
import EmpleadosPage from './pages/EmpleadosPage';
import AnimalesPage from './pages/AnimalesPage';
import AnimalDetailPage from './pages/AnimalDetailPage';
import AlertasPage from './pages/AlertasPage';
import DashboardPage from './pages/DashboardPage';

function InvitacionesRouter() {
  const rol = useAuthStore(state => state.rol);
  if (rol === 'ADMINISTRADOR') return <InvitacionesPage />;
  if (rol === 'PRODUCTOR') return <InvitacionesEmpleadosPage />;
  if (rol === 'VETERINARIO' || rol === 'CAMPERO' || rol === 'PENDIENTE') return <InvitacionesPendientesPage />;
  return <Navigate to="/" replace />;
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/aceptar-invitacion" element={<AcceptInvitationPage />} />
        <Route path="/oauth2/callback" element={<OAuth2CallbackPage />} />
        <Route
          path="/mapa"
          element={
            <ProtectedRoute>
              <MapPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute roles={['PRODUCTOR', 'VETERINARIO', 'CAMPERO']}>
              <DashboardPage />
            </ProtectedRoute>
          }
        />
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route
          path="/establecimientos"
          element={
            <ProtectedRoute roles={['ADMINISTRADOR', 'PRODUCTOR']}>
              <EstablecimientosPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/invitaciones"
          element={
            <ProtectedRoute roles={['ADMINISTRADOR', 'PRODUCTOR', 'VETERINARIO', 'CAMPERO', 'PENDIENTE']}>
              <InvitacionesRouter />
            </ProtectedRoute>
          }
        />
        <Route
          path="/usuarios"
          element={
            <ProtectedRoute roles={['ADMINISTRADOR']}>
              <UsuariosAdminPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/empleados"
          element={
            <ProtectedRoute roles={['PRODUCTOR']}>
              <EmpleadosPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/animales"
          element={
            <ProtectedRoute roles={['PRODUCTOR', 'VETERINARIO', 'CAMPERO']}>
              <AnimalesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/animales/:id"
          element={
            <ProtectedRoute roles={['PRODUCTOR', 'VETERINARIO', 'CAMPERO']}>
              <AnimalDetailPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/alertas"
          element={
            <ProtectedRoute roles={['PRODUCTOR', 'VETERINARIO', 'CAMPERO']}>
              <AlertasPage />
            </ProtectedRoute>
          }
        />
        {/* Cualquier ruta no encontrada redirige al root */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
