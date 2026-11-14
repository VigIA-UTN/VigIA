import path from 'node:path';
import { test as base, expect, type Page, type TestInfo } from '@playwright/test';
import {
  animalEnMapa,
  animalJoven,
  crearAlerta,
  email,
  enviarPosicion,
  loginPorClave,
  mostrarSoloEnMapa,
  pines,
  POSICION_FIXTURE_A,
  resolucionEnBase,
  tarjetaPanel,
} from '../helpers';

/**
 * RNF06 - Compatibilidad. Los 5 flujos críticos (login, alta de animal, confirmar alerta, mapa en tiempo real y
 * Dashboard) en cada proyecto de Playwright: chromium, firefox y edge a 1280×720, y mobile-4g a 390 px con red 4G
 * simulada.
 *
 * Cada flujo falla si la página tira un error de JavaScript o escribe un error en la consola (0 errores de
 * renderizado) y deja una captura como evidencia en evidencia-rnf06/<proyecto>/, además de adjuntarla al reporte HTML.
 * La navegación entre pantallas es por el menú, como la haría el usuario: un page.goto en medio de la carga aborta
 * pedidos en curso y Firefox lo informa como error.
 */

/** Perfil "Fast 4G" de Chrome DevTools: 9 Mbps de bajada, 1,5 Mbps de subida y 165 ms de latencia. */
const RED_4G = {
  offline: false,
  latency: 165,
  downloadThroughput: (9_000_000 * 0.9) / 8,
  uploadThroughput: (1_500_000 * 0.9) / 8,
};

/**
 * Firefox escribe "CORS request did not succeed" cuando se cierra un EventSource que todavía está conectando. En el dev
 * server pasa siempre: StrictMode monta, desmonta y vuelve a montar los efectos, así que el primer stream se cierra al
 * instante. Con el build de producción no aparece. Que el stream funciona lo verifica el flujo del mapa.
 */
function esStreamCerradoEnFirefox(texto: string) {
  return (
    /CORS request did not succeed\)\. Status code: \(null\)/.test(texto) && /\/api\/v1\/[a-z/]+\/stream\?/.test(texto)
  );
}

const test = base.extend<{ erroresDeRender: string[] }>({
  erroresDeRender: [
    async ({ page, browserName }, use, testInfo) => {
      if (testInfo.project.name === 'mobile-4g') {
        expect(browserName, 'la red 4G se simula por CDP, que solo existe en Chromium').toBe('chromium');
        const cdp = await page.context().newCDPSession(page);
        await cdp.send('Network.enable');
        await cdp.send('Network.emulateNetworkConditions', RED_4G);
      }

      const errores: string[] = [];
      page.on('pageerror', (error) => errores.push(`pageerror: ${error.message}`));
      page.on('console', (mensaje) => {
        if (mensaje.type() === 'error' && !esStreamCerradoEnFirefox(mensaje.text())) errores.push(`console.error: ${mensaje.text()}`);
      });

      await use(errores);

      expect(errores, 'RNF06: el flujo no tiene errores de renderizado').toEqual([]);
    },
    { auto: true },
  ],
});

test.describe.configure({ timeout: 90_000 });

/**
 * Guarda la captura del estado final del flujo y la adjunta al reporte. Antes espera a que terminen las transiciones
 * (los paneles del mapa entran deslizándose); las animaciones infinitas, como los pines que laten, no se esperan.
 */
async function evidencia(page: Page, testInfo: TestInfo, nombre: string) {
  await page.evaluate(() =>
    Promise.all(
      document
        .getAnimations()
        .filter((a) => a.effect?.getComputedTiming().iterations !== Infinity)
        .map((a) => a.finished.catch(() => undefined))
    )
  );
  const archivo = path.join('evidencia-rnf06', testInfo.project.name, `${nombre}.png`);
  // En móvil, la captura de página completa deja en blanco los mapas de Leaflet que quedan fuera de la pantalla.
  const captura = await page.screenshot({ path: archivo, fullPage: testInfo.project.name !== 'mobile-4g' });
  await testInfo.attach(nombre, { body: captura, contentType: 'image/png' });
}

/** Navega con el navbar: en desktop los links están a la vista; debajo de 992 px (lg), en el menú hamburguesa. */
async function irA(page: Page, opcion: 'Animales' | 'Mapa') {
  if (page.viewportSize()!.width < 992) {
    await page.locator('button[aria-label="Abrir menú"]').click();
    await page.locator('.mobile-drawer-link', { hasText: opcion }).click();
  } else {
    await page.getByRole('link', { name: opcion, exact: true }).click();
  }
}

