import { test, expect } from '@playwright/test';
import {
  loginPorRol,
  loginPorClave,
  obtenerEstablecimientoSeleccionado,
  seleccionarEstablecimiento,
} from '../helpers';

test.describe('Humo - Autenticación y navegación básica', () => {
  test('login de Productor y llegada al Dashboard', async ({ page }) => {
    // 1. Iniciar sesión como Productor usando credenciales del escenario
    const usuario = await loginPorRol(page, 'PRODUCTOR');
    expect(usuario.rol).toBe('PRODUCTOR');

    // 2. Verificar llegada al Dashboard
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });

    // 3. Verificar que el header del Dashboard y la navegación estén presentes
    await expect(page.locator('.dashboard-page')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.dash-header')).toBeVisible();

    // 4. Verificar que el establecimiento inicial coincida con el usuario
    const establecimientoActual = await obtenerEstablecimientoSeleccionado(page);
    expect(usuario.establecimientos).toContain(establecimientoActual);
  });

  test('productor con múltiples campos puede alternar de establecimiento', async ({ page }) => {
    // ProductorAmbos tiene asignados "Fixture A" y "Fixture B"
    const usuario = await loginPorClave(page, 'productorAmbos');
    expect(usuario.establecimientos).toContain('Fixture A');
    expect(usuario.establecimientos).toContain('Fixture B');

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });

    // Cambiar a Fixture B usando el helper
    await seleccionarEstablecimiento(page, 'Fixture B');
    const seleccionadoB = await obtenerEstablecimientoSeleccionado(page);
    expect(seleccionadoB).toBe('Fixture B');

    // Cambiar nuevamente a Fixture A
    await seleccionarEstablecimiento(page, 'Fixture A');
    const seleccionadoA = await obtenerEstablecimientoSeleccionado(page);
    expect(seleccionadoA).toBe('Fixture A');
  });
});
