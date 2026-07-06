import { create } from 'zustand';
import { persist } from 'zustand/middleware';

const TOKEN_KEY = 'vigia-jwt';

interface AuthState {
  // El token NO se persiste en localStorage; vive solo en sessionStorage
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
      token: sessionStorage.getItem(TOKEN_KEY),
      email: null,
      rol: null,
      isAuthenticated: !!sessionStorage.getItem(TOKEN_KEY),
      lastActivity: Date.now(),

      login: (token, email, rol) => {
        sessionStorage.setItem(TOKEN_KEY, token);
        set({ token, email, rol, isAuthenticated: true, lastActivity: Date.now() });
      },

      logout: () => {
        sessionStorage.removeItem(TOKEN_KEY);
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
      // Solo persiste email, rol y lastActivity — el token va a sessionStorage
      partialize: (state) => ({
        email: state.email,
        rol: state.rol,
        lastActivity: state.lastActivity,
      }),
      // Al rehidratar, reconstruir isAuthenticated y token desde sessionStorage
      onRehydrateStorage: () => (state) => {
        if (state) {
          const token = sessionStorage.getItem(TOKEN_KEY);
          state.token = token;
          state.isAuthenticated = !!token;
        }
      },
    }
  )
);
