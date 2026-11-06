import { test, expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import {
  abrirPanelAlertas,
  animalJoven,
  crearAlerta,
  filaAlerta,
  login,
  loginPorClave,
  productorDeEstablecimientoVacio,
  resolverPorApi,
  tarjetaPanel,
} from '../helpers';

/**
 * H0011 - Filtrar alertas por tipo y severidad, en el histórico de alertas y en el panel del mapa. Cada test arma su
 * propio conjunto y verifica solo sus alertas por el animal: el escenario y los otros specs dejan alertas pendientes
 * en Fixture A que no se pueden asumir.
 */

interface Conjunto {
  celoAlta: number;
  celoBaja: number;
  saludMedia: number;
  perimetroAlta: number;
  alertaCeloAlta: number;
}

/** Cuatro animales nuevos con una alerta pendiente cada uno: celo ALTA, celo BAJA, salud MEDIA y perímetro ALTA. */
async function armarConjunto(request: APIRequestContext): Promise<Conjunto> {
  const [a, b, c, d] = [
    await animalJoven(request),
    await animalJoven(request),
    await animalJoven(request),
    await animalJoven(request),
  ];
  const alertaCeloAlta = crearAlerta(a.id, 'CELO', { severidad: 'ALTA' });
  crearAlerta(b.id, 'CELO', { severidad: 'BAJA' });
  crearAlerta(c.id, 'SALUD', { severidad: 'MEDIA' });
  crearAlerta(d.id, 'FUERA_DE_PERIMETRO', { severidad: 'ALTA' });
  return { celoAlta: a.id, celoBaja: b.id, saludMedia: c.id, perimetroAlta: d.id, alertaCeloAlta };
}

/** Los tres filtros del histórico, en el orden en que se muestran. */
function filtros(page: Page) {
  const selects = page.locator('.vigia-page-container select');
  return { estado: selects.nth(0), tipo: selects.nth(1), severidad: selects.nth(2) };
}

/**
 * Cambia un filtro del histórico y espera la respuesta del listado antes de seguir. La página no cancela los pedidos
 * anteriores: si se cambian dos filtros seguidos, la respuesta más lenta puede pisar a la última.
 */
async function filtrar(page: Page, filtro: Locator, valor: string) {
  const listado = page.waitForResponse((r) => /\/alertas\?/.test(r.url()) && r.ok());
  await filtro.selectOption(valor);
  await listado;
}

/** Comprueba cuáles de los animales del conjunto tienen fila en el histórico y cuáles no. */
async function filasVisibles(page: Page, visibles: number[], ocultas: number[]) {
  for (const id of visibles) await expect(filaAlerta(page, id), `#${id} visible`).toHaveCount(1);
  for (const id of ocultas) await expect(filaAlerta(page, id), `#${id} oculta`).toHaveCount(0);
}

test.describe('H0011 - Filtrar alertas por tipo y severidad (UI)', () => {
  test('H0011-07 - Histórico: filtro por tipo', async ({ page, request }) => {
    const c = await armarConjunto(request);
    await loginPorClave(page, 'productorA');
    await page.goto('/alertas');
    await filasVisibles(page, [c.celoAlta, c.celoBaja, c.saludMedia, c.perimetroAlta], []);

    await filtrar(page, filtros(page).tipo, 'CELO');
    await filasVisibles(page, [c.celoAlta, c.celoBaja], [c.saludMedia, c.perimetroAlta]);

    await filtrar(page, filtros(page).tipo, 'SALUD');
    await filasVisibles(page, [c.saludMedia], [c.celoAlta, c.celoBaja, c.perimetroAlta]);

    // Geoespacial agrupa perímetro, aislamiento y abigeato.
    await filtrar(page, filtros(page).tipo, 'GEOESPACIAL');
    await filasVisibles(page, [c.perimetroAlta], [c.celoAlta, c.celoBaja, c.saludMedia]);

    await filtrar(page, filtros(page).tipo, '');
    await filasVisibles(page, [c.celoAlta, c.celoBaja, c.saludMedia, c.perimetroAlta], []);
  });

  test('H0011-08 - Histórico: filtro por severidad', async ({ page, request }) => {
    const c = await armarConjunto(request);
    await loginPorClave(page, 'veterinarioA');
    await page.goto('/alertas');

    await filtrar(page, filtros(page).severidad, 'ALTA');
    await filasVisibles(page, [c.celoAlta, c.perimetroAlta], [c.celoBaja, c.saludMedia]);

    await filtrar(page, filtros(page).severidad, 'MEDIA');
    await filasVisibles(page, [c.saludMedia], [c.celoAlta, c.celoBaja, c.perimetroAlta]);

    await filtrar(page, filtros(page).severidad, 'BAJA');
    await filasVisibles(page, [c.celoBaja], [c.celoAlta, c.saludMedia, c.perimetroAlta]);
  });

  test('H0011-09 - Histórico: tipo, severidad y estado combinados', async ({ page, request }) => {
    const c = await armarConjunto(request);
    await loginPorClave(page, 'productorA');
    await page.goto('/alertas');

    await filtrar(page, filtros(page).tipo, 'CELO');
    await filtrar(page, filtros(page).severidad, 'ALTA');
    await filasVisibles(page, [c.celoAlta], [c.celoBaja, c.saludMedia, c.perimetroAlta]);

    // Una combinación sin coincidencias de este conjunto: salud de severidad ALTA.
    await filtrar(page, filtros(page).tipo, 'SALUD');
    await filasVisibles(page, [], [c.celoAlta, c.celoBaja, c.saludMedia, c.perimetroAlta]);

    // Con el estado: la alerta de celo ALTA confirmada solo aparece entre las confirmadas.
    await resolverPorApi(request, c.alertaCeloAlta, 'CONFIRMADA');
    await filtrar(page, filtros(page).tipo, 'CELO');
    await filasVisibles(page, [], [c.celoAlta]);
    await filtrar(page, filtros(page).estado, 'CONFIRMADA');
    await filasVisibles(page, [c.celoAlta], [c.celoBaja]);
    await expect(filaAlerta(page, c.celoAlta)).toContainText('CONFIRMADA');
  });

  test('H0011-10 - Panel del mapa: filtro por tipo y por severidad', async ({ page, request }) => {
    const c = await armarConjunto(request);
    await loginPorClave(page, 'productorA');
    await abrirPanelAlertas(page);
    const severidad = page.getByRole('combobox').filter({ hasText: 'Filtrar por Severidad' });
    const todas = [c.celoAlta, c.celoBaja, c.saludMedia, c.perimetroAlta];
    for (const id of todas) await expect(tarjetaPanel(page, id)).toHaveCount(1);

    // Por tipo, con los botones del panel; volver a tocar el botón saca el filtro.
    await page.getByRole('button', { name: /Celo$/ }).click();
    await expect(tarjetaPanel(page, c.celoAlta)).toHaveCount(1);
    await expect(tarjetaPanel(page, c.celoBaja)).toHaveCount(1);
    await expect(tarjetaPanel(page, c.saludMedia)).toHaveCount(0);
    await expect(tarjetaPanel(page, c.perimetroAlta)).toHaveCount(0);

    await page.getByRole('button', { name: /Salud$/ }).click();
    await expect(tarjetaPanel(page, c.saludMedia)).toHaveCount(1);
    await expect(tarjetaPanel(page, c.celoAlta)).toHaveCount(0);

    await page.getByRole('button', { name: /Geoespacial$/ }).click();
    await expect(tarjetaPanel(page, c.perimetroAlta)).toHaveCount(1);
    await expect(tarjetaPanel(page, c.saludMedia)).toHaveCount(0);

    await page.getByRole('button', { name: /Geoespacial$/ }).click();
    for (const id of todas) await expect(tarjetaPanel(page, id)).toHaveCount(1);

    // Tipo y severidad juntos.
    await page.getByRole('button', { name: /Celo$/ }).click();
    await severidad.selectOption('ALTA');
    await expect(tarjetaPanel(page, c.celoAlta)).toHaveCount(1);
    await expect(tarjetaPanel(page, c.celoBaja)).toHaveCount(0);
    await severidad.selectOption('BAJA');
    await expect(tarjetaPanel(page, c.celoBaja)).toHaveCount(1);
    await expect(tarjetaPanel(page, c.celoAlta)).toHaveCount(0);
  });

  test('H0011-11 - Sin coincidencias: se avisa en el histórico y en el panel', async ({ page, request }) => {
    // Un establecimiento nuevo no tiene alertas: cualquier combinación de filtros queda vacía.
    const productor = await productorDeEstablecimientoVacio(request);
    await login(page, productor.email, productor.password);

    await page.goto('/alertas');
    await expect(page.getByText('No se encontraron alertas que coincidan con los filtros.')).toBeVisible();
    await filtrar(page, filtros(page).tipo, 'CELO');
    await filtrar(page, filtros(page).severidad, 'ALTA');
    await expect(page.getByText('No se encontraron alertas que coincidan con los filtros.')).toBeVisible();

    await abrirPanelAlertas(page);
    await expect(page.getByText('No hay alertas registradas.')).toBeVisible();
  });
});
