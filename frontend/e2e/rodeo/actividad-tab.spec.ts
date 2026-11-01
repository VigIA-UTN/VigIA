import { test, expect, type Page } from '@playwright/test';
import { abrirDetalleAnimal, contextoRodeo, crearAnimal, pestanaAnimal } from '../helpers';

/**
 * H0017 - Pestaña Actividad muestra los últimos 7 días.
 *
 * Precondiciones:
 *   - Usuario PRODUCTOR con "Fixture A".
 *   - Cada test crea por API un animal nuevo: sin telemetría, el backend igual devuelve los 7 días (marcados "sin
 *     datos"), que es lo que verifica la pestaña.
 */

async function irAlDetalleDeUnAnimalNuevo(page: Page, request: Parameters<typeof contextoRodeo>[0]) {
  await abrirDetalleAnimal(page, await crearAnimal(request, await contextoRodeo(request)));
}

test.describe('H0017 - Pestaña Actividad muestra los últimos 7 días (UI)', () => {
  test('H0017-01 - La pestaña Actividad carga y muestra el gráfico de actividad motora', async ({ page, request }) => {
    await irAlDetalleDeUnAnimalNuevo(page, request);

    // Se llega a Actividad desde otra pestaña, para probar también el clic
    await pestanaAnimal(page, 'Ficha').click();
    await pestanaAnimal(page, 'Actividad').click();

    await expect(page.getByRole('heading', { name: 'Actividad Motora' })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/últimos 7 días/i)).toBeVisible();
    await expect(page.locator('.recharts-wrapper').first()).toBeVisible();
  });

  test('H0017-02 - El gráfico de actividad motora muestra 7 barras (una por día)', async ({ page, request }) => {
    await irAlDetalleDeUnAnimalNuevo(page, request);

    // Un tick por día en el eje X del primer gráfico de la pestaña, el de barras (el de peso viene después)
    const ticks = page.locator('.recharts-wrapper').first().locator('.recharts-xAxis .recharts-cartesian-axis-tick');
    await expect(ticks).toHaveCount(7, { timeout: 15000 });
  });

  test('H0017-03 - La pestaña Actividad no muestra error de carga', async ({ page, request }) => {
    await irAlDetalleDeUnAnimalNuevo(page, request);

    await expect(page.getByRole('heading', { name: 'Actividad Motora' })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('Cargando historial de actividad...')).toHaveCount(0);
    await expect(page.locator('.alert-danger')).toHaveCount(0);
    await expect(page.getByText(/últimos 7 días/i)).toBeVisible();
  });

  test('H0017-04 - La pestaña Actividad es la pestaña por defecto del detalle del animal', async ({ page, request }) => {
    await irAlDetalleDeUnAnimalNuevo(page, request);

    // Sin clic adicional: Actividad figura activa y su contenido está a la vista
    await expect(pestanaAnimal(page, 'Actividad')).toHaveClass(/active/);
    await expect(pestanaAnimal(page, 'Ficha')).not.toHaveClass(/active/);
    await expect(page.getByText(/últimos 7 días/i)).toBeVisible({ timeout: 15000 });
  });
});
