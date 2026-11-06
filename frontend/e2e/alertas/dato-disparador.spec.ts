import { test, expect } from '@playwright/test';
import {
  abrirDetalleAlerta,
  abrirDetalleDesdePanel,
  abrirPanelAlertas,
  animalJoven,
  crearAlerta,
  crearAlertaAislamiento,
  filaAlerta,
  loginPorClave,
  resolverPorApi,
} from '../helpers';

/**
 * H0022 - Ver el dato que disparó la alerta. El detalle muestra, para cada tipo, de dónde salió la alerta ("Dato de
 * origen") y la acción recomendada, y lo conserva cuando la alerta ya se resolvió.
 */
test.describe('H0022 - Ver dato que disparó la alerta (UI)', () => {
  test('H0022-04 - Celo: la confianza de la detección, la hora y la acción recomendada', async ({ page, request }) => {
    const animal = await animalJoven(request);
    crearAlerta(animal.id, 'CELO', {
      valorDetectado: 0.83,
      accionRecomendada: 'Planificar inseminación artificial dentro de la ventana óptima.',
    });
    await loginPorClave(page, 'veterinarioA');

    await abrirDetalleAlerta(page, animal.id);

    await expect(page.getByRole('heading', { name: 'Dato de origen' })).toBeVisible();
    await expect(page.getByText(/signos de celo.*confianza del 83%/)).toBeVisible();
    await expect(page.getByText(/Detectado el /)).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Acción recomendada' })).toBeVisible();
    await expect(page.getByText('Planificar inseminación artificial dentro de la ventana óptima.')).toBeVisible();
  });

  test('H0022-05 - Perímetro y aislamiento: el dato de cada animal sin abrir su ficha', async ({ page, request }) => {
    const [fuera, ...aislados] = [
      await animalJoven(request),
      await animalJoven(request),
      await animalJoven(request),
      await animalJoven(request),
    ];
    crearAlerta(fuera.id, 'FUERA_DE_PERIMETRO', { valorDetectado: 0, valorUmbral: 1 });
    crearAlertaAislamiento(aislados.map((a) => a.id), 1);
    await loginPorClave(page, 'productorA');

    // Salida del perímetro: el sistema indica que el animal salió y desde cuándo.
    await abrirDetalleDesdePanel(page, fuera.id);
    await expect(page.getByRole('heading', { name: 'Dato de origen' })).toBeVisible();
    await expect(page.getByText('El sistema detectó que el animal salió del perímetro asignado.')).toBeVisible();
    await expect(page.getByText(/Detectado el /)).toBeVisible();

    // Aislamiento: la distancia al grupo y el tiempo fuera de cada animal listado.
    await page.goto('/mapa');
    await abrirPanelAlertas(page);
    await page.getByText('3 animales aislados del rodeo').first().click();
    await expect(page.getByText(/se mantuvo lejos del grupo principal del rodeo/)).toBeVisible();
    for (const [i, a] of aislados.entries()) {
      const fila = page.getByRole('button', { name: new RegExp(`#${a.idCaravanaOficial}`) });
      await expect(fila).toContainText(`a ${280 + i * 10} m del grupo`);
    }
  });

  test('H0022-06 - El dato de origen se conserva al resolver la alerta y muestra quién la resolvió', async ({
    page,
    request,
  }) => {
    const animal = await animalJoven(request);
    const alertaId = crearAlerta(animal.id, 'CELO', { valorDetectado: 0.9 });
    await resolverPorApi(request, alertaId, 'CONFIRMADA', 'veterinarioA');
    await loginPorClave(page, 'productorA');

    await page.goto('/alertas');
    await page.locator('.vigia-page-container select').first().selectOption('CONFIRMADA');
    await filaAlerta(page, animal.id).first().getByRole('button', { name: /Ver Detalle/i }).click();

    await expect(page.getByText(/signos de celo.*confianza del 90%/)).toBeVisible();
    await expect(page.locator('.card-body', { hasText: 'ID Alerta' })).toContainText('CONFIRMADA');
    await expect(page.getByText('por Veterinario A', { exact: true })).toBeVisible();
    // Ya resuelta, no ofrece confirmar ni descartar.
    await expect(page.getByRole('button', { name: 'Descartar alerta' })).toHaveCount(0);
  });
});
