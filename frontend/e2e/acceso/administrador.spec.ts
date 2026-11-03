import { test, expect, type Page } from '@playwright/test';
import {
  crearEmpleado,
  emailUnico,
  idEstablecimiento,
  login,
  loginPorRol,
  logout,
  obtenerUsuarioPorClave,
  tokenPorClave,
} from '../helpers';

/**
 * Flujos del administrador: alta y edición de establecimiento (H0003), alta de productor por invitación (H0032) y
 * activación/desactivación de usuarios (H0036). Lo que se crea lleva nombres y emails únicos del dominio del
 * escenario, así limpiar.sql lo borra y el test se puede repetir.
 */

async function intentarLogin(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.locator('#btn-login').click();
}

test.describe('H0003 - Alta y edición de establecimiento (UI)', () => {
  test('H0003-01 - El administrador da de alta un establecimiento', async ({ page }) => {
    const nombre = `Fixture E2E ${Date.now()}`;
    await loginPorRol(page, 'ADMINISTRADOR');
    await expect(page).toHaveURL(/\/establecimientos/);

    await page.getByRole('button', { name: /Nuevo/i }).click();
    await page.getByLabel('Nombre').fill(nombre);
    await page.getByLabel('Latitud').fill('-36.7');
    await page.getByLabel('Longitud').fill('-61.7');
    await page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click();

    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.locator('.est-card-name', { hasText: nombre })).toBeVisible();
  });

  test('H0003-02 - Alta de establecimiento con datos faltantes', async ({ page }) => {
    await loginPorRol(page, 'ADMINISTRADOR');
    await page.getByRole('button', { name: /Nuevo/i }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click();

    // El modal queda abierto y marca los campos obligatorios.
    await expect(page.getByText('Crear establecimiento')).toBeVisible();
    await expect(page.getByLabel('Nombre')).toHaveClass(/is-invalid/);
    await expect(page.getByLabel('Latitud')).toHaveClass(/is-invalid/);
    await expect(page.getByLabel('Longitud')).toHaveClass(/is-invalid/);
    await expect(page.getByText('El nombre es obligatorio')).toHaveCount(1);
  });

  test('H0003-05 - El administrador edita el nombre y la ubicación de un establecimiento', async ({ page, request }) => {
    // Se edita uno creado por el test: Fixture A y B los usan el resto de los specs y los otros navegadores.
    const nombre = `Fixture E2E ${Date.now()}`;
    const editado = `${nombre} editado`;
    const token = await tokenPorClave(request, 'admin');
    const alta = await request.post('/api/v1/establecimientos', {
      headers: { Authorization: `Bearer ${token}` },
      data: { nombre, latitud: -36.7, longitud: -61.7 },
    });
    expect(alta.status()).toBe(201);

    await loginPorRol(page, 'ADMINISTRADOR');
    await page.getByPlaceholder('Buscar por nombre o #id...').fill(nombre);
    await page.locator('.establecimiento-card', { hasText: nombre }).getByTitle('Más acciones').click();
    await page.locator('.est-menu').getByText('Configuración').click();
    await expect(page).toHaveURL(/\/establecimientos\/\d+\/configuracion/);
    await expect(page.locator('#nombre')).toHaveValue(nombre);

    await page.locator('#nombre').fill(editado);
    await page.locator('#latitud').fill('-35.25');
    await page.locator('#longitud').fill('-60.75');
    await page.getByRole('button', { name: 'Guardar Cambios' }).click();
    await expect(page.locator('.cfg-toast-visible')).toContainText('Cambios guardados correctamente');

    // Al volver a entrar los datos son los guardados.
    await page.reload();
    await expect(page.locator('#nombre')).toHaveValue(editado);
    await expect(page.locator('#latitud')).toHaveValue('-35.25');
    await expect(page.locator('#longitud')).toHaveValue('-60.75');
    await page.goto('/establecimientos');
    await expect(page.locator('.est-card-name', { hasText: editado })).toBeVisible();
  });

  test('H0003-07 - El veterinario ve los datos del establecimiento sin poder editarlos', async ({ page, request }) => {
    const id = await idEstablecimiento(request, await tokenPorClave(request, 'veterinarioA'), 'Fixture A');
    await loginPorRol(page, 'VETERINARIO', { establecimiento: 'Fixture A' });
    await page.goto(`/establecimientos/${id}/configuracion`);

    await expect(page.locator('#nombre')).toHaveValue('Fixture A');
    for (const campo of ['#nombre', '#latitud', '#longitud']) {
      await expect(page.locator(campo)).toHaveAttribute('readonly', '');
    }
    await expect(page.getByText('Solo el productor puede editar estos datos')).toHaveCount(2);
  });
});

