import { test, expect, type Page } from '@playwright/test';
import {
  loginPorRol,
  contextoRodeo,
  crearAnimal,
  registrarEstadoSaludApi,
  sufijoUnico,
  ESTABLECIMIENTO_E2,
} from '../helpers';

/**
 * H0012 - Listado del rodeo: filtros, orden y navegación al detalle del animal.
 *
 * Precondiciones:
 *   - Usuario PRODUCTOR con "Fixture A", que tiene los 6 animales del escenario (A-101 a A-106).
 *   - Los tests que filtran crean por API sus propios animales con identificador y nombre únicos, así la búsqueda
 *     devuelve un resultado conocido y ninguno se saltea por falta de datos.
 */

async function irAAnimales(page: Page) {
  await loginPorRol(page, 'PRODUCTOR', { establecimiento: ESTABLECIMIENTO_E2 });
  await expect(page).toHaveURL(/\/dashboard/);
  await page.getByRole('link', { name: 'Animales', exact: true }).click();
  await expect(page).toHaveURL(/\/animales/);
  await expect(page.locator('.vigia-table')).toBeVisible({ timeout: 15000 });
  await expect(page.locator('.vigia-table tbody tr.animal-row').first()).toBeVisible({ timeout: 15000 });
}

const filas = (page: Page) => page.locator('.vigia-table tbody tr.animal-row');
const buscador = (page: Page) => page.getByPlaceholder(/Buscar por identificador/i);

test.describe('H0012 - Listado del rodeo: filtros, orden y navegación (UI)', () => {
  test('H0012-01 - Buscador filtra animales por identificador', async ({ page, request }) => {
    const ctx = await contextoRodeo(request);
    const buscado = await crearAnimal(request, ctx);
    await crearAnimal(request, ctx);
    await irAAnimales(page);
    const total = await filas(page).count();
    expect(total).toBeGreaterThan(1);

    await buscador(page).fill(buscado.idCaravanaOficial);

    await expect(filas(page)).toHaveCount(1, { timeout: 10000 });
    await expect(filas(page).first().locator('td').first()).toHaveText(buscado.idCaravanaOficial);
  });

  test('H0012-02 - Buscador filtra animales por nombre', async ({ page, request }) => {
    const ctx = await contextoRodeo(request);
    const alias = `Nombre-${sufijoUnico()}`;
    const buscado = await crearAnimal(request, ctx, { alias });
    await crearAnimal(request, ctx);
    await irAAnimales(page);

    await buscador(page).fill(alias);

    await expect(filas(page)).toHaveCount(1, { timeout: 10000 });
    await expect(filas(page).first().locator('td').nth(1)).toHaveText(alias);
    await expect(filas(page).first().locator('td').first()).toHaveText(buscado.idCaravanaOficial);
  });

  test('H0012-03 - Buscador con texto inexistente muestra "No se encontraron animales"', async ({ page }) => {
    await irAAnimales(page);

    await buscador(page).fill('XXXXXXXX_INEXISTENTE_99999');

    await expect(page.getByText('No se encontraron animales.')).toBeVisible({ timeout: 10000 });
    await expect(filas(page)).toHaveCount(0);
  });

  test('H0012-04 - Ordenar por columna ID cambia el orden de la lista', async ({ page }) => {
    await irAAnimales(page);
    const encabezadoId = page.getByRole('columnheader', { name: /^ID/ });
    const ids = () => filas(page).locator('td:first-child').allTextContents();

    // Sin ordenar → ascendente → descendente
    await encabezadoId.click();
    await expect(encabezadoId).toContainText('↑');
    const ascendente = await ids();
    expect(ascendente.length).toBeGreaterThan(1);

    await encabezadoId.click();
    await expect(encabezadoId).toContainText('↓');
    const descendente = await ids();

    expect(descendente).toEqual([...ascendente].reverse());
    expect(descendente).not.toEqual(ascendente);
  });

  test('H0012-05 - Clic en "Ver" navega al detalle del animal', async ({ page, request }) => {
    const alias = `Detalle-${sufijoUnico()}`;
    const animal = await crearAnimal(request, await contextoRodeo(request), { alias });
    await irAAnimales(page);

    await buscador(page).fill(animal.idCaravanaOficial);
    await expect(filas(page)).toHaveCount(1, { timeout: 10000 });
    await filas(page).first().locator('button:has(.bi-eye)').click();

    await expect(page).toHaveURL(new RegExp(`/animales/${animal.id}$`), { timeout: 10000 });
    await expect(page.getByRole('heading', { name: alias })).toBeVisible({ timeout: 10000 });
  });

  test('H0012-06 - Pestaña "Celo" filtra animales por estado reproductivo', async ({ page }) => {
    await irAAnimales(page);
    await expect(page.getByRole('columnheader', { name: /Rodeo/ })).toBeVisible();

    await page.locator('.btn-filter', { hasText: 'Celo' }).click();

    // La tabla cambia a las columnas reproductivas y deja las de rodeo, estado y salud
    await expect(page.getByRole('columnheader', { name: /Estado Reproductivo/ })).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('columnheader', { name: /Fecha Último Parto/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Fecha Última Inseminación/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Salud/ })).toHaveCount(0);
    await expect(filas(page).first()).toBeVisible();
  });

  test('H0012-07 - Pestaña "Salud" filtra animales con problemas de salud', async ({ page, request }) => {
    const ctx = await contextoRodeo(request);
    const marca = `Salud-${sufijoUnico()}`;
    const sano = await crearAnimal(request, ctx, { alias: `${marca}-sano` });
    const enfermo = await crearAnimal(request, ctx, { alias: `${marca}-enfermo` });
    await registrarEstadoSaludApi(request, ctx, sano.id, 'SANO');
    await registrarEstadoSaludApi(request, ctx, enfermo.id, 'ENFERMO');
    await irAAnimales(page);

    await buscador(page).fill(marca);
    await expect(filas(page)).toHaveCount(2, { timeout: 10000 });

    await page.locator('.btn-filter', { hasText: 'Salud' }).click();

    // Solo queda el animal enfermo
    await expect(filas(page)).toHaveCount(1, { timeout: 10000 });
    await expect(filas(page).first().locator('td').first()).toHaveText(enfermo.idCaravanaOficial);
    await expect(filas(page).first()).toContainText('Enfermo');
  });
});
