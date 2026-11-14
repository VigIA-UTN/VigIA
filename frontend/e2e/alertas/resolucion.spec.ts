import { test, expect } from '@playwright/test';
import {
  abrirDetalleAlerta,
  abrirDetalleDesdePanel,
  animalJoven,
  crearAlerta,
  email,
  filaAlerta,
  loginPorClave,
  resolucionEnBase,
} from '../helpers';

/**
 * H0010 - Confirmar o descartar una alerta. Cada test crea su propia alerta: resolverla no se puede deshacer y los
 * specs corren en varios navegadores contra el mismo escenario. La resolución se verifica en la base, que es donde queda.
 */
test.describe('H0010 - Confirmar/descartar alerta (UI)', () => {
  // Los casos de dos sesiones inician dos logins y tardan más de 30 s en Firefox y WebKit.
  test.describe.configure({ timeout: 60_000 });

  test('H0010-07 - Confirmar una alerta desde el panel del mapa', async ({ page, request }) => {
    const animal = await animalJoven(request);
    const alertaId = crearAlerta(animal.id, 'CELO');
    await loginPorClave(page, 'veterinarioA');

    await abrirDetalleDesdePanel(page, animal.id);
    await page.getByRole('button', { name: /Confirmar celo$/ }).click();

    await expect.poll(() => resolucionEnBase(alertaId).estado).toBe('CONFIRMADA');
    expect(resolucionEnBase(alertaId).usuario).toBe(email('veterinarioA'));
    // El detalle se cierra al resolver.
    await expect(page.getByRole('button', { name: 'Descartar alerta' })).toHaveCount(0);
  });

  test('H0010-08 - Descartar una alerta desde el panel del mapa con una nota', async ({ page, request }) => {
    const animal = await animalJoven(request);
    const alertaId = crearAlerta(animal.id, 'FUERA_DE_PERIMETRO');
    await loginPorClave(page, 'productorA');

    await abrirDetalleDesdePanel(page, animal.id);
    await page.getByPlaceholder('Agregá una nota (opcional)...').fill('Estaba en el potrero vecino');
    await page.getByRole('button', { name: 'Descartar alerta' }).click();

    await expect.poll(() => resolucionEnBase(alertaId).estado).toBe('DESCARTADA');
    const resolucion = resolucionEnBase(alertaId);
    expect(resolucion.usuario).toBe(email('productorA'));
    expect(resolucion.motivoDescarte).toBe('Estaba en el potrero vecino');
  });

  test('H0010-09 - Confirmar y descartar desde el detalle del histórico de alertas', async ({ page, request }) => {
    const [aConfirmar, aDescartar] = [await animalJoven(request), await animalJoven(request)];
    const [confirmada, descartada] = [crearAlerta(aConfirmar.id, 'CELO'), crearAlerta(aDescartar.id, 'FUERA_DE_PERIMETRO')];
    await loginPorClave(page, 'productorA');

    await abrirDetalleAlerta(page, aConfirmar.id);
    await page.getByRole('button', { name: /Confirmar celo$/ }).click();
    // Con el filtro de pendientes, la alerta resuelta sale de la lista sin recargar.
    await expect(filaAlerta(page, aConfirmar.id)).toHaveCount(0);

    await abrirDetalleAlerta(page, aDescartar.id);
    await page.getByRole('button', { name: 'Descartar alerta' }).click();
    await expect(filaAlerta(page, aDescartar.id)).toHaveCount(0);

    expect(resolucionEnBase(confirmada).estado).toBe('CONFIRMADA');
    expect(resolucionEnBase(descartada).estado).toBe('DESCARTADA');

    // Cada una aparece en la lista de su estado.
    const filtroEstado = page.locator('.vigia-page-container select').first();
    await filtroEstado.selectOption('CONFIRMADA');
    await expect(filaAlerta(page, aConfirmar.id)).toContainText('CONFIRMADA');
    await filtroEstado.selectOption('DESCARTADA');
    await expect(filaAlerta(page, aDescartar.id)).toContainText('DESCARTADA');
  });

  test('H0010-10 - Dos sesiones sobre la misma alerta: la segunda ve el aviso de alerta ya procesada', async ({
    page,
    browser,
    request,
  }) => {
    const animal = await animalJoven(request);
    const alertaId = crearAlerta(animal.id, 'CELO');
    const otraSesion = await browser.newContext();
    const otraPagina = await otraSesion.newPage();
    try {
      await loginPorClave(page, 'veterinarioA');
      await abrirDetalleAlerta(page, animal.id);
      await loginPorClave(otraPagina, 'productorA');
      await abrirDetalleAlerta(otraPagina, animal.id);
      await expect(otraPagina.getByRole('button', { name: 'Descartar alerta' })).toBeVisible();

      // El veterinario la confirma primero; la otra sesión sigue con el detalle que abrió antes.
      await page.getByRole('button', { name: /Confirmar celo$/ }).click();
      await expect.poll(() => resolucionEnBase(alertaId).estado).toBe('CONFIRMADA');

      await otraPagina.getByRole('button', { name: 'Descartar alerta' }).click();

      const aviso = otraPagina.getByRole('alert').filter({ hasText: 'Alerta ya procesada' });
      await expect(aviso).toContainText(/ya fue confirmada por Veterinario A/);
      // Muestra el estado real y ya no ofrece resolverla.
      await expect(otraPagina.getByRole('button', { name: 'Descartar alerta' })).toHaveCount(0);
      await expect(otraPagina.getByText('por Veterinario A', { exact: true })).toBeVisible();

      // Ganó la primera: nada de la segunda quedó guardado.
      const resolucion = resolucionEnBase(alertaId);
      expect(resolucion).toEqual({ estado: 'CONFIRMADA', usuario: email('veterinarioA'), motivoDescarte: '' });
    } finally {
      await otraSesion.close();
    }
  });

  test('H0010-11 - La otra sesión ve sin recargar que la alerta ya no está pendiente', async ({
    page,
    browser,
    request,
  }) => {
    const animal = await animalJoven(request);
    crearAlerta(animal.id, 'CELO');
    const otraSesion = await browser.newContext();
    const otraPagina = await otraSesion.newPage();
    try {
      await loginPorClave(page, 'veterinarioA');
      await abrirDetalleAlerta(page, animal.id);
      await loginPorClave(otraPagina, 'productorA');
      const streamAbierto = otraPagina.waitForResponse(/\/alertas\/stream/);
      await otraPagina.goto('/alertas');
      await expect(filaAlerta(otraPagina, animal.id)).toBeVisible();
      // Sin la conexión del stream abierta, el aviso de la otra sesión se perdería.
      await streamAbierto;

      await page.getByRole('button', { name: /Confirmar celo$/ }).click();

      // El listado de pendientes de la otra sesión se actualiza solo, por el stream de alertas.
      await expect(filaAlerta(otraPagina, animal.id)).toHaveCount(0);
    } finally {
      await otraSesion.close();
    }
  });
});
