import { test, expect } from '@playwright/test';
import {
  crearEmpleado,
  emailUnico,
  idUsuario,
  loginPorRol,
  logout,
  tokenPorClave,
  type RolUsuario,
} from '../helpers';

/**
 * H0002 - Login, bloqueo por intentos fallidos, cierre y expiración de sesión, y opciones de menú por rol.
 */

async function intentarLogin(page: import('@playwright/test').Page, email: string, password: string) {
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.locator('#btn-login').click();
}

// Pantalla de inicio de cada rol después de iniciar sesión.
const INICIO_POR_ROL: [RolUsuario, RegExp][] = [
  ['ADMINISTRADOR', /\/establecimientos$/],
  ['PRODUCTOR', /\/dashboard$/],
  ['VETERINARIO', /\/dashboard$/],
  ['CAMPERO', /\/dashboard$/],
];

test.describe('H0002 - Login y sesión (UI)', () => {
  for (const [rol, inicio] of INICIO_POR_ROL) {
    test(`H0002-01 - Login correcto - ${rol} llega a su pantalla de inicio`, async ({ page }) => {
      await loginPorRol(page, rol, rol === 'ADMINISTRADOR' ? undefined : { establecimiento: 'Fixture A' });
      await expect(page).toHaveURL(inicio, { timeout: 15000 });
      await expect(page.locator('.btn-salir-navbar')).toBeVisible();
    });
  }

  test('H0002-02 - Credenciales inválidas y cuenta inactiva', async ({ page, request }) => {
    await intentarLogin(page, emailUnico('inexistente'), 'OtraClave123');
    await expect(page.getByRole('alert')).toContainText(/Credenciales incorrectas/i);
    await expect(page).toHaveURL(/\/login/);

    // Usuario desactivado por el administrador.
    const usuario = await crearEmpleado(request, 'productorA', 'Fixture A', 'CAMPERO');
    const admin = await tokenPorClave(request, 'admin');
    const id = await idUsuario(request, usuario.email);
    expect((await request.patch(`/api/v1/usuarios/${id}/estado`, { headers: { Authorization: `Bearer ${admin}` } })).status()).toBe(200);

    await intentarLogin(page, usuario.email, usuario.password);
    await expect(page.getByRole('alert')).toContainText(/La cuenta no está activa/i);
    await expect(page).toHaveURL(/\/login/);
  });

  test('H0002-03 - Mensaje de cuenta bloqueada tras 5 intentos fallidos', async ({ page }) => {
    // Email propio del test: el bloqueo es por email y no afecta a los usuarios del escenario.
    const email = emailUnico('bloqueo');
    for (let intento = 1; intento <= 5; intento++) {
      await intentarLogin(page, email, `Incorrecta${intento}`);
      await expect(page.getByRole('alert')).toContainText(/Credenciales incorrectas/i);
    }

    await intentarLogin(page, email, 'Incorrecta6');
    await expect(page.getByRole('alert')).toContainText(/bloqueada por múltiples intentos fallidos/i);
  });

  test('H0002-04 - Cierre de sesión', async ({ page }) => {
    await loginPorRol(page, 'PRODUCTOR', { establecimiento: 'Fixture A' });
    await expect(page).toHaveURL(/\/dashboard/);

    await logout(page);
    await expect(page).toHaveURL(/\/login/);

    // Sin sesión, las rutas privadas vuelven al login.
    await page.goto('/alertas');
    await expect(page).toHaveURL(/\/login/);
  });

  test('H0002-05 - Sesión expirada redirige a Login', async ({ page }) => {
    await loginPorRol(page, 'VETERINARIO', { establecimiento: 'Fixture A' });
    await expect(page).toHaveURL(/\/dashboard/);

    // A partir de acá la API recibe un JWT vencido, que es lo que llega al backend cuando la sesión expira. (Cambiar
    // el token guardado en el navegador no es confiable: la app lo vuelve a escribir desde memoria.)
    const base64 = (obj: object) => Buffer.from(JSON.stringify(obj)).toString('base64url');
    const ahora = Math.floor(Date.now() / 1000);
    const vencido = `${base64({ alg: 'HS256' })}.${base64({ sub: 'x', iat: ahora - 7200, exp: ahora - 3600 })}.firma`;
    await page.route('**/api/v1/**', (route) =>
      route.continue({ headers: { ...route.request().headers(), authorization: `Bearer ${vencido}` } })
    );

    // El próximo pedido de la app recibe 401: tiene que cerrar la sesión y volver al login. Si la app ya redirigió
    // (algún pedido en curso recibió el 401), esta navegación se aborta, y eso también es el resultado esperado.
    await page.goto('/alertas').catch(() => undefined);
    await expect(page).toHaveURL(/\/login/, { timeout: 15000 });
    await expect(page.locator('#email')).toBeVisible();
  });

  test('H0002-06 - Sesión cerrada por 60 minutos de inactividad', async ({ page }) => {
    await page.clock.install();
    await loginPorRol(page, 'CAMPERO', { establecimiento: 'Fixture A' });
    await expect(page).toHaveURL(/\/dashboard/);

    // Con el dashboard ya montado, el timer de inactividad está corriendo.
    await expect(page.locator('.dashboard-page')).toBeVisible();
    await page.clock.fastForward('01:01:00');

    await expect(page).toHaveURL(/\/login/, { timeout: 15000 });
  });
});

/** Opciones del navbar (escritorio) que corresponden a cada rol; el resto no tiene que aparecer. */
const MENU = ['Establecimientos', 'Caravanas', 'Usuarios', 'Empleados', 'Animales', 'Diagnósticos', 'Mapa', 'Alertas', 'Invitaciones'];
const MENU_POR_ROL: [RolUsuario, string[], string][] = [
  ['ADMINISTRADOR', ['Establecimientos', 'Caravanas', 'Usuarios', 'Invitaciones'], '/animales'],
  ['PRODUCTOR', ['Establecimientos', 'Empleados', 'Animales', 'Mapa', 'Alertas', 'Invitaciones'], '/usuarios'],
  ['VETERINARIO', ['Animales', 'Diagnósticos', 'Mapa', 'Alertas', 'Invitaciones'], '/empleados'],
  ['CAMPERO', ['Animales', 'Mapa', 'Alertas', 'Invitaciones'], '/diagnosticos'],
];

test.describe('H0002 - Opciones de menú por rol (UI)', () => {
  for (const [rol, visibles, rutaAjena] of MENU_POR_ROL) {
    test(`H0002-07 - Menú por rol - ${rol} ve solo sus opciones`, async ({ page }) => {
      await loginPorRol(page, rol, rol === 'ADMINISTRADOR' ? undefined : { establecimiento: 'Fixture A' });
      const navbar = page.locator('nav.navbar');

      for (const opcion of MENU) {
        const enlace = navbar.getByRole('link', { name: opcion, exact: true });
        if (visibles.includes(opcion)) {
          await expect(enlace, `${rol} debería ver ${opcion}`).toBeVisible();
        } else {
          await expect(enlace, `${rol} no debería ver ${opcion}`).toHaveCount(0);
        }
      }

      // Escribir a mano la ruta de otro rol tampoco la muestra.
      await page.goto(rutaAjena);
      await expect(page).not.toHaveURL(new RegExp(`${rutaAjena}$`));
    });
  }
});
