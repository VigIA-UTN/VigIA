import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import {
  crearEmpleado,
  invitacionesPendientes,
  invitarEmpleado,
  login,
  obtenerEstablecimientosDisponibles,
  type UsuarioCreado,
} from '../helpers';

/**
 * H0034 - Aceptar y rechazar invitaciones pendientes a otro establecimiento, con y sin sesión iniciada.
 *
 * Un usuario que ya tiene cuenta responde sus invitaciones desde "Mis invitaciones": el mail que recibe lo lleva a
 * /invitaciones (el link /aceptar-invitacion es solo para quien todavía no tiene cuenta, ver H0001). Cada test usa un
 * veterinario nuevo de Fixture A al que productorB invita a Fixture B, así no depende de los otros tests.
 */

async function veterinarioInvitadoAB(request: APIRequestContext) {
  const usuario = await crearEmpleado(request, 'productorA', 'Fixture A', 'VETERINARIO');
  const invitacion = await invitarEmpleado(request, 'productorB', 'Fixture B', 'VETERINARIO', usuario.email);
  return { usuario, invitacion };
}

/** Sin sesión: abre el link del mail (/invitaciones), inicia sesión y entra a Mis invitaciones. */
async function abrirLinkDelMailSinSesion(page: Page, usuario: UsuarioCreado) {
  await page.goto('/invitaciones');
  await expect(page).toHaveURL(/\/login/);
  await login(page, usuario.email, usuario.password);
  await page.getByRole('link', { name: 'Invitaciones', exact: true }).click();
}

async function aceptar(page: Page) {
  const tarjeta = page.locator('.invitacion-card', { hasText: 'Fixture B' });
  await tarjeta.getByRole('button', { name: /Aceptar Invitación/i }).click();
  await expect(tarjeta).toHaveCount(0);
}

async function rechazar(page: Page) {
  const tarjeta = page.locator('.invitacion-card', { hasText: 'Fixture B' });
  await tarjeta.getByRole('button', { name: /^Rechazar$/ }).click();
  await page.getByRole('button', { name: /Sí, rechazar/i }).click();
  await expect(tarjeta).toHaveCount(0);
}

test.describe('H0034 - Invitaciones pendientes (UI)', () => {
  // La preparación por API (crear al usuario e invitarlo) suma varios logins: más margen que los 30 s por defecto.
  test.describe.configure({ timeout: 60_000 });

  test('H0034-01 - Con sesión - Aceptar desde Mis invitaciones', async ({ page, request }) => {
    const { usuario, invitacion } = await veterinarioInvitadoAB(request);
    await login(page, usuario.email, usuario.password);

    await page.getByRole('link', { name: 'Invitaciones', exact: true }).click();
    await aceptar(page);

    expect(await invitacionesPendientes(request, usuario)).not.toContain(invitacion.id);
    await expect.poll(() => obtenerEstablecimientosDisponibles(page)).toContain('Fixture B');
  });

  test('H0034-02 - Con sesión - Rechazar desde Mis invitaciones', async ({ page, request }) => {
    const { usuario, invitacion } = await veterinarioInvitadoAB(request);
    await login(page, usuario.email, usuario.password);

    await page.getByRole('link', { name: 'Invitaciones', exact: true }).click();
    await rechazar(page);

    expect(await invitacionesPendientes(request, usuario)).not.toContain(invitacion.id);
    expect(await obtenerEstablecimientosDisponibles(page)).not.toContain('Fixture B');
  });

  test('H0034-03 - Sin sesión - Aceptar desde el link del mail', async ({ page, request }) => {
    const { usuario, invitacion } = await veterinarioInvitadoAB(request);

    await abrirLinkDelMailSinSesion(page, usuario);
    await aceptar(page);

    expect(await invitacionesPendientes(request, usuario)).not.toContain(invitacion.id);
    await expect.poll(() => obtenerEstablecimientosDisponibles(page)).toContain('Fixture B');
  });

  test('H0034-04 - Sin sesión - Rechazar desde el link del mail', async ({ page, request }) => {
    const { usuario, invitacion } = await veterinarioInvitadoAB(request);

    await abrirLinkDelMailSinSesion(page, usuario);
    await rechazar(page);

    expect(await invitacionesPendientes(request, usuario)).not.toContain(invitacion.id);
    expect(await obtenerEstablecimientosDisponibles(page)).not.toContain('Fixture B');
  });
});
