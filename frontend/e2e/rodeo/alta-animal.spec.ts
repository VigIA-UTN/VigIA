import { test, expect, type Page } from '@playwright/test';
import { loginPorRol, contextoRodeo, crearCaravana, ESTABLECIMIENTO_E2 } from '../helpers';

/**
 * H0010 - Alta manual de animal (incluido el campo sensor) y error por identificador repetido.
 *
 * Precondiciones:
 *   - Usuario PRODUCTOR con "Fixture A" (productorA).
 *   - "Fixture A" tiene al menos un rodeo/lote activo.
 *   - Para el campo sensor, el test crea por API una caravana disponible con código único.
 *
 * Los campos del modal se toman por su atributo name: las etiquetas no están asociadas a su input (Form.Group sin
 * controlId), así que getByLabel no los encuentra.
 */

const MODAL = '#form-nuevo-animal';

/** Navega a /animales con productorA y devuelve la página lista. */
async function irAAnimales(page: Page) {
  await loginPorRol(page, 'PRODUCTOR', { establecimiento: ESTABLECIMIENTO_E2 });
  await expect(page).toHaveURL(/\/dashboard/);
  await page.getByRole('link', { name: 'Animales', exact: true }).click();
  await expect(page).toHaveURL(/\/animales/);
  await expect(page.locator('.vigia-table')).toBeVisible({ timeout: 15000 });
}

/** Abre el modal "Nuevo Animal". */
async function abrirModalNuevoAnimal(page: Page) {
  await page.getByRole('button', { name: /Animal/i }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByText('Nuevo Animal')).toBeVisible();
}

/** Elige el primer lote de la lista (el 0 es "Seleccioná una opción"). */
async function elegirPrimerLote(page: Page) {
  const loteSelect = page.locator(`${MODAL} select[name="rodeoLoteId"]`);
  await expect(loteSelect).toBeVisible({ timeout: 10000 });
  await expect(loteSelect.locator('option')).not.toHaveCount(1, { timeout: 10000 });
  const primerLote = await loteSelect.locator('option').nth(1).getAttribute('value');
  await loteSelect.selectOption(primerLote!);
}

test.describe('H0010 - Alta manual de animal (UI)', () => {
  test('H0010-01 - Alta de animal con todos los campos, incluyendo sensor', async ({ page, request }) => {
    const caravana = await crearCaravana(request, await contextoRodeo(request));
    const idAnimal = `E2E-${Date.now()}`;
    const alias = `Vaquillona ${Date.now()}`;

    await irAAnimales(page);
    await abrirModalNuevoAnimal(page);

    await page.locator(`${MODAL} input[name="idCaravanaOficial"]`).fill(idAnimal);
    await page.locator(`${MODAL} input[name="alias"]`).fill(alias);
    await page.locator(`${MODAL} input[name="fechaNacimiento"]`).fill('2024-03-15');
    await page.locator(`${MODAL} input[name="raza"]`).fill('Holando');
    await elegirPrimerLote(page);

    // Sensor: la caravana creada para el test figura entre las disponibles
    const sensorSelect = page.locator(`${MODAL} select[name="caravanaId"]`);
    await expect(sensorSelect).toBeEnabled({ timeout: 10000 });
    const valorSensor = await sensorSelect.locator('option', { hasText: caravana.codigo }).getAttribute('value');
    expect(valorSensor, `la caravana ${caravana.codigo} figura como disponible`).toBeTruthy();
    await sensorSelect.selectOption(valorSensor!);

    await page.getByRole('dialog').getByRole('button', { name: 'Guardar' }).click();

    // El modal se cierra y el animal aparece en la tabla con su sensor vinculado
    await expect(page.getByRole('dialog')).toHaveCount(0, { timeout: 10000 });
    await page.getByPlaceholder(/Buscar por identificador/i).fill(idAnimal);
    const fila = page.locator('.vigia-table tbody tr.animal-row').filter({ hasText: idAnimal });
    await expect(fila).toHaveCount(1, { timeout: 10000 });
    await expect(fila).toContainText(alias);
    await expect(fila).toContainText(caravana.codigo);
    await expect(fila.getByText('Sin vincular')).toHaveCount(0);
  });

  test('H0010-02 - Alta de animal con identificador ya existente muestra error', async ({ page }) => {
    const idAnimal = `E2E-DUP-${Date.now()}`;

    await irAAnimales(page);

    // Alta del primer animal
    await abrirModalNuevoAnimal(page);
    await page.locator(`${MODAL} input[name="idCaravanaOficial"]`).fill(idAnimal);
    await page.locator(`${MODAL} input[name="fechaNacimiento"]`).fill('2024-01-01');
    await elegirPrimerLote(page);
    await page.getByRole('dialog').getByRole('button', { name: 'Guardar' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0, { timeout: 10000 });

    // Intento de alta con el mismo identificador
    await abrirModalNuevoAnimal(page);
    await page.locator(`${MODAL} input[name="idCaravanaOficial"]`).fill(idAnimal);
    await page.locator(`${MODAL} input[name="fechaNacimiento"]`).fill('2024-02-01');
    await elegirPrimerLote(page);
    await page.getByRole('dialog').getByRole('button', { name: 'Guardar' }).click();

    // El modal permanece abierto y muestra el error del backend
    const dialogo = page.getByRole('dialog');
    await expect(dialogo).toBeVisible();
    await expect(dialogo.getByRole('alert')).toContainText('El identificador ya se encuentra registrado', {
      timeout: 10000,
    });
  });

  test('H0010-03 - Alta de animal sin identificador muestra error de validación', async ({ page }) => {
    await irAAnimales(page);
    await abrirModalNuevoAnimal(page);

    await page.getByRole('dialog').getByRole('button', { name: 'Guardar' }).click();

    // El input es required: el navegador bloquea el envío, el modal sigue abierto y no se creó el animal
    const identificador = page.locator(`${MODAL} input[name="idCaravanaOficial"]`);
    expect(await identificador.evaluate((el: HTMLInputElement) => el.validity.valueMissing)).toBe(true);
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByRole('dialog').getByText('Animal registrado exitosamente.')).toHaveCount(0);
  });
});
