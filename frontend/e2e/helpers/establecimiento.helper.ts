import { type Page } from '@playwright/test';

/**
 * Helper para manipular el selector de establecimiento en la barra de navegación de VigIA.
 * Soporta tanto la vista de escritorio como la vista móvil (abriendo el drawer si fuera necesario).
 */

async function resolverSelectorEstablecimiento(page: Page) {
  const selectorDesktop = page.locator('select#navbar-establecimiento-selector:visible');

  if (await selectorDesktop.isVisible()) {
    return selectorDesktop;
  }

  // Si no está visible en desktop, puede estar en mobile dentro del drawer
  const hamburger = page.locator('button[aria-label="Abrir menú"]:visible');
  if (await hamburger.isVisible()) {
    await hamburger.click();
    const selectorDrawer = page.locator('.mobile-drawer select#navbar-establecimiento-selector:visible');
    await selectorDrawer.waitFor({ state: 'visible', timeout: 5000 });
    return selectorDrawer;
  }

  // Esperar a que el selector esté presente y visible
  await selectorDesktop.waitFor({ state: 'visible', timeout: 10000 });
  return selectorDesktop;
}

/**
 * Selecciona un establecimiento por su nombre o ID en el selector de la navbar.
 *
 * @example
 * await seleccionarEstablecimiento(page, 'Fixture A');
 * await seleccionarEstablecimiento(page, 'Fixture B');
 */
export async function seleccionarEstablecimiento(
  page: Page,
  nombreOId: string | number
): Promise<void> {
  const selector = await resolverSelectorEstablecimiento(page);

  if (typeof nombreOId === 'number') {
    await selector.selectOption(String(nombreOId));
  } else {
    await selector.selectOption({ label: nombreOId });
  }

  // Da un pequeño margen para que el estado global (Zustand) y las queries se actualicen
  await page.waitForTimeout(300);
}

/**
 * Obtiene el nombre del establecimiento actualmente seleccionado en la navbar.
 */
export async function obtenerEstablecimientoSeleccionado(page: Page): Promise<string> {
  const selector = await resolverSelectorEstablecimiento(page);
  return await selector.evaluate((el: HTMLSelectElement) => {
    const opt = el.options[el.selectedIndex];
    return opt ? opt.text.trim() : '';
  });
}

/**
 * Obtiene la lista de nombres de todos los establecimientos disponibles en el selector.
 */
export async function obtenerEstablecimientosDisponibles(page: Page): Promise<string[]> {
  const selector = await resolverSelectorEstablecimiento(page);
  return await selector.evaluate((el: HTMLSelectElement) =>
    Array.from(el.options).map((opt) => opt.text.trim())
  );
}
