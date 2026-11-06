import { test, expect, type Page } from '@playwright/test';
import {
  animalJoven,
  asegurarRodeo,
  contextoRodeo,
  crearAlertaCelo,
  login,
  loginPorClave,
  productorDeEstablecimientoVacio,
  sufijoUnico,
} from '../helpers';

/**
 * Dashboard. Los valores exactos salen de Fixture B, que ningún spec modifica: 6 animales en calibración (4 gestantes y
 * 2 con posible celo) y 5 alertas pendientes, 4 en el Rodeo B1 y la sanitaria en el Rodeo B2. Necesita el escenario
 * recién cargado: las ventanas de celo empiezan a abrir a las 2 horas de la carga y cambian los valores.
 *
 * Las secciones muestran el alias del animal ("Sanitaria B"), no su identificador.
 */

/** Se busca por id: el ícono de la pestaña forma parte de su nombre accesible. */
async function abrirPestana(page: Page, pestana: 'salud' | 'ejecutivo') {
  await page.goto('/dashboard');
  await page.locator(`#dash-tab-${pestana}`).click();
}

async function filtrarPorLote(page: Page, lote: string) {
  await page.locator('select.dash-lote-select', { hasText: 'Lote: todos' }).selectOption({ label: lote });
}

/** Tarjeta del Dashboard por su título. */
function tarjeta(page: Page, titulo: string) {
  return page.locator('.dash-card', { has: page.locator('.dash-card-title', { hasText: titulo }) });
}

function indicadorCentroMando(page: Page, nombre: string) {
  return page.locator('.dash-kpi-chip', { hasText: nombre });
}

function indicadorSalud(page: Page, nombre: string) {
  return page.locator('.dash-salud-kpi', { hasText: nombre }).locator('.dash-salud-kpi-val');
}

/** Simula un 500 en una pestaña: no hay forma de provocarlo con datos reales. */
async function fallarPestana(page: Page, endpoint: 'centro-mando' | 'salud-reproduccion' | 'resumen-ejecutivo') {
  await page.route(new RegExp(`/dashboard/${endpoint}(\\?|$)`), (ruta) => ruta.fulfill({ status: 500, json: {} }));
}

async function loginEstablecimientoVacio(page: Page, request: Parameters<typeof productorDeEstablecimientoVacio>[0]) {
  const productor = await productorDeEstablecimientoVacio(request);
  await login(page, productor.email, productor.password);
}

