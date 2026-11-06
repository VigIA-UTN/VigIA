import { test, expect, type Page } from '@playwright/test';
import { loginPorClave, sql, sufijoUnico } from '../helpers';

/**
 * H0027 - Alta y edición de perímetros desde el mapa. El perímetro de VigIA es geográfico (área, rodeos, zona estricta y
 * tolerancia): no tiene un horario nocturno, así que estos casos cubren lo que existe. Cada test crea el suyo y no toca
 * "Perímetro A", del que dependen la detección de salidas y otros specs. El alta usa el polígono que propone el mapa.
 */

interface PerimetroEnBase {
  nombre: string;
  descripcion: string;
  zonaEstricta: boolean;
  tolerancia: number;
  activo: boolean;
}

/** El perímetro de Fixture A con ese nombre en la base, o null si no existe. */
function perimetroEnBase(nombre: string): PerimetroEnBase | null {
  const fila = sql(
    `SELECT p.nombre, coalesce(p.descripcion, ''), p.zona_estricta, p.distancia_tolerancia_metros, p.activo
     FROM perimetro p JOIN establecimientos e ON e.id = p.establecimiento_id
     WHERE e.nombre = 'Fixture A' AND p.nombre = '${nombre}';`
  );
  if (!fila) return null;
  const [n, descripcion, zona, tolerancia, activo] = fila.split('|');
  return { nombre: n, descripcion, zonaEstricta: zona === 't', tolerancia: Number(tolerancia), activo: activo === 't' };
}

/** Perímetro de Fixture A al norte del perímetro del escenario, sin rodeos asignados. */
function crearPerimetro(nombre: string): void {
  sql(`INSERT INTO perimetro (establecimiento_id, nombre, descripcion, activo, area, distancia_tolerancia_metros,
         zona_estricta, fecha_creacion, version)
       SELECT id, '${nombre}', 'Creado por el test', true,
         ST_MakeEnvelope(longitud - 0.003, latitud + 0.02, longitud + 0.003, latitud + 0.026, 4326), 0, false, now(), 0
       FROM establecimientos WHERE nombre = 'Fixture A';`);
}

/** Campos del formulario de alta y edición (sus etiquetas no están asociadas a los campos). */
function formulario(page: Page) {
  return {
    nombre: page.locator('label', { hasText: 'Nombre del Perímetro' }).locator('..').getByRole('textbox'),
    descripcion: page.getByPlaceholder('Descripción'),
    zonaEstricta: page.getByRole('switch', { name: /Zona estricta/ }),
    tolerancia: page.getByRole('spinbutton'),
  };
}

/** La tarjeta del perímetro en la lista, para sus botones de activar y eliminar. */
function tarjetaPerimetro(page: Page, nombre: string) {
  return page
    .locator('div')
    .filter({ hasText: nombre })
    .filter({ has: page.getByTitle('Eliminar perímetro', { exact: true }) })
    .last();
}

