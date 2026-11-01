import { test, expect, type Page } from '@playwright/test';
import { PASSWORD_E2E, invitarEmpleado, invitarProductor, login, logout } from '../helpers';

/**
 * H0001 - Registro desde el link de invitación (el mismo que llega por mail: /aceptar-invitacion?token=...) hasta el
 * primer login. Cada test crea su propia invitación con un email único, así se puede correr en todos los navegadores.
 */

async function completarRegistro(page: Page, nombre: string, apellido: string, password: string) {
  await page.getByPlaceholder('Tu nombre').fill(nombre);
  await page.getByPlaceholder('Tu apellido').fill(apellido);
  await page.getByPlaceholder('Tu contraseña').fill(password);
  await page.getByRole('button', { name: /Registrarme y Aceptar/i }).click();
}

test.describe('H0001 - Registro por invitación (UI)', () => {
  test('H0001-01 - Registro de productor desde el link de invitación hasta el primer login', async ({ page, request }) => {
    const invitacion = await invitarProductor(request, 'Fixture A');

    await page.goto(`/aceptar-invitacion?token=${invitacion.token}`);
    await expect(page.locator('.establecimiento-name')).toHaveText('Fixture A');
    await expect(page.locator('input.disabled-input').first()).toHaveValue(invitacion.email);
    await completarRegistro(page, 'Paula', 'Productora', PASSWORD_E2E);

    // El registro deja la sesión iniciada y entra al dashboard del establecimiento.
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15000 });

    // Primer login con la contraseña elegida.
    await logout(page);
    await login(page, invitacion.email, PASSWORD_E2E);
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('H0001-02 - Registro de empleado desde el link de invitación hasta el primer login', async ({ page, request }) => {
    const invitacion = await invitarEmpleado(request, 'productorA', 'Fixture A', 'CAMPERO');

    await page.goto(`/aceptar-invitacion?token=${invitacion.token}`);
    await expect(page.locator('.establecimiento-name')).toHaveText('Fixture A');
    await expect(page.locator('input.disabled-input').nth(1)).toHaveValue('Campero');
    await completarRegistro(page, 'Carlos', 'Campero', PASSWORD_E2E);

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15000 });
    await logout(page);
    await login(page, invitacion.email, PASSWORD_E2E);
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('H0001-03 - Link de invitación ya usado o inexistente', async ({ page, request }) => {
    const invitacion = await invitarEmpleado(request, 'productorA', 'Fixture A', 'VETERINARIO');
    const registro = await request.post('/api/v1/auth/register', {
      data: { nombre: 'Ya', apellido: 'Usado', password: PASSWORD_E2E, token: invitacion.token },
    });
    expect(registro.status()).toBe(201);

    await page.goto(`/aceptar-invitacion?token=${invitacion.token}`);
    await expect(page.getByRole('heading', { name: /Algo salió mal/i })).toBeVisible();
    await expect(page.locator('.error-message')).toContainText(/utilizada/i);

    await page.goto('/aceptar-invitacion?token=token-que-no-existe');
    await expect(page.locator('.error-message')).toContainText(/inválido/i);
    await page.getByRole('button', { name: /Ir al Login/i }).click();
    await expect(page).toHaveURL(/\/login/);
  });

  test('H0001-04 - Contraseña que no cumple las reglas', async ({ page, request }) => {
    const invitacion = await invitarEmpleado(request, 'productorA', 'Fixture A', 'CAMPERO');

    await page.goto(`/aceptar-invitacion?token=${invitacion.token}`);
    await completarRegistro(page, 'Débil', 'Clave', 'corta');

    await expect(page.getByText(/al menos 8 caracteres/i)).toBeVisible();
    await expect(page).toHaveURL(/\/aceptar-invitacion/);
    // La invitación sigue disponible: se puede completar con una contraseña válida.
    await page.getByPlaceholder('Tu contraseña').fill(PASSWORD_E2E);
    await page.getByRole('button', { name: /Registrarme y Aceptar/i }).click();
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15000 });
  });
});
