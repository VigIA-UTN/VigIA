import { test, expect } from '@playwright/test';
import { crearEmpleado, login, logout } from '../helpers';

/**
 * H0037 - Mi perfil: edición de datos y cambio de contraseña. Cada test usa un usuario nuevo para no cambiar los
 * datos ni la contraseña de los usuarios del escenario.
 */
test.describe('H0037 - Mi perfil (UI)', () => {
  test.describe.configure({ timeout: 60_000 });

  test('H0037-01 - Editar nombre y apellido', async ({ page, request }) => {
    const usuario = await crearEmpleado(request, 'productorA', 'Fixture A', 'VETERINARIO');
    await login(page, usuario.email, usuario.password);
    await page.getByRole('link', { name: /Mi Perfil/i }).click();
    // En desarrollo React (StrictMode) pide el perfil dos veces: se espera a que terminen ambos pedidos para que la
    // segunda respuesta no pise lo que escribe el test.
    await page.waitForLoadState('networkidle');

    await expect(page.getByLabel('Nombre')).toHaveValue('Usuario');
    await page.getByLabel('Nombre').fill('Valeria');
    await page.getByLabel('Apellido').fill('Gómez');
    await page.getByRole('button', { name: /Guardar Cambios/i }).click();
    await expect(page.getByText('Perfil actualizado correctamente.')).toBeVisible();

    await page.reload();
    await expect(page.getByLabel('Nombre')).toHaveValue('Valeria');
    await expect(page.getByLabel('Apellido')).toHaveValue('Gómez');
  });

  test('H0037-02 - Cambiar la contraseña y entrar con la nueva', async ({ page, request }) => {
    const usuario = await crearEmpleado(request, 'productorA', 'Fixture A', 'CAMPERO');
    const nueva = 'NuevaClave456';
    await login(page, usuario.email, usuario.password);
    await page.goto('/perfil');
    await page.waitForLoadState('networkidle');

    await page.getByLabel('Contraseña Actual').fill(usuario.password);
    await page.getByLabel('Nueva Contraseña', { exact: true }).fill(nueva);
    await page.getByLabel('Confirmar Nueva Contraseña').fill(nueva);
    await page.getByRole('button', { name: /Actualizar Contraseña/i }).click();
    await expect(page.getByText('Contraseña cambiada exitosamente.')).toBeVisible();

    await logout(page);
    await login(page, usuario.email, nueva);
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('H0037-03 - Cambio de contraseña con datos incorrectos', async ({ page, request }) => {
    const usuario = await crearEmpleado(request, 'productorA', 'Fixture A', 'CAMPERO');
    await login(page, usuario.email, usuario.password);
    await page.goto('/perfil');
    await page.waitForLoadState('networkidle');

    // La confirmación no coincide.
    await page.getByLabel('Contraseña Actual').fill(usuario.password);
    await page.getByLabel('Nueva Contraseña', { exact: true }).fill('NuevaClave456');
    await page.getByLabel('Confirmar Nueva Contraseña').fill('OtraClave789');
    await page.getByRole('button', { name: /Actualizar Contraseña/i }).click();
    await expect(page.getByText('Las contraseñas no coinciden.')).toBeVisible();

    // La contraseña actual es incorrecta.
    await page.getByLabel('Contraseña Actual').fill('NoEsLaActual1');
    await page.getByLabel('Confirmar Nueva Contraseña').fill('NuevaClave456');
    await page.getByRole('button', { name: /Actualizar Contraseña/i }).click();
    await expect(page.locator('.alert-danger')).toBeVisible();

    // La contraseña no cambió.
    await logout(page);
    await login(page, usuario.email, usuario.password);
    await expect(page).toHaveURL(/\/dashboard/);
  });
});
