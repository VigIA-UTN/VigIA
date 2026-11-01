import { test, expect, type Page } from '@playwright/test';
import { loginPorRol, contextoRodeo, crearAnimal, ESTABLECIMIENTO_E2 } from '../helpers';

/**
 * H0014 - Alta y edición de rodeos/lotes; edición y baja de animal.
 *
 * Precondiciones:
 *   - Usuario PRODUCTOR con "Fixture A".
 *   - Los animales que se editan o dan de baja los crea el propio test por API.
 *
 * Los campos de los formularios se toman por su atributo name: las etiquetas de los modales no están asociadas a su
 * input (Form.Group sin controlId), así que getByLabel no los encuentra.
 */

async function irAAnimales(page: Page) {
  await loginPorRol(page, 'PRODUCTOR', { establecimiento: ESTABLECIMIENTO_E2 });
  await expect(page).toHaveURL(/\/dashboard/);
  await page.getByRole('link', { name: 'Animales', exact: true }).click();
  await expect(page).toHaveURL(/\/animales/);
  await expect(page.locator('.vigia-table')).toBeVisible({ timeout: 15000 });
}

async function abrirModalNuevoRodeo(page: Page) {
  await page.locator('button[aria-label="Más acciones"]').click();
  await expect(page.locator('.est-menu')).toBeVisible();
  await page.locator('.est-menu button', { hasText: /^Rodeo/ }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByText('Nuevo Rodeo')).toBeVisible();
}

async function buscarAnimal(page: Page, identificador: string) {
  await page.getByPlaceholder(/Buscar por identificador/i).fill(identificador);
  const fila = page.locator('.vigia-table tbody tr.animal-row').filter({ hasText: identificador });
  await expect(fila).toHaveCount(1, { timeout: 10000 });
  return fila;
}

test.describe('H0014 - Gestión de rodeos y animales (UI)', () => {
  test('H0014-01 - Alta de rodeo/lote nuevo', async ({ page }) => {
    const nombreRodeo = `Rodeo E2E ${Date.now()}`;
    await irAAnimales(page);
    await abrirModalNuevoRodeo(page);

    await page.locator('#form-nuevo-rodeo input[name="nombre"]').fill(nombreRodeo);
    await page.getByRole('dialog').getByRole('button', { name: 'Guardar' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0, { timeout: 10000 });

    // El rodeo nuevo se ofrece como lote al dar de alta un animal
    await page.getByRole('button', { name: /Animal/i }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    const loteSelect = page.locator('select[name="rodeoLoteId"]');
    await expect(loteSelect).toBeVisible({ timeout: 10000 });
    await expect(loteSelect.locator('option', { hasText: nombreRodeo })).toHaveCount(1);
  });

  test('H0014-02 - Alta de rodeo sin nombre muestra validación', async ({ page }) => {
    await irAAnimales(page);
    await abrirModalNuevoRodeo(page);

    await page.getByRole('dialog').getByRole('button', { name: 'Guardar' }).click();

    // El input es required: el navegador bloquea el envío, el modal sigue abierto y no se creó nada
    const nombre = page.locator('#form-nuevo-rodeo input[name="nombre"]');
    expect(await nombre.evaluate((el: HTMLInputElement) => el.validity.valueMissing)).toBe(true);
    await expect(page.getByRole('dialog')).toBeVisible();
  });

  // La app todavía no permite editar un rodeo/lote: el backend solo expone GET, POST y DELETE en /api/v1/rodeo-lotes
  // y el frontend no tiene pantalla de edición. Se deja el caso escrito para activarlo cuando exista la funcionalidad
  // (hay que ajustar el acceso a la edición en la UI).
  test.fixme('H0014-05 - Edición del nombre de un rodeo/lote', async ({ page }) => {
    const nombreOriginal = `Rodeo E2E ${Date.now()}`;
    const nombreEditado = `${nombreOriginal} editado`;
    await irAAnimales(page);
    await abrirModalNuevoRodeo(page);
    await page.locator('#form-nuevo-rodeo input[name="nombre"]').fill(nombreOriginal);
    await page.getByRole('dialog').getByRole('button', { name: 'Guardar' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0, { timeout: 10000 });

    // TODO: abrir la edición del rodeo cuando exista en la UI
    await page.getByText(nombreOriginal).click();
    await page.getByRole('button', { name: /Editar/i }).click();
    await page.locator('input[name="nombre"]').fill(nombreEditado);
    await page.getByRole('button', { name: 'Guardar' }).click();

    await expect(page.getByText(nombreEditado)).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(nombreOriginal, { exact: true })).toHaveCount(0);
  });

  test('H0014-03 - Edición de animal desde su detalle', async ({ page, request }) => {
    const animal = await crearAnimal(request, await contextoRodeo(request));
    const nuevoAlias = `EditadoE2E-${Date.now()}`;

    await irAAnimales(page);
    const fila = await buscarAnimal(page, animal.idCaravanaOficial);
    await fila.getByRole('button', { name: /Ver/i }).click();
    await expect(page).toHaveURL(new RegExp(`/animales/${animal.id}`), { timeout: 10000 });

    // La edición vive en la pestaña Ficha (la pestaña por defecto es Actividad)
    await page.locator('.animal-tab-btn', { hasText: 'Ficha' }).click();
    await page.getByRole('button', { name: /Editar/i }).click();

    const inputAlias = page.locator('input[name="alias"]');
    await expect(inputAlias).toBeVisible({ timeout: 10000 });
    await inputAlias.fill(nuevoAlias);
    await page.getByRole('button', { name: 'Guardar' }).click();

    // Sale del modo edición y el alias nuevo queda en el título y en la ficha
    await expect(page.getByRole('button', { name: 'Guardar' })).toHaveCount(0, { timeout: 10000 });
    await expect(page.getByRole('heading', { name: nuevoAlias })).toBeVisible();
    await expect(page.getByText(nuevoAlias).first()).toBeVisible();

    // Persistió: se ve al recargar
    await page.reload();
    await expect(page.getByRole('heading', { name: nuevoAlias })).toBeVisible({ timeout: 10000 });
  });

  test('H0014-04 - Baja de animal desde el listado', async ({ page, request }) => {
    const animal = await crearAnimal(request, await contextoRodeo(request));

    await irAAnimales(page);
    const fila = await buscarAnimal(page, animal.idCaravanaOficial);

    // Botón de baja (ícono de tacho) y confirmación
    await fila.locator('button:has(.bi-trash)').click();
    const btnConfirmar = page.getByRole('button', { name: /Sí, dar de baja/i });
    await expect(btnConfirmar).toBeVisible({ timeout: 10000 });
    await btnConfirmar.click();

    // El animal desaparece del listado y sigue sin aparecer después de recargar
    await expect(page.locator('.vigia-table tbody tr.animal-row').filter({ hasText: animal.idCaravanaOficial })).toHaveCount(0, {
      timeout: 10000,
    });
    await page.reload();
    await expect(page.locator('.vigia-table')).toBeVisible({ timeout: 15000 });
    await page.getByPlaceholder(/Buscar por identificador/i).fill(animal.idCaravanaOficial);
    await expect(page.locator('.vigia-table tbody tr.animal-row').filter({ hasText: animal.idCaravanaOficial })).toHaveCount(0);
  });
});
