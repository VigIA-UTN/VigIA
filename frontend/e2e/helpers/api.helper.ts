import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { obtenerCredenciales, obtenerUsuarioPorClave } from '../fixtures/credenciales';

/**
 * Preparación de datos por API para los flujos de acceso.
 *
 * Los specs corren en varios navegadores contra el mismo escenario, así que todo lo que modifica datos (registrar,
 * aceptar una invitación, desactivar o cambiar la contraseña) trabaja sobre usuarios e invitaciones nuevas con emails
 * únicos de @fixture.vigia.test: el limpiar.sql del escenario los borra en la próxima carga y los usuarios del
 * escenario quedan intactos para el resto de los tests.
 *
 * Las URLs son relativas: pasan por el proxy de Vite (/api → backend), igual que la app.
 */

/** Contraseña de los usuarios creados por los tests: cumple las reglas del registro. */
export const PASSWORD_E2E = 'ClaveE2e123';

export interface UsuarioCreado {
  email: string;
  password: string;
  rol: string;
}

export interface InvitacionCreada {
  id: number;
  token: string;
  email: string;
}

/** Email único dentro del dominio que limpia el escenario. */
export function emailUnico(prefijo = 'e2e'): string {
  return `${prefijo}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@fixture.vigia.test`;
}

/** JWT de un usuario por la API de login. */
export async function tokenApi(request: APIRequestContext, email: string, password: string): Promise<string> {
  const respuesta = await request.post('/api/v1/auth/login', { data: { email, password } });
  expect(respuesta.status(), `login por API de ${email}`).toBe(200);
  return (await respuesta.json()).token as string;
}

/** JWT de un usuario del escenario por su clave en credenciales.json. */
export async function tokenPorClave(request: APIRequestContext, clave: string): Promise<string> {
  return tokenApi(request, obtenerUsuarioPorClave(clave).email, obtenerCredenciales().password);
}

/** Id de un establecimiento visible para el usuario del token. */
export async function idEstablecimiento(request: APIRequestContext, token: string, nombre: string): Promise<number> {
  const respuesta = await request.get('/api/v1/establecimientos', { headers: { Authorization: `Bearer ${token}` } });
  expect(respuesta.status()).toBe(200);
  const lista = (await respuesta.json()) as { id: number; nombre: string }[];
  const encontrado = lista.find((e) => e.nombre === nombre);
  if (!encontrado) throw new Error(`El usuario no ve el establecimiento ${nombre}`);
  return encontrado.id;
}

/** Invitación de empleado creada por un productor del escenario (por ejemplo productorA a Fixture A). */
export async function invitarEmpleado(
  request: APIRequestContext,
  claveProductor: string,
  establecimiento: string,
  rol: 'VETERINARIO' | 'CAMPERO',
  email = emailUnico(rol.toLowerCase())
): Promise<InvitacionCreada> {
  const token = await tokenPorClave(request, claveProductor);
  const establecimientoId = await idEstablecimiento(request, token, establecimiento);
  const respuesta = await request.post('/api/v1/invitaciones/empleado', {
    headers: { Authorization: `Bearer ${token}` },
    data: { email, establecimientoId, rol },
  });
  expect(respuesta.status(), await respuesta.text()).toBe(201);
  const invitacion = await respuesta.json();
  return { id: invitacion.id, token: invitacion.token, email };
}

/** Invitación de productor creada por el administrador. */
export async function invitarProductor(
  request: APIRequestContext,
  establecimiento: string,
  email = emailUnico('productor')
): Promise<InvitacionCreada> {
  const token = await tokenPorClave(request, 'admin');
  const establecimientoId = await idEstablecimiento(request, token, establecimiento);
  const respuesta = await request.post('/api/v1/invitaciones/productor', {
    headers: { Authorization: `Bearer ${token}` },
    data: { email, establecimientoId },
  });
  expect(respuesta.status(), await respuesta.text()).toBe(201);
  const invitacion = await respuesta.json();
  return { id: invitacion.id, token: invitacion.token, email };
}

/**
 * Empleado nuevo de un establecimiento, ya registrado: el productor lo invita y se registra con el token. Sirve
 * para los tests que necesitan un usuario existente que después se modifica.
 */
export async function crearEmpleado(
  request: APIRequestContext,
  claveProductor: string,
  establecimiento: string,
  rol: 'VETERINARIO' | 'CAMPERO'
): Promise<UsuarioCreado> {
  const invitacion = await invitarEmpleado(request, claveProductor, establecimiento, rol);
  const respuesta = await request.post('/api/v1/auth/register', {
    data: { nombre: 'Usuario', apellido: 'E2E', password: PASSWORD_E2E, token: invitacion.token },
  });
  expect(respuesta.status(), await respuesta.text()).toBe(201);
  return { email: invitacion.email, password: PASSWORD_E2E, rol };
}

/** Id de un usuario (no administrador) en el listado del administrador. */
export async function idUsuario(request: APIRequestContext, email: string): Promise<number> {
  const token = await tokenPorClave(request, 'admin');
  const respuesta = await request.get('/api/v1/usuarios', { headers: { Authorization: `Bearer ${token}` } });
  expect(respuesta.status()).toBe(200);
  const usuario = ((await respuesta.json()) as { id: number; email: string }[]).find((u) => u.email === email);
  if (!usuario) throw new Error(`No existe el usuario ${email}`);
  return usuario.id;
}

/** Estado de una invitación vista por su destinatario. */
export async function invitacionesPendientes(request: APIRequestContext, usuario: UsuarioCreado): Promise<number[]> {
  const token = await tokenApi(request, usuario.email, usuario.password);
  const respuesta = await request.get('/api/v1/invitaciones/mis-invitaciones', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(respuesta.status()).toBe(200);
  return ((await respuesta.json()) as { id: number; estado: string }[])
    .filter((i) => i.estado === 'PENDIENTE')
    .map((i) => i.id);
}

/**
 * Deja una sesión iniciada en el navegador sin pasar por el formulario: escribe el estado que persiste el store de
 * autenticación (zustand, clave vigia-auth). Hay que llamarlo después de abrir alguna página del sitio.
 */
export async function establecerSesion(page: Page, token: string, email: string, rol: string): Promise<void> {
  await page.evaluate(
    ({ token, email, rol }) => {
      localStorage.setItem(
        'vigia-auth',
        JSON.stringify({ state: { token, email, rol, isAuthenticated: true, lastActivity: Date.now() }, version: 0 })
      );
    },
    { token, email, rol }
  );
}
