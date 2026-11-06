import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import {
  animalJoven,
  crearAlerta,
  haceHoras,
  intervenirPorApi,
  loginPorClave,
  resolverPorApi,
} from '../helpers';

/**
 * H0024 - Línea de tiempo de alertas del animal. Está en la pestaña Reproducción de la ficha y muestra, en orden, las
 * alertas de celo, las inseminaciones y los estados reproductivos del animal.
 */

interface Historial {
  animalId: number;
  celoConfirmado: number;
  celoPendiente: number;
}

/** Celo de hace 50 h confirmado con su inseminación (hace 44 h) y un celo de hace 3 h, pendiente. */
async function armarHistorial(request: APIRequestContext): Promise<Historial> {
  const animal = await animalJoven(request);
  const celoConfirmado = crearAlerta(animal.id, 'CELO', { minutosAtras: 50 * 60 });
  await resolverPorApi(request, celoConfirmado, 'CONFIRMADA', 'veterinarioA');
  await intervenirPorApi(request, celoConfirmado, {
    tipo: 'INSEMINACION',
    descripcion: 'IA con semen Angus',
    fechaHora: haceHoras(44),
  });
  const celoPendiente = crearAlerta(animal.id, 'CELO', { minutosAtras: 3 * 60 });
  return { animalId: animal.id, celoConfirmado, celoPendiente };
}

async function abrirLineaDeTiempo(page: Page, animalId: number): Promise<void> {
  await page.goto(`/animales/${animalId}?tab=reproduccion`);
  await expect(page.getByRole('heading', { name: 'Línea de tiempo reproductiva' })).toBeVisible();
}

/** Los nodos de la línea de tiempo, en el orden en que se muestran. */
function nodos(page: Page) {
  return page.getByRole('button', { name: /^Ver detalle de / });
}

test.describe('H0024 - Línea de tiempo de alertas por animal (UI)', () => {
  // El histórico y el panel cargan todas las alertas pendientes del establecimiento: con la base cargada de otros specs
  // superan los 30 s por defecto.
  test.describe.configure({ timeout: 60_000 });

  test('H0024-05 - Alertas, inseminaciones y estados del animal en orden cronológico', async ({ page, request }) => {
    const h = await armarHistorial(request);
    await loginPorClave(page, 'productorA');

    await abrirLineaDeTiempo(page, h.animalId);

    // El último es el estado reproductivo "Celo" que quedó registrado al confirmar la primera alerta, hace un momento.
    await expect(nodos(page)).toHaveCount(4);
    expect(await nodos(page).evaluateAll((els) => els.map((e) => e.getAttribute('aria-label')))).toEqual([
      'Ver detalle de Celo',
      'Ver detalle de Inseminación',
      'Ver detalle de Celo',
      'Ver detalle de Celo',
    ]);
    // Cada nodo muestra la fecha del evento.
    await expect(page.locator('.lt-nodo-label-time').first()).not.toHaveText('—');
  });

  test('H0024-06 - Datos de cada evento: estado de la alerta, interventor y alerta asociada', async ({
    page,
    request,
  }) => {
    const h = await armarHistorial(request);
    await loginPorClave(page, 'veterinarioA');
    await abrirLineaDeTiempo(page, h.animalId);

    // Celo confirmado: con su estado y el acceso a la alerta.
    await nodos(page).nth(0).click();
    await expect(page.getByText('Alerta Celo')).toBeVisible();
    await expect(page.getByText(/Estado:\s*Confirmada/)).toBeVisible();
    await expect(page.getByRole('button', { name: `Ver alerta #${h.celoConfirmado}` })).toBeVisible();

    // Inseminación: quién la hizo, qué anotó y a qué alerta corresponde.
    await nodos(page).nth(1).click();
    const popup = page.locator('.lt-popup');
    await expect(popup).toContainText('IA con semen Angus');
    await expect(popup).toContainText('Veterinario A');
    await expect(popup).toContainText(`Asociada a alerta #${h.celoConfirmado}`);

    // Celo pendiente: figura sin atender.
    await nodos(page).nth(2).click();
    await expect(page.getByText(/Estado:\s*Pendiente/)).toBeVisible();
  });

  test('H0024-07 - Desde la línea de tiempo se abre la alerta y al resolverla se actualiza', async ({
    page,
    request,
  }) => {
    const h = await armarHistorial(request);
    await loginPorClave(page, 'veterinarioA');
    await abrirLineaDeTiempo(page, h.animalId);

    await nodos(page).nth(2).click();
    await page.getByRole('button', { name: `Ver alerta #${h.celoPendiente}` }).click();
    await expect(page.getByRole('heading', { name: 'Dato de origen' })).toBeVisible();
    await page.getByRole('button', { name: /Confirmar celo$/ }).click();

    // La línea de tiempo se recarga con la alerta ya confirmada.
    await expect(page.getByRole('heading', { name: 'Dato de origen' })).toHaveCount(0);
    await nodos(page).nth(2).click();
    await expect(page.getByText(/Estado:\s*Confirmada/)).toBeVisible();
  });

  test('H0024-08 - Filtro por período y animal sin eventos', async ({ page, request }) => {
    const animal = await animalJoven(request);
    crearAlerta(animal.id, 'CELO', { minutosAtras: 40 * 24 * 60 });
    crearAlerta(animal.id, 'CELO', { minutosAtras: 2 * 24 * 60 });
    const sinEventos = await animalJoven(request);
    await loginPorClave(page, 'camperoA');

    // Por defecto muestra todo el historial; "30 días" deja solo lo reciente.
    await abrirLineaDeTiempo(page, animal.id);
    await expect(nodos(page)).toHaveCount(2);
    await page.getByRole('button', { name: '30 días', exact: true }).click();
    await expect(nodos(page)).toHaveCount(1);
    await page.getByRole('button', { name: 'Todo', exact: true }).click();
    await expect(nodos(page)).toHaveCount(2);

    // Un animal sin eventos lo dice, sin filtros y con ellos.
    await abrirLineaDeTiempo(page, sinEventos.id);
    await expect(page.getByText('Sin eventos registrados para este animal.')).toBeVisible();
    await page.getByRole('button', { name: '30 días', exact: true }).click();
    await expect(page.getByText('Sin eventos registrados con los filtros aplicados.')).toBeVisible();
  });
});
