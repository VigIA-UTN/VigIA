import '@testing-library/jest-dom/vitest';

/**
 * matchMedia no existe en jsdom. Se registra aquí (antes de cualquier import
 * de módulo) para que componentes como CohesionIndiceCard que lo usan en
 * scope de módulo no fallen.
 */
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});