test.describe('RNF06 - Compatibilidad de los flujos críticos', () => {
  test('RNF06-01 - Login', async ({ page }, testInfo) => {
    await loginPorClave(page, 'productorA');

    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.locator('.dashboard-page')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Requiere atención hoy' })).toBeVisible();
    await evidencia(page, testInfo, 'RNF06-01-login');
  });

  test('RNF06-02 - Alta de animal', async ({ page }, testInfo) => {
    // El identificador y el alias son únicos por establecimiento.
    const idAnimal = `RNF06-${testInfo.project.name}-${Date.now()}`;
    const alias = `Compatibilidad ${testInfo.project.name} ${Date.now()}`;
    await loginPorClave(page, 'productorA');
    await irA(page, 'Animales');
    await expect(page).toHaveURL(/\/animales/);
    await expect(page.locator('.vigia-table')).toBeVisible({ timeout: 30_000 });

    // "+ Animal": el nombre termina en "Animal" para no confundirlo con "Animales" del menú móvil.
    await page.getByRole('button', { name: /Animal$/ }).click();
    const modal = page.getByRole('dialog');
    await expect(modal.getByText('Nuevo Animal')).toBeVisible();
    await modal.locator('input[name="idCaravanaOficial"]').fill(idAnimal);
    await modal.locator('input[name="alias"]').fill(alias);
    await modal.locator('input[name="fechaNacimiento"]').fill('2024-03-15');
    await modal.locator('input[name="raza"]').fill('Holando');
    const lote = modal.locator('select[name="rodeoLoteId"]');
    await expect(lote.locator('option')).not.toHaveCount(1, { timeout: 15_000 });
    await lote.selectOption((await lote.locator('option').nth(1).getAttribute('value'))!);
    await modal.getByRole('button', { name: 'Guardar' }).click();

    await expect(page.getByRole('dialog')).toHaveCount(0, { timeout: 15_000 });
    await page.getByPlaceholder(/Buscar por identificador/i).fill(idAnimal);
    const fila = page.locator('.vigia-table tbody tr.animal-row').filter({ hasText: idAnimal });
    await expect(fila).toHaveCount(1, { timeout: 15_000 });
    await expect(fila).toContainText(alias);
    await evidencia(page, testInfo, 'RNF06-02-alta-animal');
  });

  test('RNF06-03 - Confirmar alerta', async ({ page, request }, testInfo) => {
    const animal = await animalJoven(request);
    const alertaId = crearAlerta(animal.id, 'CELO');
    await loginPorClave(page, 'veterinarioA');

    await irA(page, 'Mapa');
    await page.getByRole('button', { name: 'Alertas', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Alertas', exact: true })).toBeVisible();
    await tarjetaPanel(page, animal.id).click();
    const confirmar = page.getByRole('button', { name: /Confirmar celo$/ });
    await expect(confirmar).toBeVisible();
    await evidencia(page, testInfo, 'RNF06-03a-alerta-pendiente');
    await confirmar.click();

    await expect.poll(() => resolucionEnBase(alertaId).estado, { timeout: 15_000 }).toBe('CONFIRMADA');
    expect(resolucionEnBase(alertaId).usuario).toBe(email('veterinarioA'));
    await expect(page.getByRole('button', { name: 'Descartar alerta' })).toHaveCount(0);
    await evidencia(page, testInfo, 'RNF06-03b-alerta-confirmada');
  });

  test('RNF06-04 - Mapa en tiempo real', async ({ page, request }, testInfo) => {
    const animal = await animalEnMapa(request, 20);
    await loginPorClave(page, 'productorA');
    await mostrarSoloEnMapa(page, animal);
    await irA(page, 'Mapa');
    await expect(page).toHaveURL(/\/mapa/);

    const pinSinDatos = pines(page, 'sindatos');
    await expect(pinSinDatos).toHaveCount(1, { timeout: 20_000 });
    const posicionInicial = await pinSinDatos.evaluate((el) => (el as HTMLElement).style.transform);

    // El stream puede no estar conectado todavía: se reintenta con otro frame hasta que el pin reciba el evento.
    let frame = 1;
    const nueva = { lat: POSICION_FIXTURE_A.lat + 0.001, lng: POSICION_FIXTURE_A.lng };
    await expect(async () => {
      await enviarPosicion(request, animal.caravana, nueva, frame++);
      await expect(pines(page, 'normal')).toHaveCount(1, { timeout: 3_000 });
    }).toPass({ timeout: 40_000 });
    const posicionFinal = await pines(page, 'normal').evaluate((el) => (el as HTMLElement).style.transform);
    expect(posicionFinal).not.toBe(posicionInicial);

    await pines(page, 'normal').click();
    await expect(page.getByRole('heading', { name: animal.idCaravanaOficial })).toBeVisible();
    await expect(page.getByText('Información del animal')).toBeVisible();
    await expect(page.getByText('Sin alertas activas')).toBeVisible();
    await expect(page.getByText('Cargando detalles...')).toHaveCount(0);
    await evidencia(page, testInfo, 'RNF06-04-mapa');
  });

  test('RNF06-05 - Dashboard', async ({ page }, testInfo) => {
    await loginPorClave(page, 'productorB');
    await expect(page).toHaveURL(/\/dashboard/);

    await expect(page.locator('#dash-tab-mando')).toHaveClass(/dash-tab-active/);
    await expect(page.getByRole('heading', { name: 'Requiere atención hoy' })).toBeVisible();
    await expect(page.locator('.dash-kpi-chip', { hasText: 'Alertas pendientes' })).toBeVisible();
    await evidencia(page, testInfo, 'RNF06-05a-centro-de-mando');

    await page.locator('#dash-tab-salud').click();
    await expect(page).toHaveURL(/tab=salud/);
    await expect(page.locator('.dash-salud-kpi', { hasText: 'En calibración' })).toBeVisible();
    await evidencia(page, testInfo, 'RNF06-05b-salud-reproduccion');

    await page.locator('#dash-tab-ejecutivo').click();
    await expect(page.getByRole('heading', { name: 'Resumen del establecimiento' })).toBeVisible();
    await expect(page.getByRole('row').filter({ hasText: 'Rodeo B1' })).toBeVisible();
    await evidencia(page, testInfo, 'RNF06-05c-resumen-ejecutivo');
  });
});
