import { test, expect, type Page } from '@playwright/test';
import { login, loginPorClave, productorDeEstablecimientoVacio } from '../helpers';

/**
 * H0025 - Mapa de calor. Corre contra el ml-geo real del stack con la telemetría de Fixture B: los filtros se verifican
 * en la consulta que sale del panel, porque lo que se dibuja es un canvas.
 */

const CAPA = 'canvas.leaflet-heatmap-layer';

function panelMapaCalor(page: Page) {
  return page.getByRole('dialog', { name: 'Mapa de calor' });
}

/** Consultas al mapa de calor hechas desde que se llama: la última es la del filtro vigente. */
function registrarConsultas(page: Page): URLSearchParams[] {
  const consultas: URLSearchParams[] = [];
  page.on('request', (pedido) => {
    if (/\/api\/v1\/mapa-calor\?/.test(pedido.url())) consultas.push(new URL(pedido.url()).searchParams);
  });
  return consultas;
}

/** Abre el mapa, despliega el panel y activa la capa. */
async function activarMapaDeCalor(page: Page) {
  await page.goto('/mapa');
  await page.getByRole('button', { name: 'Mapa de calor' }).click();
  await page.getByRole('switch', { name: 'Activar HeatMap' }).click();
  await expect(page.locator(CAPA)).toBeAttached();
}

async function abrirPanelDeFixtureB(page: Page) {
  await loginPorClave(page, 'productorB');
  await activarMapaDeCalor(page);
  await expect(panelMapaCalor(page).getByText('Rodeo B1')).toBeVisible();
}

/** Días entre el desde y el hasta de una consulta (las fechas son hora local, sin zona). */
function diasDelRango(consulta: URLSearchParams): number {
  const fecha = (param: string) => Date.parse(`${consulta.get(param)!.slice(0, 10)}T00:00:00Z`);
  return (fecha('hasta') - fecha('desde')) / 86400000;
}