async function abrirPerimetros(page: Page): Promise<void> {
  await page.goto('/mapa');
  await page.getByRole('button', { name: 'Perímetros', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Perímetros', exact: true })).toBeVisible();
  // La lista se carga con el establecimiento ya elegido; antes de eso, "Nuevo" no hace nada.
  await expect(page.getByText('Perímetro A', { exact: true })).toBeVisible();
}

test.describe('H0027 - Alta y edición de perímetros (UI)', () => {
  // El mapa carga perímetros, rodeos y alertas antes de poder crear: con la API recién iniciada supera los 30 s.
  test.describe.configure({ timeout: 60_000 });

  test('H0027-06 - El productor da de alta un perímetro con tolerancia', async ({ page }) => {
    const nombre = `Perímetro E2E ${sufijoUnico()}`;
    await loginPorClave(page, 'productorA');
    await abrirPerimetros(page);

    await page.getByRole('button', { name: /Nuevo/ }).click();
    await formulario(page).nombre.fill(nombre);
    await formulario(page).descripcion.fill('Potrero norte');
    await formulario(page).tolerancia.fill('15');
    await page.getByRole('button', { name: 'Terminado' }).click();

    await expect(page.getByText(nombre)).toBeVisible();
    expect(perimetroEnBase(nombre)).toEqual({
      nombre,
      descripcion: 'Potrero norte',
      zonaEstricta: false,
      tolerancia: 15,
      activo: true,
    });
  });

  test('H0027-07 - Edición: nombre, zona estricta y tolerancia', async ({ page }) => {
    const [original, nuevo] = [`Perímetro E2E ${sufijoUnico()}`, `Perímetro E2E editado ${sufijoUnico()}`];
    crearPerimetro(original);
    await loginPorClave(page, 'productorA');
    await abrirPerimetros(page);

    // Zona estricta: no admite tolerancia, así que el campo se oculta.
    await page.getByText(original).click();
    await formulario(page).nombre.fill(nuevo);
    await expect(formulario(page).tolerancia).toBeVisible();
    await formulario(page).zonaEstricta.check();
    await expect(formulario(page).tolerancia).toHaveCount(0);
    await page.getByRole('button', { name: 'Terminado' }).click();
    await expect(page.getByText(nuevo)).toBeVisible();
    expect(perimetroEnBase(original)).toBeNull();
    expect(perimetroEnBase(nuevo)).toMatchObject({ zonaEstricta: true });

    // De vuelta con tolerancia.
    await page.getByText(nuevo).click();
    await formulario(page).zonaEstricta.uncheck();
    await formulario(page).tolerancia.fill('40');
    await page.getByRole('button', { name: 'Terminado' }).click();
    await expect.poll(() => perimetroEnBase(nuevo)?.tolerancia).toBe(40);
    expect(perimetroEnBase(nuevo)).toMatchObject({ zonaEstricta: false, activo: true });
  });

  test('H0027-08 - Desactivar, activar y eliminar un perímetro', async ({ page }) => {
    const nombre = `Perímetro E2E ${sufijoUnico()}`;
    crearPerimetro(nombre);
    await loginPorClave(page, 'productorA');
    await abrirPerimetros(page);
    await tarjetaPerimetro(page, nombre).getByTitle('Desactivar perímetro', { exact: true }).click();
    await expect.poll(() => perimetroEnBase(nombre)?.activo).toBe(false);
    await tarjetaPerimetro(page, nombre).getByTitle('Activar perímetro', { exact: true }).click();
    await expect.poll(() => perimetroEnBase(nombre)?.activo).toBe(true);

    // Eliminar pide confirmación; cancelar lo deja, confirmar lo borra.
    await tarjetaPerimetro(page, nombre).getByTitle('Eliminar perímetro', { exact: true }).click();
    await expect(page.getByText(/¿Estás seguro de que deseas eliminar el perímetro/)).toContainText(nombre);
    await page.getByRole('button', { name: 'Cancelar' }).click();
    expect(perimetroEnBase(nombre)).not.toBeNull();
    await tarjetaPerimetro(page, nombre).getByTitle('Eliminar perímetro', { exact: true }).click();
    await page.getByRole('button', { name: 'Eliminar', exact: true }).click();
    await expect.poll(() => perimetroEnBase(nombre)).toBeNull();
    await expect(page.getByText(nombre)).toHaveCount(0);
  });

  test('H0027-09 - Validaciones del formulario y cancelar sin guardar', async ({ page }) => {
    const nombre = `Perímetro E2E ${sufijoUnico()}`;
    await loginPorClave(page, 'productorA');
    await abrirPerimetros(page);
    await page.getByRole('button', { name: /Nuevo/ }).click();

    await formulario(page).nombre.fill('   ');
    await page.getByRole('button', { name: 'Terminado' }).click();
    await expect(page.getByText('El nombre del perímetro no puede estar vacío')).toBeVisible();

    await formulario(page).nombre.fill(nombre);
    await formulario(page).tolerancia.fill('-5');
    await page.getByRole('button', { name: 'Terminado' }).click();
    await expect(page.getByText('La distancia de tolerancia debe ser un número mayor o igual a 0')).toBeVisible();
    expect(perimetroEnBase(nombre)).toBeNull();

    await page.getByRole('button', { name: 'Cancelar' }).click();
    await expect(formulario(page).nombre).toHaveCount(0);
    expect(perimetroEnBase(nombre)).toBeNull();
  });
});
