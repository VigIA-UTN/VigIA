import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { loginPorRol } from './auth.helper';
import { idEstablecimiento, tokenPorClave } from './api.helper';

/**
 * Preparación de datos por API para los flujos de gestión del rodeo (E2).
 *
 * Los specs corren en varios navegadores contra el mismo escenario y el escenario no trae animales sin caravana, ni
 * caravanas dadas de baja, ni el lote "Lote Cuarentena" del CSV de demo. Cada test crea lo que necesita con nombres
 * únicos, así no se saltea por falta de datos ni depende del orden de ejecución.
 */

export const ESTABLECIMIENTO_E2 = 'Fixture A';
export const CLAVE_PRODUCTOR_E2 = 'productorA';

export interface ContextoRodeo {
  /** JWT del productor del escenario. */
  tokenProductor: string;
  /** JWT del administrador (único que gestiona caravanas). */
  tokenAdmin: string;
  establecimientoId: number;
}

export interface CaravanaE2 {
  id: number;
  codigo: string;
}

export interface AnimalE2 {
  id: number;
  idCaravanaOficial: string;
}

export function sufijoUnico(): string {
  return `${Date.now()}${Math.floor(Math.random() * 1000)}`;
}

function auth(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export async function contextoRodeo(request: APIRequestContext): Promise<ContextoRodeo> {
  const tokenProductor = await tokenPorClave(request, CLAVE_PRODUCTOR_E2);
  const tokenAdmin = await tokenPorClave(request, 'admin');
  const establecimientoId = await idEstablecimiento(request, tokenProductor, ESTABLECIMIENTO_E2);
  return { tokenProductor, tokenAdmin, establecimientoId };
}

/** Id del rodeo/lote activo con ese nombre; lo crea si no existe. */
export async function asegurarRodeo(request: APIRequestContext, ctx: ContextoRodeo, nombre: string): Promise<number> {
  const lista = await request.get(`/api/v1/rodeo-lotes?establecimientoId=${ctx.establecimientoId}`, {
    headers: auth(ctx.tokenProductor),
  });
  expect(lista.status()).toBe(200);
  const existente = ((await lista.json()) as { id: number; nombre: string; activo: boolean }[]).find(
    (r) => r.nombre === nombre && r.activo
  );
  if (existente) return existente.id;

  const creado = await request.post('/api/v1/rodeo-lotes', {
    headers: auth(ctx.tokenProductor),
    data: { nombre, establecimientoId: ctx.establecimientoId },
  });
  expect(creado.status(), await creado.text()).toBe(201);
  return (await creado.json()).id as number;
}

/** Caravana nueva del establecimiento, DISPONIBLE; el alias es el código para reconocerla en los selectores. */
export async function crearCaravana(request: APIRequestContext, ctx: ContextoRodeo): Promise<CaravanaE2> {
  const codigo = `E2-CAR-${sufijoUnico()}`;
  const respuesta = await request.post('/api/v1/caravana', {
    headers: auth(ctx.tokenAdmin),
    data: { dev_eui: codigo, idCaravanaOficial: codigo, alias: codigo, establecimientoId: ctx.establecimientoId },
  });
  expect(respuesta.status(), await respuesta.text()).toBe(201);
  return { id: (await respuesta.json()).id as number, codigo };
}

export async function darDeBajaCaravanaApi(request: APIRequestContext, ctx: ContextoRodeo, id: number): Promise<void> {
  const respuesta = await request.patch(`/api/v1/caravana/${id}/baja`, { headers: auth(ctx.tokenAdmin), data: {} });
  expect(respuesta.status(), await respuesta.text()).toBe(200);
}

/** Animal nuevo en el primer rodeo activo del establecimiento, con o sin caravana. */
export async function crearAnimal(
  request: APIRequestContext,
  ctx: ContextoRodeo,
  opciones: { caravanaId?: number; alias?: string } = {}
): Promise<AnimalE2> {
  const lotes = await request.get(`/api/v1/rodeo-lotes?establecimientoId=${ctx.establecimientoId}`, {
    headers: auth(ctx.tokenProductor),
  });
  expect(lotes.status()).toBe(200);
  const lote = ((await lotes.json()) as { id: number; activo: boolean }[]).find((l) => l.activo);
  if (!lote) throw new Error('El establecimiento no tiene rodeos activos');

  const idCaravanaOficial = `E2-ANI-${sufijoUnico()}`;
  const respuesta = await request.post('/api/v1/animales', {
    headers: auth(ctx.tokenProductor),
    data: {
      idCaravanaOficial,
      alias: opciones.alias ?? idCaravanaOficial,
      fechaNacimiento: '2024-05-01',
      raza: 'Holando',
      rodeoLoteId: lote.id,
      estadoActual: 'ACTIVO',
      establecimientoId: ctx.establecimientoId,
      caravanaId: opciones.caravanaId,
    },
  });
  expect(respuesta.status(), await respuesta.text()).toBe(201);
  return { id: (await respuesta.json()).id as number, idCaravanaOficial };
}

/** Pesaje por API (la UI lo muestra en las estadísticas y el gráfico de evolución de la pestaña Actividad). */
export async function registrarPesoApi(
  request: APIRequestContext,
  ctx: ContextoRodeo,
  animalId: number,
  valorKg: number,
  fecha: string
): Promise<void> {
  const respuesta = await request.post(`/api/v1/animales/${animalId}/historial-pesos`, {
    headers: auth(ctx.tokenProductor),
    data: { valorKg, unidad: 'kg', fecha },
  });
  expect(respuesta.status(), await respuesta.text()).toBe(201);
}

export async function registrarEstadoSaludApi(
  request: APIRequestContext,
  ctx: ContextoRodeo,
  animalId: number,
  estado: 'SANO' | 'EN_OBSERVACION' | 'ENFERMO'
): Promise<void> {
  const respuesta = await request.post(`/api/v1/animales/${animalId}/estado-salud`, {
    headers: auth(ctx.tokenProductor),
    data: { estado },
  });
  expect(respuesta.status(), await respuesta.text()).toBe(201);
}

/** Entra como productor de Fixture A, busca el animal en el listado y abre su detalle (pestaña Actividad por defecto). */
export async function abrirDetalleAnimal(page: Page, animal: AnimalE2): Promise<void> {
  await loginPorRol(page, 'PRODUCTOR', { establecimiento: ESTABLECIMIENTO_E2 });
  await expect(page).toHaveURL(/\/dashboard/);
  await page.getByRole('link', { name: 'Animales', exact: true }).click();
  await expect(page).toHaveURL(/\/animales/);
  await expect(page.locator('.vigia-table')).toBeVisible({ timeout: 15000 });

  await page.getByPlaceholder(/Buscar por identificador/i).fill(animal.idCaravanaOficial);
  const fila = page.locator('.vigia-table tbody tr.animal-row').filter({ hasText: animal.idCaravanaOficial });
  await expect(fila).toHaveCount(1, { timeout: 10000 });
  await fila.locator('button:has(.bi-eye)').click();
  await expect(page).toHaveURL(new RegExp(`/animales/${animal.id}`), { timeout: 10000 });
}

/** Pestaña del detalle del animal (son botones comunes, no role="tab"). */
export function pestanaAnimal(page: Page, nombre: 'Actividad' | 'Ficha' | 'Reproducción' | 'Historial') {
  return page.locator('.animal-tab-btn', { hasText: nombre });
}