test.describe('H0025 - Mapa de calor con filtros (UI)', () => {
  test('H0025-05 - Activar y desactivar la capa', async ({ page }) => {
    await loginPorClave(page, 'productorB');
    await page.goto('/mapa');
    await page.getByRole('button', { name: 'Mapa de calor' }).click();
    await expect(page.locator(CAPA)).toHaveCount(0);
    const interruptor = page.getByRole('switch', { name: 'Activar HeatMap' });

    const pedido = page.waitForResponse(/\/api\/v1\/mapa-calor\?/);
    await interruptor.click();
    const respuesta = await pedido;
    expect(respuesta.status()).toBe(200);
    expect((await respuesta.json()).mapa_calor.length).toBeGreaterThan(0);

    await expect(page.locator(CAPA)).toBeAttached();
    await expect(page.locator('.map-fab-on')).toHaveText('ON');

    await interruptor.click();
    await expect(page.locator(CAPA)).toHaveCount(0);
    await expect(page.locator('.map-fab-on')).toHaveCount(0);
  });

  test('H0025-06 - Accesos rápidos de período', async ({ page }) => {
    const consultas = registrarConsultas(page);
    await abrirPanelDeFixtureB(page);
    const panel = panelMapaCalor(page);

    await panel.getByRole('button', { name: '7 días', exact: true }).click();
    await expect.poll(() => diasDelRango(consultas.at(-1)!)).toBe(6);

    await panel.getByRole('button', { name: '30 días', exact: true }).click();
    await expect.poll(() => diasDelRango(consultas.at(-1)!)).toBe(29);

    await panel.getByRole('button', { name: 'Este mes', exact: true }).click();
    await expect.poll(() => consultas.at(-1)!.get('desde')!.slice(8, 10)).toBe('01');
  });

  test('H0025-07 - Rango de fechas personalizado', async ({ page }) => {
    const consultas = registrarConsultas(page);
    await abrirPanelDeFixtureB(page);

    // El primer mes que muestra el calendario es el actual, porque el filtro arranca en "Este mes".
    const dias = panelMapaCalor(page).locator('.hm-cal-months > div').first();
    await dias.getByRole('button', { name: '2', exact: true }).click();
    await dias.getByRole('button', { name: '3', exact: true }).click();

    await expect.poll(() => consultas.at(-1)!.get('hasta')!.slice(8, 10)).toBe('03');
    expect(consultas.at(-1)!.get('desde')!.slice(8, 10)).toBe('02');
    await expect(panelMapaCalor(page).getByText(/^02 \w+ \d{4} → 03 \w+ \d{4}$/)).toBeVisible();
  });

  test('H0025-08 - Alcance: todo el rodeo, un rodeo o un animal', async ({ page }) => {
    const consultas = registrarConsultas(page);
    await abrirPanelDeFixtureB(page);
    const panel = panelMapaCalor(page);
    const pestana = (nombre: string) => panel.getByRole('tab', { name: nombre, exact: true });

    await pestana('Todo el rodeo').click();
    await expect.poll(() => consultas.at(-1)!.has('rodeoId')).toBe(false);
    expect(consultas.at(-1)!.has('animalId')).toBe(false);

    await pestana('Rodeo').click();
    await panel.locator('.hm-item-btn', { hasText: 'Rodeo B2' }).click();
    await expect(panel.getByText('1 animales').first()).toBeVisible();
    await expect.poll(() => consultas.at(-1)!.has('rodeoId')).toBe(true);
    const rodeoB2 = consultas.at(-1)!.get('rodeoId');

    await pestana('Rodeo').click();
    await panel.locator('.hm-item-btn', { hasText: 'Rodeo B1' }).click();
    await expect.poll(() => consultas.at(-1)!.get('rodeoId')).not.toBe(rodeoB2);

    await pestana('Animal').click();
    await panel.locator('.hm-item', { hasText: 'B-101' }).click();
    await expect.poll(() => consultas.at(-1)!.has('animalId')).toBe(true);
    expect(consultas.at(-1)!.has('rodeoId')).toBe(false);
    await expect(panel.getByText('Caravana B-101')).toBeVisible();
  });

  test('H0025-09 - Superponer los perímetros del rodeo', async ({ page }) => {
    await abrirPanelDeFixtureB(page);
    const panel = panelMapaCalor(page);
    const superponer = panel.getByRole('switch', { name: 'Mostrar perímetro del rodeo' });

    await panel.locator('.hm-item-btn', { hasText: 'Rodeo B1' }).click();
    await superponer.click();
    await expect(page.locator('path.hm-peri-poly')).toHaveCount(1);
    await expect(panel.getByText('Perímetro B')).toBeVisible();

    // El Rodeo B2 no tiene perímetro asignado: no se ofrece superponer nada.
    await panel.locator('.hm-item-btn', { hasText: 'Rodeo B2' }).click();
    await expect(superponer).toHaveCount(0);
    await expect(page.locator('path.hm-peri-poly')).toHaveCount(0);
  });

  test('H0025-10 - Sin telemetría para el filtro elegido', async ({ page, request }) => {
    const productor = await productorDeEstablecimientoVacio(request);
    await login(page, productor.email, productor.password);
    await activarMapaDeCalor(page);

    await panelMapaCalor(page).getByRole('tab', { name: 'Todo el rodeo', exact: true }).click();

    await expect(panelMapaCalor(page).getByText('Sin datos para el filtro elegido.')).toBeVisible();
  });

  test('H0025-11 - El servicio de análisis no está disponible', async ({ page }) => {
    // Se simula la respuesta parcial con la que la API avisa que ml-geo está degradado.
    await page.route(/\/api\/v1\/mapa-calor\?/, (ruta) => ruta.fulfill({ status: 206, json: { mapa_calor: [] } }));
    await abrirPanelDeFixtureB(page);

    await expect(panelMapaCalor(page).getByText('Servicio de análisis no disponible.')).toBeVisible();
    await expect(page.locator('.leaflet-container')).toBeVisible();
  });

  test('H0025-12 - Si falla la carga se avisa en el panel', async ({ page }) => {
    // Se simula el error: no hay forma de provocarlo con datos reales.
    await page.route(/\/api\/v1\/mapa-calor\?/, (ruta) => ruta.fulfill({ status: 500, json: {} }));
    await abrirPanelDeFixtureB(page);

    await expect(panelMapaCalor(page).getByText('No se pudo cargar el mapa de calor.')).toBeVisible();
    await expect(page.locator('.leaflet-container')).toBeVisible();
  });
});
