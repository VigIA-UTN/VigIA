import { test, expect } from '@playwright/test';
import { loginPorClave, obtenerEstablecimientoSeleccionado, seleccionarEstablecimiento } from '../helpers';

/**
 * H0035 - Cambio de establecimiento activo desde el selector del navbar. productorAmbos pertenece a Fixture A y
 * Fixture B; los animales del escenario se identifican A-101..A-106 y B-101..B-106.
 */
test.describe('H0035 - Cambio de establecimiento activo (UI)', () => {
  test('H0035-01 - El listado de animales muestra solo los del establecimiento elegido', async ({ page }) => {
    await loginPorClave(page, 'productorAmbos');
    await page.getByRole('link', { name: 'Animales', exact: true }).click();

    await seleccionarEstablecimiento(page, 'Fixture A');
    await expect(page.getByText('A-101', { exact: true })).toBeVisible();
    await expect(page.getByText('B-101', { exact: true })).toHaveCount(0);

    await seleccionarEstablecimiento(page, 'Fixture B');
    await expect(page.getByText('B-101', { exact: true })).toBeVisible();
    await expect(page.getByText('A-101', { exact: true })).toHaveCount(0);
  });

  test('H0035-02 - El establecimiento elegido se mantiene al recargar y al navegar', async ({ page }) => {
    await loginPorClave(page, 'productorAmbos');
    await seleccionarEstablecimiento(page, 'Fixture B');

    await page.reload();
    await page.getByRole('link', { name: 'Animales', exact: true }).click();

    await expect.poll(() => obtenerEstablecimientoSeleccionado(page)).toBe('Fixture B');
    await expect(page.getByText('B-101', { exact: true })).toBeVisible();
    await expect(page.getByText('A-101', { exact: true })).toHaveCount(0);
  });
});
