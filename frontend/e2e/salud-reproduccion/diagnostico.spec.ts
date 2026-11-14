import { test, expect } from '@playwright/test';
import {
  abrirDetalleAlerta,
  expandirEvento,
  animalNuevo,
  crearAlertaSalud,
  diagnosticarPorApi,
  loginPorClave,
  tokenPorClave,
} from '../helpers';

/**
 * H0031 - Diagnóstico veterinario trazable e inmutable. Cada test usa una alerta sanitaria nueva: confirmarla y
 * diagnosticarla no se puede deshacer, y los specs corren en varios navegadores.
 */
test.describe('H0031 - Diagnóstico veterinario (UI)', () => {
  test('H0031-05 - Confirmar la alerta y registrar el diagnóstico', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    crearAlertaSalud(animal.id);
    await loginPorClave(page, 'veterinarioA');
    await abrirDetalleAlerta(page, animal.id);

    // Sin confirmar la alerta no se puede diagnosticar.
    await expect(page.getByRole('button', { name: 'Confirmar alerta' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Registrar diagnóstico' })).toHaveCount(0);

    await page.getByRole('button', { name: 'Confirmar alerta' }).click();
    await page.getByLabel('Diagnóstico', { exact: true }).fill('Mastitis clínica');
    await page.getByLabel(/Indicación o tratamiento/).fill('Antibiótico por 5 días');
    await page.getByRole('button', { name: 'Registrar diagnóstico' }).click();

    const registrado = page.locator('.card-body', { hasText: 'Mastitis clínica' });
    await expect(registrado).toContainText('Antibiótico por 5 días');
    await expect(registrado).toContainText('Veterinario A');
  });

  test('H0031-06 - El diagnóstico no se puede editar', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    const alertaId = crearAlertaSalud(animal.id);
    await diagnosticarPorApi(request, alertaId, 'Neumonía', 'Reposo');
    await loginPorClave(page, 'veterinarioA');

    // Sin acciones de edición en pantalla.
    await page.getByRole('link', { name: 'Diagnósticos', exact: true }).click();
    const fila = page.locator('table tbody tr', { hasText: animal.idCaravanaOficial });
    await expect(fila).toContainText('Neumonía');
    await expect(fila.getByRole('button', { name: /Editar|Eliminar/i })).toHaveCount(0);

    // Tampoco por API, ni siendo el veterinario que lo registró.
    const headers = { Authorization: `Bearer ${await tokenPorClave(request, 'veterinarioA')}` };
    const datos = { diagnostico: 'Otro diagnóstico', tratamiento: 'Otro tratamiento' };
    const url = `/api/v1/alertas/${alertaId}/diagnosticos`;
    expect((await request.put(url, { headers, data: datos })).ok()).toBeFalsy();
    expect((await request.delete(url, { headers })).ok()).toBeFalsy();

    await page.reload();
    await expect(fila).toContainText('Neumonía');
  });

  test('H0031-07 - El historial clínico muestra el diagnóstico', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    const alertaId = crearAlertaSalud(animal.id);
    await diagnosticarPorApi(request, alertaId, 'Neumonía', 'Reposo');
    await loginPorClave(page, 'productorA');
    await page.goto(`/animales/${animal.id}?tab=historial`);

    const evento = page.locator('.hc-event', { hasText: 'DIAGNÓSTICO' });
    await expect(evento).toBeVisible();
    await expandirEvento(evento);
    await expect(evento).toContainText('Neumonía');
    await expect(evento).toContainText('Reposo');
  });

  test('H0031-08 - El campero no ve las acciones del veterinario', async ({ page, request }) => {
    const animal = await animalNuevo(request);
    const pendiente = await animalNuevo(request);
    const alertaId = crearAlertaSalud(animal.id);
    crearAlertaSalud(pendiente.id);
    await diagnosticarPorApi(request, alertaId, 'Neumonía', 'Reposo');
    await loginPorClave(page, 'camperoA');

    // Sin la opción ni la ruta de Diagnósticos.
    await expect(page.locator('nav.navbar').getByRole('link', { name: 'Diagnósticos', exact: true })).toHaveCount(0);
    await page.goto('/diagnosticos');
    await expect(page).not.toHaveURL(/\/diagnosticos$/);

    // En una alerta sanitaria pendiente no puede confirmar, descartar ni diagnosticar.
    await abrirDetalleAlerta(page, pendiente.id);
    await expect(page.getByText('Desvío sanitario detectado')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Confirmar alerta' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Descartar alerta' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Registrar diagnóstico' })).toHaveCount(0);

    // En el historial clínico consulta el diagnóstico, pero no registra estado de salud ni intervención.
    await page.goto(`/animales/${animal.id}?tab=historial`);
    await expect(page.locator('.hc-event', { hasText: 'DIAGNÓSTICO' })).toContainText('Neumonía');
    await expect(page.getByRole('button', { name: /Registrar estado de salud/i })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Registrar intervención/i })).toHaveCount(0);
  });
});
