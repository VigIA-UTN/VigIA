import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useInactivityTimer } from '../hooks/useInactivityTimer';

interface ProtectedRouteProps {
  children: React.ReactNode;
}

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  // El timer solo corre cuando el usuario está autenticado y en una ruta privada
  useInactivityTimer();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