test.describe('H0032 - Alta de productor (UI)', () => {
  test('H0032-01 - El administrador invita a un productor nuevo', async ({ page }) => {
    const email = emailUnico('productor');
    await loginPorRol(page, 'ADMINISTRADOR');
    await page.getByRole('link', { name: 'Invitaciones', exact: true }).click();

    await page.getByRole('button', { name: /Nuevo/i }).click();
    await page.getByLabel('Email del Productor').fill(email);
    await page.getByLabel('Nombre de Establecimiento').selectOption({ label: 'Fixture A' });
    await page.getByRole('button', { name: /^Invitar$/ }).click();

    await expect(page.getByRole('alert').filter({ hasText: email })).toContainText(/Invitación enviada correctamente/i);
    await page.getByPlaceholder('Buscar por email...').fill(email);
    await expect(page.getByRole('link', { name: email })).toBeVisible();
  });

  test('H0032-02 - Invitar a un productor que ya pertenece al establecimiento', async ({ page }) => {
    await loginPorRol(page, 'ADMINISTRADOR');
    await page.goto('/invitaciones');

    await page.getByRole('button', { name: /Nuevo/i }).click();
    await page.getByLabel('Email del Productor').fill(obtenerUsuarioPorClave('productorA').email);
    await page.getByLabel('Nombre de Establecimiento').selectOption({ label: 'Fixture A' });
    await page.getByRole('button', { name: /^Invitar$/ }).click();

    await expect(page.getByRole('dialog').getByRole('alert')).toContainText(/ya pertenece/i);
  });
});

test.describe('H0036 - Activación y desactivación de usuarios (UI)', () => {
  test('H0036-01 - El administrador desactiva y reactiva un usuario', async ({ page, request }) => {
    // Flujo largo (tres inicios de sesión): más tiempo que el default de 30 s.
    test.setTimeout(90_000);
    const usuario = await crearEmpleado(request, 'productorA', 'Fixture A', 'CAMPERO');
    await loginPorRol(page, 'ADMINISTRADOR');
    await page.getByRole('link', { name: 'Usuarios', exact: true }).click();

    await page.getByPlaceholder('Buscar por nombre o email...').fill(usuario.email);
    const fila = page.locator('tr', { hasText: usuario.email });
    await fila.getByRole('button', { name: 'Desactivar' }).click();
    await expect(fila.locator('.estado-badge')).toHaveText('INACTIVO');

    // El usuario desactivado no puede entrar.
    await logout(page);
    await intentarLogin(page, usuario.email, usuario.password);
    await expect(page.getByRole('alert')).toContainText(/La cuenta no está activa/i);

    // Reactivado, vuelve a entrar.
    await loginPorRol(page, 'ADMINISTRADOR');
    await page.goto('/usuarios');
    await page.getByPlaceholder('Buscar por nombre o email...').fill(usuario.email);
    await fila.getByRole('button', { name: 'Activar' }).click();
    await expect(fila.locator('.estado-badge')).toHaveText('ACTIVO');
    await logout(page);
    await login(page, usuario.email, usuario.password);
    await expect(page).toHaveURL(/\/dashboard/);
  });
});
