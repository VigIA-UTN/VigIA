import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  token: string | null;
  email: string | null;
  rol: string | null;
  isAuthenticated: boolean;
  login: (token: string, email: string, rol: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      email: null,
      rol: null,
      isAuthenticated: false,
      login: (token, email, rol) =>
        set({ token, email, rol, isAuthenticated: true }),
      logout: () =>
        set({ token: null, email: null, rol: null, isAuthenticated: false }),
    }),
    {
      name: 'vigia-auth',
    }
  )
);