test.describe('H0043 - Dashboard: Centro de mando (UI)', () => {
  test('H0043-07 - Abre en Centro de mando con los indicadores del establecimiento', async ({ page }) => {
    await loginPorClave(page, 'productorB');
    await page.goto('/dashboard');

    await expect(page.getByRole('heading', { name: 'Requiere atención hoy' })).toBeVisible();
    await expect(page.locator('#dash-tab-mando')).toHaveClass(/dash-tab-active/);
    await expect(indicadorCentroMando(page, 'Alertas pendientes').locator('.dash-kpi-chip-val')).toHaveText('5');
    await expect(indicadorCentroMando(page, 'Alertas pendientes')).toContainText('2 celo · 1 salud · 1 perímetro');
    await expect(indicadorCentroMando(page, 'Ventanas abiertas').locator('.dash-kpi-chip-val')).toHaveText('0');
    await expect(indicadorCentroMando(page, 'Bajo observación').locator('.dash-kpi-chip-val')).toHaveText('0');
    await expect(indicadorCentroMando(page, 'Cobertura de caravanas').locator('.dash-kpi-chip-val')).toHaveText('0%');
  });

  test('H0043-08 - Cola de atención: Atender abre la ficha y Ver todas las alertas', async ({ page }) => {
    await loginPorClave(page, 'productorB');
    await page.goto('/dashboard');

    const filas = page.locator('.dash-cola-row');
    await expect(filas).toHaveCount(5);
    await expect(filas.filter({ hasText: 'Fuera B' })).toContainText('PERÍMETRO');
    await expect(filas.filter({ hasText: 'Celo B' })).toContainText('CELO');
    // La sanitaria de severidad media va última: las otras cuatro alertas son de severidad alta.
    await expect(filas.last()).toContainText('Sanitaria B');
    await expect(filas.last()).toContainText('SALUD');

    await page.getByRole('button', { name: /Ver todas \(5\)/ }).click();
    await expect(page).toHaveURL(/\/alertas/);

    await page.goBack();
    await filas.last().getByRole('button', { name: 'Atender' }).click();
    await expect(page).toHaveURL(/\/animales\/\d+/);
  });

  test('H0043-09 - Filtro por lote', async ({ page }) => {
    await loginPorClave(page, 'productorB');
    await page.goto('/dashboard');
    await expect(page.locator('.dash-cola-row')).toHaveCount(5);

    await filtrarPorLote(page, 'Rodeo B2');

    await expect(page.locator('.dash-cola-row')).toHaveCount(1);
    await expect(page.locator('.dash-cola-row')).toContainText('Sanitaria B');
    await expect(indicadorCentroMando(page, 'Alertas pendientes').locator('.dash-kpi-chip-val')).toHaveText('1');
  });

  test('H0043-10 - Ventanas de inseminación destacadas según el tiempo restante', async ({ page, request }) => {
    // Rodeo propio para contar solo estas ventanas, sin depender de las alertas de otros tests en Fixture A.
    const rodeo = `Rodeo E5 ${sufijoUnico()}`;
    const rodeoId = await asegurarRodeo(request, await contextoRodeo(request), rodeo);
    const [critica, porVencer, holgada] = [await animalJoven(request, rodeoId), await animalJoven(request, rodeoId), await animalJoven(request, rodeoId)];
    crearAlertaCelo(critica.id, 22); // la ventana cierra a las 24 h de la detección
    crearAlertaCelo(porVencer.id, 20);
    crearAlertaCelo(holgada.id, 10);

    await loginPorClave(page, 'productorA');
    await page.goto('/dashboard');
    await filtrarPorLote(page, rodeo);

    const ventana = (animal: { idCaravanaOficial: string }) =>
      page.locator('.dash-ventana-row', { hasText: animal.idCaravanaOficial }).locator('.dash-ventana-restante');
    await expect(ventana(critica)).toHaveClass(/dash-ventana-restante-urgente/);
    await expect(ventana(porVencer)).toHaveClass(/dash-ventana-restante-warning/);
    await expect(ventana(holgada)).toHaveClass(/dash-ventana-restante-ok/);
    await expect(indicadorCentroMando(page, 'Ventanas abiertas').locator('.dash-kpi-chip-val')).toHaveText('3');
  });

  test('H0043-11 - Estado del monitoreo, mapa en vivo y embudo reproductivo', async ({ page }) => {
    await loginPorClave(page, 'productorB');
    await page.goto('/dashboard');

    await expect(page.locator('.dash-coverage-leg-row', { hasText: 'En calibración' })).toContainText('6');
    await expect(page.locator('.dash-map-wrap img.leaflet-marker-icon')).toHaveCount(6);
    const embudo = tarjeta(page, 'Embudo reproductivo');
    await expect(embudo.locator('.dash-embudo-leg-item', { hasText: 'Gestante' })).toContainText('4');
    await expect(embudo.locator('.dash-embudo-leg-item', { hasText: 'Posible celo' })).toContainText('2');
  });

  test('H0043-12 - Establecimiento sin datos', async ({ page, request }) => {
    await loginEstablecimientoVacio(page, request);
    await page.goto('/dashboard');

    await expect(indicadorCentroMando(page, 'Alertas pendientes').locator('.dash-kpi-chip-val')).toHaveText('0');
    await expect(page.getByText('Sin alertas pendientes — todo en orden')).toBeVisible();
    await expect(page.getByText('Sin ventanas activas')).toBeVisible();
    await expect(tarjeta(page, 'Embudo reproductivo')).toContainText('Sin animales reproductivos registrados');
    await expect(page.getByText('Sin alertas en las últimas 8 semanas')).toBeVisible();
  });

  test('H0043-13 - Si falla la carga se avisa y las otras pestañas siguen funcionando', async ({ page }) => {
    await fallarPestana(page, 'centro-mando');
    await loginPorClave(page, 'productorB');
    await page.goto('/dashboard');

    await expect(page.getByText('Error al cargar el centro de mando.')).toBeVisible();

    await page.locator('#dash-tab-salud').click();
    await expect(page.getByRole('heading', { name: 'Salud y reproducción del rodeo' })).toBeVisible();
    await expect(indicadorSalud(page, 'En calibración')).toHaveText('6');
  });
});

