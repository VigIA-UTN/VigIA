import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useInactivityTimer } from '../hooks/useInactivityTimer';
import NavScroll from './Navbar';

interface ProtectedRouteProps {
  roles?: string[];
  children: React.ReactNode;
}

export default function ProtectedRoute({ roles, children }: ProtectedRouteProps) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  // El timer solo corre cuando el usuario está autenticado y en una ruta privada
  useInactivityTimer();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const rol = useAuthStore((s) => s.rol);

  if (roles && !roles.includes(rol)) {
    return <Navigate to="/" replace />;
  }

  return <><NavScroll /> {children}</>;
}
