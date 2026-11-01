import { test, expect, type Page } from '@playwright/test';
import {
  loginPorRol,
  contextoRodeo,
  crearAnimal,
  crearCaravana,
  darDeBajaCaravanaApi,
  ESTABLECIMIENTO_E2,
} from '../helpers';

/**
 * H0015 - Gestión de caravanas: baja, reactivación, vinculación y desvinculación.
 *
 * Precondiciones:
 *   - Usuario PRODUCTOR con "Fixture A" y usuario ADMINISTRADOR (único que da de baja y reactiva caravanas).
 *   - Cada test crea por API su propio animal y su propia caravana, con códigos únicos: no depende del contenido del
 *     escenario ni se saltea cuando falta un dato.
 */

async function irAAnimalesYBuscar(page: Page, identificador: string) {
  await loginPorRol(page, 'PRODUCTOR', { establecimiento: ESTABLECIMIENTO_E2 });
  await expect(page).toHaveURL(/\/dashboard/);
  await page.getByRole('link', { name: 'Animales', exact: true }).click();
  await expect(page).toHaveURL(/\/animales/);
  await expect(page.locator('.vigia-table')).toBeVisible({ timeout: 15000 });

  await page.getByPlaceholder(/Buscar por identificador/i).fill(identificador);
  const fila = page.locator('.vigia-table tbody tr.animal-row').filter({ hasText: identificador });
  await expect(fila).toHaveCount(1, { timeout: 10000 });
  return fila;
}

async function irACaravanasYBuscar(page: Page, codigo: string) {
  await loginPorRol(page, 'ADMINISTRADOR');
  await expect(page).toHaveURL(/\/establecimientos/);
  await page.getByRole('link', { name: 'Caravanas', exact: true }).click();
  await expect(page).toHaveURL(/\/caravanas/);
  await expect(page.locator('.vigia-table')).toBeVisible({ timeout: 15000 });

  // La página abre en el primer establecimiento de la lista: se elige el del escenario
  await page.locator('select').last().selectOption({ label: ESTABLECIMIENTO_E2 });
  await page.getByPlaceholder('Buscar por código...').fill(codigo);
  const fila = page.locator('.vigia-table tbody tr').filter({ hasText: codigo });
  await expect(fila).toHaveCount(1, { timeout: 10000 });
  return fila;
}

test.describe('H0015 - Gestión de caravanas (UI)', () => {
  test('H0015-01 - Vincular caravana disponible a un animal sin caravana', async ({ page, request }) => {
    const ctx = await contextoRodeo(request);
    const caravana = await crearCaravana(request, ctx);
    const animal = await crearAnimal(request, ctx);

    const fila = await irAAnimalesYBuscar(page, animal.idCaravanaOficial);
    await expect(fila.getByText('Sin vincular')).toBeVisible();

    await fila.getByRole('button', { name: /Vincular/i }).click();
    await expect(page.getByRole('dialog')).toBeVisible();

    const select = page.locator('#form-vincular-caravana').getByRole('combobox');
    await expect(select).toBeVisible({ timeout: 10000 });
    const valor = await select.locator('option', { hasText: caravana.codigo }).getAttribute('value');
    expect(valor, `la caravana ${caravana.codigo} figura como disponible`).toBeTruthy();
    await select.selectOption(valor!);
    await page.getByRole('dialog').getByRole('button', { name: 'Vincular' }).click();

    await expect(page.getByRole('dialog')).toHaveCount(0, { timeout: 10000 });
    await expect(fila.getByText('Sin vincular')).toHaveCount(0);
    await expect(fila.getByText(caravana.codigo)).toBeVisible();
  });

  test('H0015-02 - Desvincular caravana de un animal y deshacer con "Deshacer"', async ({ page, request }) => {
    const ctx = await contextoRodeo(request);
    const caravana = await crearCaravana(request, ctx);
    const animal = await crearAnimal(request, ctx, { caravanaId: caravana.id });

    const fila = await irAAnimalesYBuscar(page, animal.idCaravanaOficial);
    await expect(fila.getByText(caravana.codigo)).toBeVisible();

    await fila.locator('button[title="Desvincular"]').click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.locator('.btn-confirmar-desvincular').click();

    // Desvinculada: la fila vuelve a "Sin vincular" y aparece el toast con Deshacer
    await expect(fila.getByText('Sin vincular')).toBeVisible({ timeout: 10000 });
    const undoToast = page.locator('.undo-toast').filter({ hasText: /Caravana desvinculada/i });
    await expect(undoToast).toBeVisible({ timeout: 10000 });

    // Deshacer: la caravana vuelve a estar vinculada al animal
    await undoToast.getByRole('button', { name: /Deshacer/i }).click();
    await expect(fila.getByText(caravana.codigo)).toBeVisible({ timeout: 10000 });
    await expect(fila.getByText('Sin vincular')).toHaveCount(0);
  });

  test('H0015-03 - Dar de baja una caravana desde la sección Caravanas (administrador)', async ({ page, request }) => {
    const ctx = await contextoRodeo(request);
    const caravana = await crearCaravana(request, ctx);

    const fila = await irACaravanasYBuscar(page, caravana.codigo);
    await expect(fila.locator('.estado-caravana-disponible')).toBeVisible();

    await fila.getByRole('button', { name: 'Dar de baja' }).click();
    const dialogo = page.getByRole('dialog');
    await expect(dialogo.getByText('Dar de baja la caravana')).toBeVisible();
    await dialogo.getByRole('button', { name: 'Dar de baja', exact: true }).click();

    await expect(dialogo).toHaveCount(0, { timeout: 10000 });
    await expect(fila.locator('.estado-caravana-inactivo')).toBeVisible({ timeout: 10000 });
    await expect(fila.getByRole('button', { name: 'Reactivar' })).toBeVisible();
  });

  test('H0015-04 - Reactivar una caravana dada de baja (administrador)', async ({ page, request }) => {
    const ctx = await contextoRodeo(request);
    const caravana = await crearCaravana(request, ctx);
    await darDeBajaCaravanaApi(request, ctx, caravana.id);

    const fila = await irACaravanasYBuscar(page, caravana.codigo);
    await expect(fila.locator('.estado-caravana-inactivo')).toBeVisible();

    await fila.getByRole('button', { name: 'Reactivar' }).click();
    const dialogo = page.getByRole('dialog');
    await expect(dialogo.getByText('Reactivar la caravana')).toBeVisible();
    await dialogo.getByRole('button', { name: 'Reactivar', exact: true }).click();

    await expect(dialogo).toHaveCount(0, { timeout: 10000 });
    await expect(fila.locator('.estado-caravana-disponible')).toBeVisible({ timeout: 10000 });
    await expect(fila.getByRole('button', { name: 'Dar de baja' })).toBeVisible();
  });
});
