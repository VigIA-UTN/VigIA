import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    exclude: ['**/node_modules/**', '**/dist/**', '**/e2e/**'],
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (
              id.includes('react') ||
              id.includes('react-dom') ||
              id.includes('react-bootstrap') ||
              id.includes('react-router-dom') ||
              id.includes('axios') ||
              id.includes('zustand')
            ) {
              return 'vendor';
            }
          }
        }
      }
    }
  },
  test: {
    // Usa un tsconfig dedicado que incluye los archivos de test y los tipos de jest-dom
    typecheck: { tsconfig: './tsconfig.test.json' },
    // Entorno DOM completo para tests de componentes React
    environment: 'jsdom',
    // Importa @testing-library/jest-dom matchers globalmente en cada test
    setupFiles: ['./src/test/setup.ts'],
    // Permite usar describe/it/expect sin importar explícitamente de vitest
    globals: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      // Solo mide cobertura de código fuente propio
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/main.tsx',
        'src/test/**',
        'src/**/*.test.{ts,tsx}',
        'src/**/*.d.ts',
      ],
    },
  },
})
