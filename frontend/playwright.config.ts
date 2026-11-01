import process from 'node:process';
import { defineConfig, devices } from '@playwright/test';

/**
 * Configuración de Playwright para VigIA E2E tests.
 *
 * Corre contra el stack completo levantado en Docker (backend, base de datos, servicios ML)
 * y el frontend servido por Vite.
 */
const PORT = process.env.PORT || 5173;
const BASE_URL = process.env.PLAYWRIGHT_BASE_URL || `http://localhost:${PORT}`;

export default defineConfig({
  testDir: './e2e',
  /* Ejecutar tests en archivos en paralelo */
  fullyParallel: false,
  /* Fallar la build en CI si olvidamos test.only */
  forbidOnly: !!process.env.CI,
  /* Reintentos en CI */
  retries: process.env.CI ? 1 : 0,
  /* Workers en CI */
  workers: process.env.CI ? 1 : 2,
  /* Timeout por test */
  timeout: 30 * 1000,
  expect: {
    timeout: 10 * 1000,
  },
  /* Reporter HTML y list, publicando reportes y traces */
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  outputDir: 'test-results',

  use: {
    baseURL: BASE_URL,
    /* Recolecta trace en fallos para inspección posterior */
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    /* Permite seleccionar elementos por test-id si fuera necesario */
    testIdAttribute: 'data-testid',
  },

  /* Proyectos configurados para los navegadores principales */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    {
      name: 'edge',
      use: {
        ...devices['Desktop Edge'],
        ...(process.env.PLAYWRIGHT_CHANNEL_EDGE ? { channel: 'msedge' } : {}),
      },
    },
  ],

  /* Levantar automáticamente el dev server si no está corriendo */
  webServer: process.env.PLAYWRIGHT_BASE_URL
    ? undefined
    : {
        // --host :: hace que Vite escuche en IPv4 e IPv6. Sin esto, en Windows escucha solo en [::1] y Firefox,
        // que a veces resuelve localhost a 127.0.0.1 sin reintentar por IPv6, falla con NS_ERROR_CONNECTION_REFUSED.
        // Ojo: '::' son todas las interfaces, así que mientras corren los tests el dev server es accesible desde la
        // red local. Si ya hay un `npm run dev` abierto, Playwright reutiliza ese (sin este flag).
        command: 'npm run dev -- --host ::',
        url: BASE_URL,
        reuseExistingServer: !process.env.CI,
        timeout: 120 * 1000,
      },
});
