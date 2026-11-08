import { test, expect, type Page } from '@playwright/test';
import {
  abrirDetalleAlerta,
  expandirEvento,
  animalNuevo,
  contextoRodeo,
  crearAlertaSalud,
  idAnimalEscenario,
  loginPorClave,
  registrarCohesion,
  registrarEstadoSaludApi,
} from '../helpers';

/**
 * Flujos sanitarios: A-103 tiene la alerta sanitaria de severidad MEDIA del escenario. Lo demás se prueba sobre un
 * animal nuevo para que el estado de salud y el índice de cohesión no dependan de otros tests ni navegadores.
 */

async function chipEstadoSalud(page: Page, animalId: number) {
  await page.goto(`/animales/${animalId}?tab=ficha`);
  await expect(page.getByRole('heading', { name: 'Indicadores de Salud' })).toBeVisible();
}

test.describe('H0018 - Detalle de alerta sanitaria (UI)', () => {
  test('H0018-06 - Severidad media y causa probable', async ({ page, request }) => {
    const id = await idAnimalEscenario(request, 'A-103');
    await loginPorClave(page, 'veterinarioA');
    await abrirDetalleAlerta(page, id);

    await expect(page.getByText('Desvío sanitario detectado')).toBeVisible();
    await expect(page.getByText('MEDIA', { exact: true }).last()).toBeVisible();
    await expect(page.getByText('Posible causa', { exact: true })).toBeVisible();
    await expect(page.getByText('Caída de actividad respecto de su perfil').first()).toBeVisible();
  });

  test('H0018-07 - Severidad alta', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    crearAlertaSalud(animal.id, 'ALTA');
    await loginPorClave(page, 'veterinarioA');
    await abrirDetalleAlerta(page, animal.id);

    await expect(page.getByText('Desvío sanitario detectado')).toBeVisible();
    await expect(page.getByText('ALTA', { exact: true }).last()).toBeVisible();
  });
});

test.describe('H0019 - Registro manual del estado de salud (UI)', () => {
  test('H0019-05 - Registro manual', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    await loginPorClave(page, 'veterinarioA');
    await page.goto(`/animales/${animal.id}?tab=historial`);

    await page.getByRole('button', { name: /Registrar estado de salud/i }).click();
    const formulario = page.locator('#form-estado-salud');
    await formulario.locator('select[name="estado"]').selectOption('EN_OBSERVACION');
    await formulario.locator('textarea[name="diagnosticoNotas"]').fill('Decaimiento leve, se controla mañana');
    await page.getByRole('dialog').getByRole('button', { name: 'Registrar', exact: true }).click();

    const evento = page.locator('.hc-event', { hasText: 'ESTADO DE SALUD' });
    await expect(evento).toBeVisible();
    await expandirEvento(evento);
    await expect(evento).toContainText('Decaimiento leve, se controla mañana');
  });

  test('H0019-06 - Estado obligatorio', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    await loginPorClave(page, 'veterinarioA');
    await page.goto(`/animales/${animal.id}?tab=historial`);

    await page.getByRole('button', { name: /Registrar estado de salud/i }).click();

    await expect(page.getByRole('dialog').getByRole('button', { name: 'Registrar', exact: true })).toBeDisabled();
  });
});

test.describe('H0020 - Indicadores de salud (UI)', () => {
  for (const [caso, nombre, estado, etiqueta] of [
    ['H0020-06', 'Nivel normal', 'SANO', 'Normal'],
    ['H0020-07', 'Nivel borde', 'EN_OBSERVACION', 'Atención'],
    ['H0020-08', 'Nivel crítico', 'ENFERMO', 'Alerta'],
  ] as const) {
    test(`${caso} - ${nombre}`, async ({ page, request }) => {
      const ctx = await contextoRodeo(request);
      const animal = await animalNuevo(request);
      await registrarEstadoSaludApi(request, ctx, animal.id, estado);
      await loginPorClave(page, 'veterinarioA');

      await chipEstadoSalud(page, animal.id);

      await expect(page.getByLabel(new RegExp(`^Estado de salud: ${etiqueta}\\b`))).toBeVisible();
    });
  }

  test('H0020-09 - Sin datos', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    await loginPorClave(page, 'veterinarioA');

    await chipEstadoSalud(page, animal.id);

    await expect(page.getByLabel(/^Estado de salud: Sin datos/)).toBeVisible();
  });
});

test.describe('H0026 - Índice de cohesión (UI)', () => {
  test('H0026-10 - Animal alejado del rodeo', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    registrarCohesion(animal.id, 0.25);
    await loginPorClave(page, 'veterinarioA');
    await page.goto(`/animales/${animal.id}?tab=actividad`);

    await expect(page.getByRole('heading', { name: 'Índice de cohesión' })).toBeVisible();
    // El calendario oculto del selector de fechas también tiene un botón "25": se busca el valor, que es un span.
    await expect(page.locator('span').filter({ hasText: /^25$/ }).first()).toBeVisible();
    await expect(page.getByText('Alejado', { exact: true })).toBeVisible();
  });

  test('H0026-11 - Animal en calibración', async ({ page, request }) => {
    const id = await idAnimalEscenario(request, 'A-102');
    await loginPorClave(page, 'veterinarioA');
    await page.goto(`/animales/${id}?tab=actividad`);

    await expect(page.getByText('Disponible al finalizar la calibración.')).toBeVisible();
  });

  test('H0026-12 - Vista del campero', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    registrarCohesion(animal.id, 0.25);
    await loginPorClave(page, 'camperoA');
    await page.goto(`/animales/${animal.id}?tab=actividad`);

    await expect(page.getByText('Alejado', { exact: true })).toBeVisible();
  });
});
