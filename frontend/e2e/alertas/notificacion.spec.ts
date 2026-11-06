import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import {
  abrirPanelAlertas,
  animalEnMapa,
  animalJoven,
  crearAlerta,
  enviarPosicion,
  filaAlerta,
  loginPorClave,
  POSICION_FIXTURE_A,
  tarjetaPanel,
  type AnimalEnMapa,
} from '../helpers';

/**
 * Alertas nuevas que llegan a la pantalla sin recargar (SSE) y el camino de la notificación a la acción recomendada.
 * La alerta no se inserta por SQL: la genera el worker de verdad. Un animal con caravana de Fixture A, que está adentro
 * del perímetro, manda por el webhook de ingesta una posición a ~2 km, afuera; el worker detecta la salida, crea la
 * alerta y la publica, y la API la reenvía por el stream.
 */

const ACCION_PERIMETRO = 'Verificar ubicación del animal en el mapa y retornarlo al perímetro autorizado.';

/** ~2 km al norte del centro del perímetro de Fixture A (±0,0045 grados), afuera con cualquier tolerancia. */
const POSICION_AFUERA = { lat: POSICION_FIXTURE_A.lat + 0.02, lng: POSICION_FIXTURE_A.lng };

/** Espera a que la página abra el stream de alertas: si la alerta se genera antes, el evento se pierde. */
function esperarStream(page: Page) {
  return page.waitForResponse(/\/alertas\/stream/);
}

/** El animal sale del perímetro: el worker lo detecta y emite la alerta. */
async function sacarDelPerimetro(request: APIRequestContext, animal: AnimalEnMapa): Promise<void> {
  await enviarPosicion(request, animal.caravana, POSICION_AFUERA, 1);
}

test.describe('H0028 - Alertas nuevas en pantalla sin recargar (UI)', () => {
  // Incluye la ingesta y el procesamiento del worker, además de abrir el mapa con todas sus alertas.
  test.describe.configure({ timeout: 60_000 });

  test('H0028-09 - Una alerta nueva aparece en el panel y en el resumen del mapa sin recargar', async ({
    page,
    request,
  }) => {
    const animal = await animalEnMapa(request);
    await loginPorClave(page, 'productorA');
    const stream = esperarStream(page);
    await abrirPanelAlertas(page);
    await stream;
    // Marca de esta carga de la página: si se recargara, se perdería.
    await page.evaluate(() => ((window as unknown as { __cargaE2e: number }).__cargaE2e = 1));
    const geoespacialAntes = Number(
      await page.locator('.resumen-alertas .card', { hasText: 'Geoespacial' }).getByRole('heading', { level: 4 }).innerText()
    );
    await expect(tarjetaPanel(page, animal.id)).toHaveCount(0);

    await sacarDelPerimetro(request, animal);

    await expect(tarjetaPanel(page, animal.id)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/^Fuera (hace|ahora)/).first()).toBeVisible();
    await expect(
      page.locator('.resumen-alertas .card', { hasText: 'Geoespacial' }).getByRole('heading', { level: 4 })
    ).not.toHaveText(String(geoespacialAntes));
    expect(await page.evaluate(() => (window as unknown as { __cargaE2e?: number }).__cargaE2e)).toBe(1);
  });

  test('H0028-10 - Una alerta nueva aparece arriba del histórico de alertas sin recargar', async ({ page, request }) => {
    const animal = await animalEnMapa(request);
    await loginPorClave(page, 'veterinarioA');
    const stream = esperarStream(page);
    await page.goto('/alertas');
    await stream;
    await expect(page.getByRole('row').nth(1)).toBeVisible();
    await expect(filaAlerta(page, animal.id)).toHaveCount(0);

    await sacarDelPerimetro(request, animal);

    // Es la más reciente de las de severidad ALTA: queda primera de la lista.
    await expect(filaAlerta(page, animal.id)).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('tbody tr').first()).toContainText(`#${animal.id}`);
    await expect(filaAlerta(page, animal.id)).toContainText('PENDIENTE');
  });
});

test.describe('H0029 - Acción recomendada visible en menos de 30 s (UI)', () => {
  test.describe.configure({ timeout: 60_000 });

  test('H0029-06 - De la notificación a la acción recomendada, medido, con una alerta generada por el worker', async ({
    page,
    request,
  }) => {
    const animal = await animalEnMapa(request);
    await loginPorClave(page, 'camperoA');
    const stream = esperarStream(page);
    await abrirPanelAlertas(page);
    await stream;

    await sacarDelPerimetro(request, animal);

    // La notificación: la tarjeta de la alerta nueva en el panel. Desde ahí corre el tiempo.
    const tarjeta = tarjetaPanel(page, animal.id);
    await expect(tarjeta).toBeVisible({ timeout: 20_000 });
    const desde = Date.now();
    await tarjeta.click();
    await expect(page.getByRole('heading', { name: 'Acción recomendada' })).toBeVisible();
    await expect(page.getByText(ACCION_PERIMETRO)).toBeVisible();
    const segundos = (Date.now() - desde) / 1000;

    expect(segundos, `tardó ${segundos.toFixed(1)} s desde la notificación hasta la acción`).toBeLessThan(30);
  });

  test('H0029-07 - La acción recomendada de celo y salud se ve en la tarjeta y en el detalle', async ({
    page,
    browser,
    request,
  }) => {
    const [celo, salud] = [await animalJoven(request), await animalJoven(request)];
    const sufijo = Date.now();
    const accionCelo = `Inseminar dentro de la ventana óptima (${sufijo})`;
    const accionSalud = `Apartar al animal para revisión clínica (${sufijo})`;
    crearAlerta(celo.id, 'CELO', { accionRecomendada: accionCelo });
    crearAlerta(salud.id, 'SALUD', { severidad: 'MEDIA', accionRecomendada: accionSalud });

    // Veterinario: celo. Campero: salud, que ve la acción aunque no pueda actuar sobre ella.
    const sesiones = [
      { clave: 'veterinarioA', animal: celo, accion: accionCelo, page },
      { clave: 'camperoA', animal: salud, accion: accionSalud, page: await (await browser.newContext()).newPage() },
    ];
    for (const { clave, animal, accion, page: p } of sesiones) {
      await loginPorClave(p, clave);
      const desde = Date.now();
      await abrirPanelAlertas(p);
      // En la tarjeta, sin abrir nada.
      await expect(p.getByText(accion), clave).toHaveCount(1);
      await tarjetaPanel(p, animal.id).click();
      // Y en el detalle.
      await expect(p.getByRole('heading', { name: 'Acción recomendada' }), clave).toBeVisible();
      await expect(p.getByText(accion), clave).toHaveCount(2);
      expect((Date.now() - desde) / 1000, clave).toBeLessThan(30);
    }
    await sesiones[1].page.context().close();
  });
});