test.describe('H0044 - Dashboard: Salud y reproducción (UI)', () => {
  test('H0044-09 - Indicadores del rodeo', async ({ page }) => {
    await loginPorClave(page, 'veterinarioB');
    await abrirPestana(page, 'salud');

    await expect(page).toHaveURL(/tab=salud/);
    // Las paridas del escenario llevan 60 días: según la hora, el promedio redondea a 60 o 61.
    await expect(indicadorSalud(page, 'Días abiertos (promedio)')).toHaveText(/^6[01]$/);
    await expect(indicadorSalud(page, 'Ventanas perdidas (30 d)')).toHaveText('0%');
    await expect(indicadorSalud(page, 'Salud sin atender')).toHaveText('0');
    await expect(indicadorSalud(page, 'Celos sin atender')).toHaveText('2');
    await expect(indicadorSalud(page, 'En calibración')).toHaveText('6');
  });

  test('H0044-10 - Secciones de la pestaña con los datos del rodeo', async ({ page }) => {
    await loginPorClave(page, 'veterinarioB');
    await abrirPestana(page, 'salud');

    const revisar = tarjeta(page, 'Animales a revisar');
    await expect(revisar.locator('.dash-salud-revisar-row', { hasText: 'Sanitaria B' })).toBeVisible();
    await expect(revisar).toContainText('Todos los animales sanos');

    const embudo = tarjeta(page, 'Embudo reproductivo');
    await expect(embudo.locator('.dash-embudo-leg-item', { hasText: 'Gestante' })).toContainText('4');
    await expect(embudo).toContainText('Excepciones que necesitan decisión');

    await expect(tarjeta(page, 'Partos esperados')).toContainText('Por semana, próximas 12 semanas');
    await expect(tarjeta(page, 'Desempeño reproductivo').locator('.dash-desempenyo-row', { hasText: 'Celo detectado' })).toContainText('2');
    await expect(tarjeta(page, 'Reincidencia')).toContainText('Sin reincidentes en 30 d');
    await expect(tarjeta(page, 'Última intervención')).toContainText('Sin intervenciones registradas');
  });

  test('H0044-11 - Filtro por lote', async ({ page }) => {
    await loginPorClave(page, 'veterinarioB');
    await abrirPestana(page, 'salud');
    await expect(indicadorSalud(page, 'Celos sin atender')).toHaveText('2');

    await filtrarPorLote(page, 'Rodeo B2');

    await expect(indicadorSalud(page, 'Celos sin atender')).toHaveText('0');
    await expect(tarjeta(page, 'Animales a revisar').locator('.dash-salud-revisar-row', { hasText: 'Sanitaria B' })).toBeVisible();
    await expect(tarjeta(page, 'Embudo reproductivo').locator('.dash-embudo-leg-item', { hasText: 'Gestante' })).toContainText('1');
  });

  test('H0044-12 - Desde Animales a revisar se abre el detalle de la alerta', async ({ page }) => {
    await loginPorClave(page, 'veterinarioB');
    await abrirPestana(page, 'salud');

    await tarjeta(page, 'Animales a revisar').locator('.dash-salud-revisar-row', { hasText: 'Sanitaria B' }).click();

    await expect(page.getByText('Desvío sanitario detectado')).toBeVisible();
    await expect(page.getByText('Caída de actividad respecto de su perfil').first()).toBeVisible();
  });

  test('H0044-13 - Selector de período y vista anual de partos', async ({ page }) => {
    await loginPorClave(page, 'veterinarioB');
    await abrirPestana(page, 'salud');

    await page.getByLabel('Período').selectOption({ label: 'Últimos 7 días' });
    await expect(indicadorSalud(page, 'Ventanas perdidas (7 d)')).toBeVisible();
    await expect(tarjeta(page, 'Desempeño reproductivo')).toContainText('Últimos 7 días');

    await page.getByLabel('Vista de partos esperados').selectOption({ label: 'Próximo año' });
    await expect(tarjeta(page, 'Partos esperados')).toContainText('Por día, próximos 12 meses');
    await expect(page.locator('.dash-partos-cell').first()).toBeVisible();
  });

  test('H0044-14 - Establecimiento sin datos', async ({ page, request }) => {
    await loginEstablecimientoVacio(page, request);
    await abrirPestana(page, 'salud');

    await expect(page.getByText('Sin celos con ventana abierta')).toBeVisible();
    await expect(tarjeta(page, 'Embudo reproductivo')).toContainText('Sin animales reproductivos registrados');
    await expect(page.getByText('Sin alertas de salud pendientes')).toBeVisible();
    await expect(page.getByText('Todos los animales sanos')).toBeVisible();
    await expect(page.getByText('Sin animales gestantes registrados')).toBeVisible();
    await expect(page.getByText('Sin reincidentes en 30 d')).toBeVisible();
    await expect(page.getByText('Sin intervenciones registradas')).toBeVisible();
  });

  test('H0044-15 - Si falla la carga se avisa en la pestaña', async ({ page }) => {
    await fallarPestana(page, 'salud-reproduccion');
    await loginPorClave(page, 'veterinarioB');
    await abrirPestana(page, 'salud');

    await expect(page.getByText('Error al cargar salud y reproducción.')).toBeVisible();
    await expect(page.locator('.dash-tabs')).toBeVisible();
  });
});

