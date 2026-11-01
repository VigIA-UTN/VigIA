import { test, expect } from '@playwright/test';
import { obtenerEstablecimientoSeleccionado, obtenerUsuarioPorClave, tokenPorClave } from '../helpers';

/**
 * H0014 - Página de callback OAuth2 (/oauth2/callback) con un token simulado: es la página a la que el backend
 * redirige después del login con Google. El token es un JWT real obtenido por la API de login, así la app puede
 * cargar datos después del callback. El login real con Google no se automatiza: se valida a mano y se deja evidencia.
 */
test.describe('H0014 - Callback OAuth2 (UI)', () => {
  test('H0014-01 - Callback con token de un usuario de establecimiento', async ({ page, request }) => {
    const usuario = obtenerUsuarioPorClave('productorA');
    const token = await tokenPorClave(request, 'productorA');

    await page.goto(`/oauth2/callback?token=${token}&email=${encodeURIComponent(usuario.email)}&rol=PRODUCTOR`);

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15000 });
    await expect.poll(() => obtenerEstablecimientoSeleccionado(page)).toBe('Fixture A');
  });

  test('H0014-02 - Callback con token del administrador', async ({ page, request }) => {
    const usuario = obtenerUsuarioPorClave('admin');
    const token = await tokenPorClave(request, 'admin');

    await page.goto(`/oauth2/callback?token=${token}&email=${encodeURIComponent(usuario.email)}&rol=ADMINISTRADOR`);

    await expect(page).toHaveURL(/\/usuarios/, { timeout: 15000 });
    await expect(page.getByRole('heading', { name: 'Usuarios' })).toBeVisible();
  });

  test('H0014-03 - Callback con error del backend (usuario sin invitación)', async ({ page }) => {
    const error = 'No se puede registrar mediante SSO sin una invitación previa.';

    await page.goto(`/oauth2/callback?error=${encodeURIComponent(error)}`);

    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('alert')).toContainText(error);
  });

  test('H0014-04 - Callback sin token', async ({ page }) => {
    await page.goto('/oauth2/callback');

    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('alert')).toContainText(/problema al iniciar sesión con Google/i);
  });
});
