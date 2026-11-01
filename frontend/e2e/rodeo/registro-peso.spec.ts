import { test, expect, type Page } from '@playwright/test';
import { abrirDetalleAnimal, contextoRodeo, crearAnimal, registrarPesoApi } from '../helpers';

/**
 * H0016 - Registro de peso y gráfico de evolución.
 *
 * Precondiciones:
 *   - Usuario PRODUCTOR con "Fixture A".
 *   - Cada test crea por API un animal nuevo (sin pesajes) y, si hace falta, sus pesajes: el historial de pesos solo
 *     se ve en las estadísticas (mínimo, máximo, promedio) y en el gráfico de la sección "Evolución de Peso".
 *
 * El registro está en la pestaña Actividad, que es la que abre el detalle por defecto.
 */

const FORM = '#form-registro-peso';

function isoHace(dias: number): string {
  const fecha = new Date();
  fecha.setDate(fecha.getDate() - dias);
  return fecha.toISOString().split('T')[0];
}

async function abrirModalPeso(page: Page) {
  await expect(page.getByText('Evolución de Peso')).toBeVisible({ timeout: 15000 });
  await page.getByRole('button', { name: /Registrar Peso/i }).click();
  await expect(page.locator('.modal.show')).toBeVisible();
  await expect(page.locator('.modal.show').getByText('Registrar Peso')).toBeVisible();
}

test.describe('H0016 - Registro de peso y gráfico de evolución (UI)', () => {
  test('H0016-01 - Registrar un nuevo peso y que aparezca en el historial', async ({ page, request }) => {
    await abrirDetalleAnimal(page, await crearAnimal(request, await contextoRodeo(request)));
    await expect(page.getByText('Sin registros de peso aún.')).toBeVisible({ timeout: 15000 });
    await abrirModalPeso(page);

    await page.locator(`${FORM} input[name="valor"]`).fill('355.5');
    await page.locator(`${FORM} input[name="fecha"]`).fill(isoHace(1));
    await page.locator('.modal.show').getByRole('button', { name: 'Registrar', exact: true }).click();

    await expect(page.getByText('¡Peso registrado correctamente!')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.modal.show')).toHaveCount(0, { timeout: 10000 });

    // Con un único pesaje, mínimo, máximo y promedio coinciden con el valor registrado
    await expect(page.getByText('Sin registros de peso aún.')).toHaveCount(0);
    await expect(page.getByText('355.5 kg')).toHaveCount(3);
    await expect(page.locator('.recharts-line-dots circle')).toHaveCount(1);
  });

  test('H0016-02 - El gráfico de evolución de peso es visible', async ({ page, request }) => {
    const ctx = await contextoRodeo(request);
    const animal = await crearAnimal(request, ctx);
    await registrarPesoApi(request, ctx, animal.id, 300, isoHace(10));
    await registrarPesoApi(request, ctx, animal.id, 320, isoHace(5));
    await registrarPesoApi(request, ctx, animal.id, 340, isoHace(1));

    await abrirDetalleAnimal(page, animal);
    await expect(page.getByText('Evolución de Peso')).toBeVisible({ timeout: 15000 });

    // Línea de evolución con un punto por pesaje y estadísticas calculadas
    await expect(page.locator('.recharts-line-dots circle').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('.recharts-line-dots circle')).toHaveCount(3);
    await expect(page.getByText('300 kg')).toBeVisible();
    await expect(page.getByText('340 kg')).toBeVisible();
    await expect(page.getByText('320 kg')).toBeVisible();
  });

  test('H0016-03 - Registrar peso con valor inválido muestra error', async ({ page, request }) => {
    await abrirDetalleAnimal(page, await crearAnimal(request, await contextoRodeo(request)));
    await abrirModalPeso(page);

    const peso = page.locator(`${FORM} input[name="valor"]`);
    await peso.fill('-10');
    await page.locator('.modal.show').getByRole('button', { name: 'Registrar', exact: true }).click();

    // El input tiene min=0.1: el navegador bloquea el envío, el modal sigue abierto y no se registró nada
    expect(await peso.evaluate((el: HTMLInputElement) => el.validity.rangeUnderflow)).toBe(true);
    await expect(page.locator('.modal.show')).toBeVisible();
    await expect(page.getByText('¡Peso registrado correctamente!')).toHaveCount(0);
  });

  test('H0016-04 - Registrar peso sin fecha muestra error de validación', async ({ page, request }) => {
    await abrirDetalleAnimal(page, await crearAnimal(request, await contextoRodeo(request)));
    await abrirModalPeso(page);

    await page.locator(`${FORM} input[name="valor"]`).fill('300');
    const fecha = page.locator(`${FORM} input[name="fecha"]`);
    await fecha.fill('');
    await page.locator('.modal.show').getByRole('button', { name: 'Registrar', exact: true }).click();

    // La fecha es required: el navegador bloquea el envío y el modal sigue abierto
    expect(await fecha.evaluate((el: HTMLInputElement) => el.validity.valueMissing)).toBe(true);
    await expect(page.locator('.modal.show')).toBeVisible();
    await expect(page.getByText('¡Peso registrado correctamente!')).toHaveCount(0);
  });
});
