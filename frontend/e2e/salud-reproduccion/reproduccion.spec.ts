import { test, expect } from '@playwright/test';
import {
  abrirDetalleAlerta,
  animalNuevo,
  crearAlertaCelo,
  filaAlerta,
  idAnimalEscenario,
  loginPorClave,
} from '../helpers';

/**
 * Flujos reproductivos con las alertas del escenario: A-102 (celo detectado hace 1 h, caravana al día) y A-106 (celo
 * con la caravana sin transmitir hace 3 h). El escenario se ubica relativo a su carga: tiene que estar recién cargado.
 */

test.describe('H0008 - Alerta de celo con dato desactualizado (UI)', () => {
  test('H0008-09 - Badge de dato desactualizado en el listado', async ({ page, request }) => {
    const [desactualizado, alDia] = [await idAnimalEscenario(request, 'A-106'), await idAnimalEscenario(request, 'A-102')];
    await loginPorClave(page, 'veterinarioA');
    await page.goto('/alertas');

    await expect(filaAlerta(page, desactualizado).getByText(/Sin señal/)).toBeVisible();
    await expect(filaAlerta(page, alDia)).toBeVisible();
    await expect(filaAlerta(page, alDia).getByText(/Sin señal/)).toHaveCount(0);
  });

  test('H0008-10 - Aviso de dato desactualizado en el detalle', async ({ page, request }) => {
    const [desactualizado, alDia] = [await idAnimalEscenario(request, 'A-106'), await idAnimalEscenario(request, 'A-102')];
    await loginPorClave(page, 'veterinarioA');

    await abrirDetalleAlerta(page, desactualizado);
    await expect(page.getByText('Dato posiblemente desactualizado')).toBeVisible();

    await abrirDetalleAlerta(page, alDia);
    await expect(page.getByText('Ventana de inseminación')).toBeVisible();
    await expect(page.getByText('Dato posiblemente desactualizado')).toHaveCount(0);
  });
});

test.describe('H0009 - Detalle del evento de celo (UI)', () => {
  test('H0009-07 - Detalle de la alerta', async ({ page, request }) => {
    const id = await idAnimalEscenario(request, 'A-102');
    await loginPorClave(page, 'productorA');
    await abrirDetalleAlerta(page, id);

    await expect(page.getByRole('heading', { name: /Celo A/ })).toBeVisible();
    await expect(page.getByText(/A-102/).first()).toBeVisible();
    await expect(page.getByText('Ventana de inseminación')).toBeVisible();
    await expect(page.getByText(/Realizar inseminación artificial/)).toBeVisible();
    await expect(page.getByRole('button', { name: /Confirmar celo e inseminación/i })).toBeVisible();
  });
});

test.describe('H0016 - Countdown de la ventana óptima (UI)', () => {
  test('H0016-05 - Antes de la ventana', async ({ page, request }) => {
    const id = await idAnimalEscenario(request, 'A-102');
    await loginPorClave(page, 'productorA');
    await abrirDetalleAlerta(page, id);

    const tarjeta = page.locator('.card-body', { hasText: 'Tiempo hasta inicio de ventana óptima' });
    await expect(tarjeta).toBeVisible();
    // El contador avanza solo, sin recargar la página.
    const inicial = await tarjeta.innerText();
    await expect.poll(() => tarjeta.innerText(), { timeout: 5000 }).not.toBe(inicial);
  });

  test('H0016-06 - Dentro de la ventana', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    crearAlertaCelo(animal.id, 8);
    await loginPorClave(page, 'productorA');
    await abrirDetalleAlerta(page, animal.id);

    await expect(page.getByText(/Tiempo restante/)).toBeVisible();
    await expect(page.getByText(/% transcurrido/)).toBeVisible();
    await expect(page.getByText(/Menos de 2 horas/)).toHaveCount(0);
  });

  test('H0016-07 - Últimas 2 horas', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    crearAlertaCelo(animal.id, 23);
    await loginPorClave(page, 'productorA');
    await abrirDetalleAlerta(page, animal.id);

    await expect(page.getByText(/Menos de 2 horas/)).toBeVisible();
  });

  test('H0016-08 - Ventana vencida', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    crearAlertaCelo(animal.id, 25);
    await loginPorClave(page, 'productorA');
    await abrirDetalleAlerta(page, animal.id);

    await expect(page.getByText('Ventana vencida')).toBeVisible();
  });
});

test.describe('H0023 - Historial reproductivo (UI)', () => {
  test('H0023-04 - Historial en la pestaña Reproducción', async ({ page, request }) => {
    const id = await idAnimalEscenario(request, 'A-102');
    await loginPorClave(page, 'veterinarioA');
    await page.goto(`/animales/${id}?tab=reproduccion`);

    await expect(page.getByRole('heading', { name: 'Historial Reproductivo' })).toBeVisible();
    const parida = page.locator('table tbody tr', { hasText: 'Parida' });
    await expect(parida).toContainText('Manual');
    await expect(parida).toContainText('Veterinario A');
    await expect(page.locator('table tbody tr', { hasText: /Posible celo/i })).toContainText(/Autom/);
  });

  test('H0023-05 - Registrar un estado', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    await loginPorClave(page, 'veterinarioA');
    await page.goto(`/animales/${animal.id}?tab=reproduccion`);
    await expect(page.getByText('Sin registros reproductivos aún.')).toBeVisible();

    await page.getByRole('button', { name: /Registrar estado/i }).click();
    await page.locator('#form-estado-reproductivo select[name="estado"]').selectOption('VACIA');
    await page.getByRole('dialog').getByRole('button', { name: 'Registrar', exact: true }).click();

    const fila = page.locator('table tbody tr').first();
    await expect(fila).toContainText(/Vac[ií]a/);
    await expect(fila).toContainText('Manual');
  });

  test('H0023-06 - El campero solo consulta', async ({ page, request }) => {
    const id = await idAnimalEscenario(request, 'A-102');
    await loginPorClave(page, 'camperoA');
    await page.goto(`/animales/${id}?tab=reproduccion`);

    await expect(page.locator('table tbody tr', { hasText: 'Parida' })).toBeVisible();
    await expect(page.getByRole('button', { name: /Registrar estado/i })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Editar/i })).toHaveCount(0);
  });
});
