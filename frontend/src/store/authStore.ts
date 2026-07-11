import { create } from 'zustand';
import { persist } from 'zustand/middleware';

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
      token: (() => { try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; } })(),
      email: null,
      rol: null,
      isAuthenticated: (() => { try { return !!sessionStorage.getItem(TOKEN_KEY); } catch { return false; } })(),
      lastActivity: Date.now(),

      login: (token, email, rol) => {
        try { sessionStorage.setItem(TOKEN_KEY, token); } catch { /* ignore */ }
        set({ token, email, rol, isAuthenticated: true, lastActivity: Date.now() });
      },

      logout: () => {
        try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ }
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
      partialize: (state) => ({
        email: state.email,
        rol: state.rol,
        lastActivity: state.lastActivity,
      }),
      onRehydrateStorage: () => (_state, error) => {
        if (!error) {
          let token = null;
          try { token = sessionStorage.getItem(TOKEN_KEY); } catch { /* ignore */ }
          useAuthStore.setState({ token, isAuthenticated: !!token });
        }
      },
    }
  )
);
