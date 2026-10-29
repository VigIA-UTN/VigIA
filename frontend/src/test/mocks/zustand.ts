/**
 * Utilidad para resetear stores de Zustand entre tests.
 * Zustand con `persist` usa localStorage/sessionStorage, que en jsdom persiste
 * entre tests dentro del mismo suite. Se resetea manualmente antes de cada test.
 */
export function resetAuthStore() {
  localStorage.removeItem('vigia-auth');
  sessionStorage.clear();
}
