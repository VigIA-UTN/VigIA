import { useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';

const INACTIVITY_LIMIT_MS = 60 * 60 * 1000; // 60 minutos

const ACTIVITY_EVENTS: (keyof WindowEventMap)[] = [
  'mousemove',
  'keydown',
  'click',
  'touchstart',
  'scroll',
];

/**
 * Cierra la sesión automáticamente tras 60 minutos de inactividad del usuario.
 * Debe montarse únicamente dentro de rutas protegidas.
 */
export function useInactivityTimer() {
  const logout = useAuthStore((s) => s.logout);
  const updateActivity = useAuthStore((s) => s.updateActivity);
  const navigate = useNavigate();
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const resetTimer = useCallback(() => {
    updateActivity();

    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }

    timerRef.current = setTimeout(() => {
      logout();
      navigate('/login', { replace: true });
    }, INACTIVITY_LIMIT_MS);
  }, [logout, navigate, updateActivity]);

  useEffect(() => {
    // Arranca el timer inicial al montar el componente
    resetTimer();

    // Registra todos los eventos de actividad
    ACTIVITY_EVENTS.forEach((event) =>
      window.addEventListener(event, resetTimer, { passive: true })
    );

    return () => {
      // Limpia al desmontar
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      ACTIVITY_EVENTS.forEach((event) =>
        window.removeEventListener(event, resetTimer)
      );
    };
  }, [resetTimer]);
}
