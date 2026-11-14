import { test, expect, type Page } from '@playwright/test';
import {
  animalEnMapa,
  crearAlertaCelo,
  crearAlertaPerimetro,
  crearAlertaSalud,
  enviarPosicion,
  idEstablecimiento,
  login,
  loginPorClave,
  mostrarSoloEnMapa,
  obtenerEstablecimientoSeleccionado,
  pines,
  POSICION_FIXTURE_A,
  productorDeEstablecimientoVacio,
  tokenPorClave,
} from '../helpers';

/**
 * Mapa del rodeo. Fixture B se verifica tal como lo deja el escenario. Los pines se prueban sobre animales propios de
 * Fixture A, con su telemetría reciente: los del escenario pasan a "sin datos recientes" a los 15 minutos de la carga.
 */

async function abrirMapaCon(page: Page, ...animales: { id: number }[]) {
  await loginPorClave(page, 'productorA');
  await mostrarSoloEnMapa(page, ...animales);
  await page.goto('/mapa');
}

/** Tarjeta de resumen de alertas del mapa, por su título. */
function tarjetaResumen(page: Page, titulo: string) {
  return page.locator('.resumen-alertas .card', { hasText: titulo });
}

test.describe('H0012 - Mapa de posiciones del rodeo (UI)', () => {
  test('H0012-06 - Muestra un pin por cada animal del establecimiento', async ({ page }) => {
    await loginPorClave(page, 'productorB');
    await page.goto('/mapa');

    await expect(pines(page)).toHaveCount(6);
  });

  test('H0012-07 - El pin se mueve al llegar telemetría nueva, sin recargar', async ({ page, request }) => {
    const animal = await animalEnMapa(request, 20);
    await abrirMapaCon(page, animal);
    const pin = pines(page, 'sindatos');
    await expect(pin).toHaveCount(1);
    const posicionInicial = await pin.evaluate((el) => (el as HTMLElement).style.transform);

    // El stream puede no estar conectado todavía: se reintenta con otro frame hasta que el pin reciba el evento.
    let frame = 1;
    await expect(async () => {
      await enviarPosicion(request, animal.caravana, { lat: POSICION_FIXTURE_A.lat + 0.001, lng: POSICION_FIXTURE_A.lng }, frame++);
      await expect(pines(page, 'normal')).toHaveCount(1, { timeout: 3000 });
    }).toPass({ timeout: 25000 });

    const posicionFinal = await pines(page, 'normal').evaluate((el) => (el as HTMLElement).style.transform);
    expect(posicionFinal).not.toBe(posicionInicial);
  });

  test('H0012-08 - Al hacer clic en el pin se abre el panel del animal', async ({ page, request }) => {
    const animal = await animalEnMapa(request);
    await abrirMapaCon(page, animal);

    await pines(page, 'normal').click();

    await expect(page.getByRole('heading', { name: animal.idCaravanaOficial })).toBeVisible();
    await expect(page.getByText('Información del animal')).toBeVisible();
    await expect(page.getByText('Holando')).toBeVisible();
    await expect(page.getByText('Última posición').locator('..').locator('..')).toContainText(/\d{2}\/\d{2}\/\d{4}/);
    await expect(page.getByText('Sin alertas activas')).toBeVisible();
    await expect(page.getByRole('button', { name: /Ver ficha completa/ })).toBeVisible();
  });

  test('H0012-09 - Sin datos hace más de 15 minutos', async ({ page, request }) => {
    const animal = await animalEnMapa(request, 20);
    await abrirMapaCon(page, animal);

    await pines(page, 'sindatos').click();

    await expect(page.getByText('Sin datos recientes')).toBeVisible();
  });

  test('H0012-10 - Establecimiento sin animales', async ({ page, request }) => {
    const productor = await productorDeEstablecimientoVacio(request);
    await login(page, productor.email, productor.password);
    await page.goto('/mapa');

    await expect(tarjetaResumen(page, 'Reproductivo')).toContainText('0');
    await expect(tarjetaResumen(page, 'Sanitario')).toContainText('0');
    await expect(tarjetaResumen(page, 'Geoespacial')).toContainText('0');
    await expect(pines(page)).toHaveCount(0);
    await expect(page.locator('.leaflet-container')).toBeVisible();
  });

  test('H0012-11 - Si falla la carga de posiciones la página sigue funcionando', async ({ page }) => {
    // Se simula el error: no hay forma de provocarlo con datos reales.
    await page.route(/\/api\/v1\/animales\/mapa(\?|$)/, (ruta) => ruta.fulfill({ status: 500, json: {} }));
    await loginPorClave(page, 'productorB');
    await page.goto('/mapa');

    await expect(page.getByText('No se pudieron cargar los animales del mapa. Recargá la página para reintentar.')).toBeVisible();
    await expect(page.locator('.leaflet-container')).toBeVisible();
    await expect(pines(page)).toHaveCount(0);
    await page.getByRole('button', { name: 'Alertas', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Alertas', exact: true })).toHaveCount(0);
  });

  test('H0012-12 - Con varios establecimientos solo se ven los animales del seleccionado', async ({ page, request }) => {
    await loginPorClave(page, 'productorAmbos');
    // Sin selección guardada, como en el primer ingreso: el mapa arranca sin establecimiento y pide todos los animales.
    // El store de establecimientos se persiste en sessionStorage (no en localStorage), que sobrevive al goto. Si el
    // Dashboard llegó a cargar los establecimientos antes de navegar, la selección quedaba guardada, el mapa pedía
    // directo con filtro y la consulta sin filtro que espera el test no salía nunca. Se borra antes de que cargue la
    // app: en el Dashboard ya abierto, el store la volvería a guardar.
    await page.addInitScript(() => sessionStorage.removeItem('vigia-establecimiento'));

    // La consulta sin filtro se retiene hasta después de la filtrada, para que siempre responda última. La espera de
    // la filtrada se registra antes de navegar: si se registrara recién dentro del handler, la filtrada podía
    // responder mientras Playwright entregaba la intercepción, y el handler esperaba algo que ya había pasado.
    const sinFiltro = /\/api\/v1\/animales\/mapa$/;
    const respuestaFiltrada = page.waitForResponse(/\/api\/v1\/animales\/mapa\?establecimientoId=/);
    await page.route(sinFiltro, async (ruta) => {
      await respuestaFiltrada;
      await page.waitForTimeout(1500);
      await ruta.continue();
    });
    const respuestaSinFiltro = page.waitForResponse(sinFiltro);

    await page.goto('/mapa');
    await Promise.all([respuestaFiltrada, respuestaSinFiltro]);
    // Da tiempo a que el mapa dibuje esa respuesta: antes, la cantidad de pines todavía sería la correcta.
    await page.waitForTimeout(1000);

    const token = await tokenPorClave(request, 'productorAmbos');
    const establecimientoId = await idEstablecimiento(request, token, await obtenerEstablecimientoSeleccionado(page));
    // Fixture A suma animales de otros tests: lo esperado se vuelve a consultar en cada intento.
    await expect
      .poll(
        async () => {
          const respuesta = await request.get(`/api/v1/animales/mapa?establecimientoId=${establecimientoId}`, {
            headers: { Authorization: `Bearer ${token}` },
          });
          const conPosicion = ((await respuesta.json()) as { latitud: number | null }[]).filter((a) => a.latitud != null);
          return (await pines(page).count()) - conPosicion.length;
        },
        { timeout: 3000 }
      )
      .toBe(0);
  });
});

test.describe('H0013 - Alertas activas en el mapa (UI)', () => {
  test('H0013-05 - Pin normal sin alertas activas', async ({ page, request }) => {
    const animal = await animalEnMapa(request);
    await abrirMapaCon(page, animal);

    await expect(pines(page, 'normal')).toHaveCount(1);
  });

  test('H0013-06 - Pin de alerta con severidad media', async ({ page, request }) => {
    const animal = await animalEnMapa(request);
    crearAlertaSalud(animal.id, 'MEDIA');
    await abrirMapaCon(page, animal);

    await expect(pines(page, 'borde')).toHaveCount(1);
  });

  test('H0013-07 - Pin crítico con severidad alta', async ({ page, request }) => {
    const animal = await animalEnMapa(request);
    crearAlertaSalud(animal.id, 'ALTA');
    await abrirMapaCon(page, animal);

    await expect(pines(page, 'critico')).toHaveCount(1);
  });

  test('H0013-08 - Con varias alertas el pin refleja la mayor severidad y el panel muestra todas', async ({ page, request }) => {
    const animal = await animalEnMapa(request);
    crearAlertaSalud(animal.id, 'MEDIA');
    crearAlertaCelo(animal.id, 1);
    await abrirMapaCon(page, animal);

    await pines(page, 'critico').click();

    await expect(page.getByText(`#${animal.id} en estado CRITICO`)).toBeVisible();
    await expect(page.getByText(`#${animal.id} en estado BORDE`)).toBeVisible();
    await expect(page.getByText('Reproductivo', { exact: true }).last()).toBeVisible();
    await expect(page.getByText('Sanitario', { exact: true }).last()).toBeVisible();
  });

  test('H0013-09 - Desde el panel se abre el detalle de la alerta', async ({ page, request }) => {
    const animal = await animalEnMapa(request);
    crearAlertaCelo(animal.id, 1);
    await abrirMapaCon(page, animal);

    await pines(page, 'critico').click();
    await page.getByText(`#${animal.id} en estado CRITICO`).click();

    await expect(page.getByText('Ventana de inseminación')).toBeVisible();
    await expect(page.getByRole('button', { name: /Confirmar celo e inseminación/i })).toBeVisible();
  });

  test('H0013-10 - El panel indica el tiempo fuera del perímetro', async ({ page, request }) => {
    const animal = await animalEnMapa(request);
    crearAlertaPerimetro(animal.id);
    await abrirMapaCon(page, animal);

    await pines(page, 'critico').click();

    await expect(page.getByText(/^Fuera hace (\d+ min|\d+h)/)).toBeVisible();
  });

  test('H0013-11 - Resumen numérico de alertas por tipo', async ({ page }) => {
    await loginPorClave(page, 'veterinarioB');
    await page.goto('/mapa');

    await expect(tarjetaResumen(page, 'Reproductivo')).toContainText('2');
    await expect(tarjetaResumen(page, 'Sanitario')).toContainText('1');
    await expect(tarjetaResumen(page, 'Geoespacial')).toContainText('2');
  });
});
