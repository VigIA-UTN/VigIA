import { test, expect, type Page } from '@playwright/test';
import {
  abrirPanelAlertas,
  animalJoven,
  crearAlerta,
  crearAlertaAislamiento,
  filaAlerta,
  loginPorClave,
  resolucionEnBase,
  sql,
  tarjetaPanel,
} from '../helpers';

/**
 * H0028 - Panel de alertas ordenado por severidad, y decisión sobre un grupo de animales con una sola acción. Cada test
 * crea sus alertas y mira solo la posición de las suyas: el escenario y los otros specs dejan otras pendientes.
 */

/** Ids de animal de las filas del histórico, en el orden en que se muestran. */
async function idsDelHistorico(page: Page): Promise<number[]> {
  const celdas = await page.locator('tbody tr td:first-child').allInnerTexts();
  return celdas.map((t) => Number(t.replace('#', ''))).filter((n) => !Number.isNaN(n));
}

/** Ids de animal de las tarjetas del panel del mapa, en el orden en que se muestran. */
async function idsDelPanel(page: Page): Promise<number[]> {
  const titulos = await page.getByText(/^#\d+ en estado/).allInnerTexts();
  return titulos.map((t) => Number(/^#(\d+)/.exec(t)![1]));
}

/** Las posiciones de estos animales dentro de la lista, para comparar el orden relativo. */
function posiciones(lista: number[], ...animales: number[]): number[] {
  return animales.map((id) => lista.indexOf(id));
}

test.describe('H0028 - Panel de alertas ordenado por severidad (UI)', () => {
  // El histórico y el panel cargan todas las alertas pendientes del establecimiento: con la base cargada de otros specs
  // superan los 30 s por defecto.
  test.describe.configure({ timeout: 60_000 });

  test('H0028-05 - Histórico: de la severidad más alta a la más baja y, a igual severidad, la más reciente', async ({
    page,
    request,
  }) => {
    // Insertadas en desorden, con la más nueva en la severidad más baja.
    const [baja, mediaA, altaVieja, altaNueva] = [
      await animalJoven(request),
      await animalJoven(request),
      await animalJoven(request),
      await animalJoven(request),
    ];
    crearAlerta(baja.id, 'CELO', { severidad: 'BAJA', minutosAtras: 2 });
    crearAlerta(mediaA.id, 'SALUD', { severidad: 'MEDIA', minutosAtras: 20 });
    crearAlerta(altaVieja.id, 'CELO', { severidad: 'ALTA', minutosAtras: 90 });
    crearAlerta(altaNueva.id, 'FUERA_DE_PERIMETRO', { severidad: 'ALTA', minutosAtras: 10 });
    await loginPorClave(page, 'veterinarioA');

    await page.goto('/alertas');
    await expect(filaAlerta(page, baja.id)).toBeVisible();
    const orden = await idsDelHistorico(page);

    const [pNueva, pVieja, pMedia, pBaja] = posiciones(orden, altaNueva.id, altaVieja.id, mediaA.id, baja.id);
    expect([pNueva, pVieja, pMedia, pBaja].every((p) => p >= 0)).toBe(true);
    expect(pNueva).toBeLessThan(pVieja);
    expect(pVieja).toBeLessThan(pMedia);
    expect(pMedia).toBeLessThan(pBaja);
    // La fila muestra la severidad de cada una.
    await expect(filaAlerta(page, altaNueva.id)).toContainText('ALTA');
    await expect(filaAlerta(page, mediaA.id)).toContainText('MEDIA');
    await expect(filaAlerta(page, baja.id)).toContainText('BAJA');
  });

  test('H0028-06 - Panel del mapa: mismo orden y el estado del animal según la severidad', async ({ page, request }) => {
    const [baja, media, alta] = [await animalJoven(request), await animalJoven(request), await animalJoven(request)];
    crearAlerta(baja.id, 'SALUD', { severidad: 'BAJA', minutosAtras: 1 });
    crearAlerta(media.id, 'CELO', { severidad: 'MEDIA', minutosAtras: 15 });
    crearAlerta(alta.id, 'CELO', { severidad: 'ALTA', minutosAtras: 120 });
    await loginPorClave(page, 'camperoA');

    await abrirPanelAlertas(page);
    await expect(tarjetaPanel(page, baja.id)).toHaveCount(1);
    const orden = await idsDelPanel(page);

    const [pAlta, pMedia, pBaja] = posiciones(orden, alta.id, media.id, baja.id);
    expect(pAlta).toBeGreaterThanOrEqual(0);
    expect(pAlta).toBeLessThan(pMedia);
    expect(pMedia).toBeLessThan(pBaja);
    await expect(page.getByText(`#${alta.id} en estado CRITICO`)).toBeVisible();
    await expect(page.getByText(`#${media.id} en estado BORDE`)).toBeVisible();
    await expect(page.getByText(`#${baja.id} en estado NORMAL`)).toBeVisible();
  });

  test('H0028-07 - Un grupo de animales aislados es una sola tarjeta y se decide con una sola acción', async ({
    page,
    request,
  }) => {
    const animales = [await animalJoven(request), await animalJoven(request), await animalJoven(request)];
    const alertaId = crearAlertaAislamiento(animales.map((a) => a.id), 1);
    await loginPorClave(page, 'productorA');

    // En el panel, una tarjeta para los tres, no una por animal.
    await abrirPanelAlertas(page);
    const tarjeta = page.getByText('3 animales aislados del rodeo').first();
    await expect(tarjeta).toBeVisible();
    for (const a of animales) await expect(tarjetaPanel(page, a.id)).toHaveCount(0);

    // El detalle lista a todos, sin abrir la ficha de cada uno.
    await tarjeta.click();
    await expect(page.getByRole('heading', { name: '3 animales aislados del rodeo' })).toBeVisible();
    for (const a of animales) await expect(page.getByText(`#${a.idCaravanaOficial}`)).toBeVisible();
    await expect(page).toHaveURL(/\/mapa/);

    // Una sola confirmación resuelve la alerta de todo el grupo.
    await page.getByRole('button', { name: /Confirmar aislamiento$/ }).click();
    await expect.poll(() => resolucionEnBase(alertaId).estado).toBe('CONFIRMADA');
    const alertasDelGrupo = sql(
      `SELECT count(DISTINCT alerta_id) FROM alerta_animales_aislados
       WHERE animal_id IN (${animales.map((a) => a.id).join(', ')});`
    );
    expect(alertasDelGrupo).toBe('1');
  });

  test('H0028-08 - Los filtros del histórico mantienen el orden por severidad', async ({ page, request }) => {
    const [celoMedia, celoAlta, celoBaja] = [
      await animalJoven(request),
      await animalJoven(request),
      await animalJoven(request),
    ];
    crearAlerta(celoBaja.id, 'CELO', { severidad: 'BAJA', minutosAtras: 1 });
    crearAlerta(celoMedia.id, 'CELO', { severidad: 'MEDIA', minutosAtras: 30 });
    crearAlerta(celoAlta.id, 'CELO', { severidad: 'ALTA', minutosAtras: 100 });
    await loginPorClave(page, 'productorA');

    await page.goto('/alertas');
    await page.locator('.vigia-page-container select').nth(1).selectOption('CELO');
    await expect(filaAlerta(page, celoBaja.id)).toBeVisible();

    const orden = await idsDelHistorico(page);
    const [pAlta, pMedia, pBaja] = posiciones(orden, celoAlta.id, celoMedia.id, celoBaja.id);
    expect(pAlta).toBeGreaterThanOrEqual(0);
    expect(pAlta).toBeLessThan(pMedia);
    expect(pMedia).toBeLessThan(pBaja);
  });
});