test.describe('H0045 - Dashboard: Resumen ejecutivo (UI)', () => {
  test('H0045-06 - Tabla por rodeo', async ({ page }) => {
    await loginPorClave(page, 'productorB');
    await abrirPestana(page, 'ejecutivo');

    await expect(page.getByRole('heading', { name: 'Resumen del establecimiento' })).toBeVisible();
    const celdas = (rodeo: string) => page.getByRole('row').filter({ hasText: rodeo }).getByRole('cell');
    // Rodeo, animales, cobertura, alertas pendientes, alertas cada 100, ventanas perdidas, % de vacías y Ver.
    await expect(celdas('Rodeo B1').nth(1)).toHaveText('5');
    await expect(celdas('Rodeo B1').nth(2)).toHaveText('0%');
    await expect(celdas('Rodeo B1').nth(3)).toHaveText('4');
    await expect(celdas('Rodeo B1').nth(4)).toContainText('80/100');
    await expect(celdas('Rodeo B1').nth(5)).toHaveText('0');
    await expect(celdas('Rodeo B1').nth(6)).toHaveText('0%');
    await expect(celdas('Rodeo B2').nth(1)).toHaveText('1');
    await expect(celdas('Rodeo B2').nth(3)).toHaveText('1');
    await expect(celdas('Rodeo B2').nth(4)).toContainText('100/100');
  });

  test('H0045-07 - Ver abre el listado de animales del rodeo', async ({ page }) => {
    await loginPorClave(page, 'productorB');
    await abrirPestana(page, 'ejecutivo');

    await page.getByRole('row').filter({ hasText: 'Rodeo B2' }).getByRole('button', { name: 'Ver' }).click();

    await expect(page).toHaveURL(/\/animales\?rodeo=\d+/);
    await expect(page.locator('tr.animal-row')).toHaveCount(1);
    await expect(page.locator('tr.animal-row')).toContainText('B-103');
  });

  test('H0045-08 - Tendencia de alertas y actuación del equipo', async ({ page }) => {
    await loginPorClave(page, 'productorB');
    await abrirPestana(page, 'ejecutivo');

    const leyenda = tarjeta(page, 'Tendencia de alertas').locator('.dash-trend-legend');
    await expect(leyenda).toContainText('Celo');
    await expect(leyenda).toContainText('Salud');
    await expect(leyenda).toContainText('Perímetro');

    const equipo = tarjeta(page, '¿Está actuando el equipo?');
    const indicador = (nombre: string) =>
      equipo.locator('.dash-team-kpi', { has: page.getByText(nombre, { exact: true }) }).locator('.dash-team-kpi-val');
    await expect(indicador('En cola')).toHaveText('5');
    await expect(indicador('Atendidas')).toHaveText('0%');
    await expect(indicador('Descartadas')).toHaveText('0%');
    await expect(equipo).toContainText('Sin alertas atendidas en los últimos 30 días');
  });

  test('H0045-09 - Establecimiento sin datos', async ({ page, request }) => {
    await loginEstablecimientoVacio(page, request);
    await abrirPestana(page, 'ejecutivo');

    await expect(page.getByText('No hay rodeos activos registrados')).toBeVisible();
    await expect(page.getByText('Sin datos de alertas en las últimas 8 semanas')).toBeVisible();
    await expect(page.getByText('Sin alertas atendidas en los últimos 30 días')).toBeVisible();
  });

  test('H0045-10 - Si falla la carga se avisa en la pestaña', async ({ page }) => {
    await fallarPestana(page, 'resumen-ejecutivo');
    await loginPorClave(page, 'productorB');
    await abrirPestana(page, 'ejecutivo');

    await expect(page.getByText('Error al cargar el resumen ejecutivo.')).toBeVisible();
    await expect(page.locator('.dash-tabs')).toBeVisible();
  });
});
