import { test, expect, type Page } from '@playwright/test';
import { loginPorRol, contextoRodeo, asegurarRodeo, sufijoUnico, ESTABLECIMIENTO_E2 } from '../helpers';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * H0011 - Carga masiva de animales con demo/nuevas_vaquillonas.csv y con un archivo con errores.
 *
 * Precondiciones:
 *   - Usuario PRODUCTOR con "Fixture A" (productorA).
 *   - demo/nuevas_vaquillonas.csv disponible en el repositorio.
 *   - El escenario no trae "Lote Cuarentena" (el lote que referencia el CSV): cada test lo asegura por API.
 *   - Los specs corren en varios navegadores contra la misma base y el CSV de demo trae IdCaravana y Alias fijos, que
 *     el backend rechaza si ya existen. Por eso se sube el contenido del CSV de demo con esas dos columnas
 *     prefijadas por una marca única de la corrida; el resto de las columnas queda tal cual.
 */

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/** Ruta al CSV de demo: frontend/e2e/rodeo → raíz del repositorio. */
const CSV_DEMO = path.resolve(__dirname, '../../../demo/nuevas_vaquillonas.csv');
const LOTE_DEMO = 'Lote Cuarentena';

/** Contenido del CSV de demo con IdCaravana y Alias únicos para esta corrida. */
function csvDemoUnico(): { contenido: string; filas: number } {
  const marca = `E2E${sufijoUnico()}-`;
  const [encabezado, ...filas] = fs
    .readFileSync(CSV_DEMO, 'utf-8')
    .replace(/^﻿/, '')
    .split(/\r?\n/)
    .filter((linea) => linea.trim() !== '');
  const datos = filas.map((linea) => {
    const [id, alias, ...resto] = linea.split(',');
    return [marca + id, marca + alias, ...resto].join(',');
  });
  return { contenido: [encabezado, ...datos].join('\n') + '\n', filas: datos.length };
}

/** CSV con una fila sin IdCaravana, una con fecha futura y una con lote inexistente, más una fila válida. */
function csvConErrores(): string {
  const marca = `E2E${sufijoUnico()}`;
  return [
    'IdCaravana,Alias,FechaNacimiento,Raza,RodeoLote',
    `,${marca}-sin-id,2025-01-01,Holando,${LOTE_DEMO}`,
    `${marca}-futura,${marca}-fut,2099-01-01,Holando,${LOTE_DEMO}`,
    `${marca}-lote,${marca}-lot,2025-01-01,Holando,RodeoInexistente`,
    `${marca}-ok,${marca}-ok,2025-01-01,Holando,${LOTE_DEMO}`,
    '',
  ].join('\n');
}

async function irAAnimales(page: Page) {
  await loginPorRol(page, 'PRODUCTOR', { establecimiento: ESTABLECIMIENTO_E2 });
  await expect(page).toHaveURL(/\/dashboard/);
  await page.getByRole('link', { name: 'Animales', exact: true }).click();
  await expect(page).toHaveURL(/\/animales/);
}

async function abrirModalCargaMasiva(page: Page) {
  await page.locator('button[aria-label="Más acciones"]').click();
  await expect(page.locator('.est-menu')).toBeVisible();
  await page.locator('.est-menu button', { hasText: 'Cargar animales' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByText('Cargar Animales con Excel')).toBeVisible();
}

test.describe('H0011 - Carga masiva de animales (UI)', () => {
  test.beforeEach(async ({ request }) => {
    await asegurarRodeo(request, await contextoRodeo(request), LOTE_DEMO);
  });

  test('H0011-01 - Carga masiva con demo/nuevas_vaquillonas.csv procesa correctamente', async ({ page }) => {
    const { contenido, filas } = csvDemoUnico();
    await irAAnimales(page);
    await abrirModalCargaMasiva(page);

    await page.locator('input[type="file"]').setInputFiles({
      name: 'nuevas_vaquillonas.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(contenido, 'utf-8'),
    });
    await page.getByRole('dialog').getByRole('button', { name: 'Subir Archivo' }).click();

    const reporte = page.getByRole('dialog');
    await expect(reporte.getByText('¡Carga procesada correctamente!')).toBeVisible({ timeout: 15000 });
    await expect(reporte.locator('li', { hasText: 'Procesados:' })).toContainText(String(filas));
    await expect(reporte.locator('li', { hasText: 'Exitosos:' })).toContainText(String(filas));
    await expect(reporte.locator('li', { hasText: 'Fallidos:' })).toContainText('0');
    await expect(reporte.getByText('Detalle de errores:')).toHaveCount(0);
  });

  test('H0011-02 - Carga masiva con archivo con errores muestra reporte de fallos', async ({ page }) => {
    await irAAnimales(page);
    await abrirModalCargaMasiva(page);

    await page.locator('input[type="file"]').setInputFiles({
      name: 'errores.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvConErrores(), 'utf-8'),
    });
    await page.getByRole('dialog').getByRole('button', { name: 'Subir Archivo' }).click();

    const reporte = page.getByRole('dialog');
    await expect(reporte.getByText(/Carga fallida/)).toBeVisible({ timeout: 15000 });
    await expect(reporte.locator('li', { hasText: 'Procesados:' })).toContainText('4');
    // Todo o nada: la fila válida tampoco se guarda cuando hay errores
    await expect(reporte.locator('li', { hasText: 'Fallidos:' })).toContainText('3');
    await expect(reporte.getByText('Detalle de errores:')).toBeVisible();
    await expect(reporte.getByText(/Fila 2: El IdCaravana es obligatorio/)).toBeVisible();
    await expect(reporte.getByText(/Fila 3: .*futura/)).toBeVisible();
    await expect(reporte.getByText(/Fila 4: .*RodeoInexistente/)).toBeVisible();
  });

  test('H0011-03 - Carga masiva sin seleccionar archivo muestra validación', async ({ page }) => {
    await irAAnimales(page);
    await abrirModalCargaMasiva(page);

    await page.getByRole('dialog').getByRole('button', { name: 'Subir Archivo' }).click();

    // El input es required: el navegador bloquea el envío y no se genera ningún reporte
    const inputArchivo = page.locator('input[type="file"]');
    expect(await inputArchivo.evaluate((el: HTMLInputElement) => el.validity.valueMissing)).toBe(true);
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByText('Procesados:')).toHaveCount(0);
  });
});
