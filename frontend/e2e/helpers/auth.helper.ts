import { expect, type Page } from '@playwright/test';
import {
  obtenerCredenciales,
  obtenerUsuarioPorClave,
  obtenerUsuarioPorRol,
  type RolUsuario,
  type UsuarioFixture,
} from '../fixtures/credenciales';

/**
 * Realiza el inicio de sesión a través de la UI de login de VigIA.
 */
export async function login(page: Page, email: string, password?: string): Promise<void> {
  const creds = obtenerCredenciales();
  const pwd = password ?? creds.password;

  await page.goto('/login');
  await expect(page.locator('#email')).toBeVisible({ timeout: 10000 });

  await page.locator('#email').fill(email);
  await page.locator('#password').fill(pwd);
  await page.locator('#btn-login').click();

  // Espera a que termine la redirección posterior al login
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 });
}

/**
 * Inicia sesión usando las credenciales del rol indicado obtenidas de fixtures/escenario/credenciales.json.
 * Opcionalmente se puede filtrar por el establecimiento asociado al usuario.
 */
export async function loginPorRol(
  page: Page,
  rol: RolUsuario | string,
  options?: { establecimiento?: string }
): Promise<UsuarioFixture> {
  const usuario = obtenerUsuarioPorRol(rol, options);
  const creds = obtenerCredenciales();
  await login(page, usuario.email, creds.password);
  return usuario;
}

/**
 * Inicia sesión usando la clave de usuario en credenciales.json (ej. 'productorA', 'admin', 'productorAmbos').
 */
export async function loginPorClave(page: Page, clave: string): Promise<UsuarioFixture> {
  const usuario = obtenerUsuarioPorClave(clave);
  const creds = obtenerCredenciales();
  await login(page, usuario.email, creds.password);
  return usuario;
}

/**
 * Cierra la sesión activa a través de la UI.
 */
export async function logout(page: Page): Promise<void> {
  // En desktop: dropdown de usuario en navbar
  const userDropdown = page.locator('#navbar-user-dropdown');
  const hamburger = page.locator('button[aria-label="Abrir menú"]:visible');

  if (await hamburger.isVisible()) {
    await hamburger.click();
    await page.getByRole('button', { name: /Cerrar sesión/i }).click();
  } else if (await userDropdown.isVisible()) {
    await userDropdown.click();
    await page.getByRole('button', { name: /Cerrar sesión/i }).click();
  }

  await page.waitForURL('**/login', { timeout: 10000 });
}
