import { test, expect, type Page } from '@playwright/test';
import { abrirDetalleAnimal, contextoRodeo, crearAnimal, pestanaAnimal } from '../helpers';

/**
 * H0013 - Registro de estado reproductivo desde la pestaña Reproducción del detalle del animal.
 *
 * Precondiciones:
 *   - Usuario PRODUCTOR con "Fixture A".
 *   - Cada test crea por API un animal nuevo, sin historial reproductivo, para que la primera fila del historial sea
 *     siempre el registro que acaba de hacer.
 */

const FORM = '#form-estado-reproductivo';

async function irAReproduccion(page: Page, request: Parameters<typeof contextoRodeo>[0]) {
  const animal = await crearAnimal(request, await contextoRodeo(request));
  await abrirDetalleAnimal(page, animal);
  await pestanaAnimal(page, 'Reproducción').click();
  await expect(page.getByText('Sin registros reproductivos aún.')).toBeVisible({ timeout: 10000 });
}

async function abrirModalRegistro(page: Page) {
  await page.getByRole('button', { name: /Registrar estado/i }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByText('Registrar Estado Reproductivo')).toBeVisible();
}

/** Primera fila del historial reproductivo: el estado vigente. */
function filaVigente(page: Page) {
  return page.locator('.vigia-table tbody tr').first();
}

test.describe('H0013 - Estado reproductivo desde pestaña Reproducción (UI)', () => {
  test('H0013-01 - Registrar estado reproductivo "Gestante" con fechas', async ({ page, request }) => {
    await irAReproduccion(page, request);
    await abrirModalRegistro(page);

    const parto = new Date();
    parto.setMonth(parto.getMonth() - 6);
    const insem = new Date();
    insem.setMonth(insem.getMonth() - 2);
    const iso = (d: Date) => d.toISOString().split('T')[0];

    await page.locator(`${FORM} select[name="estado"]`).selectOption('GESTANTE');
    await page.locator(`${FORM} input[name="fechaUltimoParto"]`).fill(iso(parto));
    await page.locator(`${FORM} input[name="fechaUltimaInseminacion"]`).fill(iso(insem));
    await page.getByRole('dialog').getByRole('button', { name: 'Registrar', exact: true }).click();

    await expect(page.getByText('Estado registrado correctamente.')).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('dialog')).toHaveCount(0, { timeout: 10000 });

    const fila = filaVigente(page);
    await expect(fila).toContainText('Gestante');
    await expect(fila).toContainText('Manual');
    // Las fechas se muestran dd/mm/aaaa
    const ddmmaaaa = (d: Date) => iso(d).split('-').reverse().join('/');
    await expect(fila).toContainText(ddmmaaaa(parto));
    await expect(fila).toContainText(ddmmaaaa(insem));
  });

  test('H0013-02 - Registrar estado "Vacía"', async ({ page, request }) => {
    await irAReproduccion(page, request);
    await abrirModalRegistro(page);

    // "Vacía" es la opción por defecto
    await expect(page.locator(`${FORM} select[name="estado"]`)).toHaveValue('VACIA');
    await page.getByRole('dialog').getByRole('button', { name: 'Registrar', exact: true }).click();

    await expect(page.getByRole('dialog')).toHaveCount(0, { timeout: 10000 });
    await expect(page.getByText('Sin registros reproductivos aún.')).toHaveCount(0);
    await expect(page.locator('.vigia-table tbody tr')).toHaveCount(1);
    await expect(filaVigente(page)).toContainText('Vacía');
  });

  test('H0013-03 - Editar el estado reproductivo más reciente', async ({ page, request }) => {
    await irAReproduccion(page, request);

    // Un estado para editar
    await abrirModalRegistro(page);
    await page.getByRole('dialog').getByRole('button', { name: 'Registrar', exact: true }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0, { timeout: 10000 });
    await expect(filaVigente(page)).toContainText('Vacía');

    await filaVigente(page).getByRole('button', { name: /Editar/i }).click();
    await expect(page.getByText('Editar Estado Reproductivo')).toBeVisible();

    await page.locator(`${FORM} select[name="estado"]`).selectOption('CELO');
    await page.getByRole('dialog').getByRole('button', { name: 'Actualizar', exact: true }).click();

    await expect(page.getByText('Estado actualizado correctamente.')).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('dialog')).toHaveCount(0, { timeout: 10000 });
    // Se editó el registro: sigue habiendo una sola fila y ahora dice Celo
    await expect(page.locator('.vigia-table tbody tr')).toHaveCount(1);
    await expect(filaVigente(page)).toContainText('Celo');
    await expect(filaVigente(page)).not.toContainText('Vacía');
  });
});
