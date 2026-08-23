import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { useEstablecimientoStore } from './establecimientoStore';

const TOKEN_KEY = 'vigia-jwt';

interface AuthState {
  token: string | null;
  email: string | null;
  rol: string | null;
  isAuthenticated: boolean;
  lastActivity: number;

  login: (token: string, email: string, rol: string) => void;
  logout: () => void;
  updateActivity: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      email: null,
      rol: null,
      isAuthenticated: false,
      lastActivity: Date.now(),

      login: (token, email, rol) => {
        // Guardar en sessionStorage como respaldo
        try { sessionStorage.setItem(TOKEN_KEY, token); } catch { /* ignore */ }
        set({ token, email, rol, isAuthenticated: true, lastActivity: Date.now() });
      },

      logout: () => {
        try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ }
        useEstablecimientoStore.getState().clear();
        set({
          token: null,
          email: null,
          rol: null,
          isAuthenticated: false,
          lastActivity: 0,
        });
      },

      updateActivity: () => set({ lastActivity: Date.now() }),
    }),
    {
      name: 'vigia-auth',
      // Ahora el token también se persiste en localStorage para que
      // el axiosClient siempre lo tenga disponible tras recargar la página.
      partialize: (state) => ({
        token: state.token,
        email: state.email,
        rol: state.rol,
        isAuthenticated: state.isAuthenticated,
        lastActivity: state.lastActivity,
      }),
    }
  )
);
