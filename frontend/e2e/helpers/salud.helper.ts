import { spawnSync } from 'node:child_process';
import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { idEstablecimiento, tokenPorClave } from './api.helper';
import { contextoRodeo, crearAnimal, type AnimalE2 } from './rodeo.helper';

/**
 * Datos para los flujos reproductivos y sanitarios (E3).
 *
 * Lo que el escenario ya trae se lee de ahí (A-102, A-103, A-106). Las alertas y la cohesión las generan los jobs del
 * worker y no hay API para crearlas, así que las variantes que el escenario no tiene (otras antigüedades, otra
 * severidad, cohesión) se insertan por SQL con los mismos campos que usa escenario.sql, sobre un animal nuevo de
 * Fixture A: no cambian lo que esperan otros tests y limpiar.sql las borra con el establecimiento.
 */

/** Los timestamps de la base son hora local de Buenos Aires, como LocalDateTime.now() en la API y el worker. */
export const AHORA = "(now() AT TIME ZONE 'America/Argentina/Buenos_Aires')";

export function sql(consulta: string): string {
  const resultado = spawnSync(
    'docker',
    ['exec', '-i', 'vigia-db', 'psql', '-U', 'vigia_user', '-d', 'vigia', '-q', '-At', '-v', 'ON_ERROR_STOP=1'],
    { input: consulta, encoding: 'utf-8' }
  );
  if (resultado.status !== 0) {
    throw new Error(`Falló el SQL (¿está levantado vigia-db?): ${resultado.stderr || resultado.error}`);
  }
  return resultado.stdout.trim();
}

/** Animal nuevo de Fixture A, sin alertas ni historial. */
export async function animalNuevo(request: APIRequestContext): Promise<AnimalE2> {
  return crearAnimal(request, await contextoRodeo(request));
}

/** Alerta de celo pendiente detectada hace `horas`, con la ventana óptima de 6 a 24 h desde la detección. */
export function crearAlertaCelo(animalId: number, horas: number): number {
  return Number(
    sql(`INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, valor_umbral, valor_detectado,
           estado, ventana_inicio_ins, ventana_fin_ins, confianza_modelo)
         SELECT ${animalId}, 'Celo Detectado', 'ALTA', x.ts, 'CELO', 0.947, 0.95, 'PENDIENTE',
           x.ts + interval '6 hours', x.ts + interval '24 hours', 0.95
         FROM (SELECT ${AHORA} - make_interval(hours => ${horas}) AS ts) x
         RETURNING id;`)
  );
}

/** Alerta sanitaria pendiente con la severidad indicada. */
export function crearAlertaSalud(animalId: number, severidad: 'MEDIA' | 'ALTA' = 'ALTA'): number {
  return Number(
    sql(`INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, valor_umbral, valor_detectado,
           estado, confianza_modelo, desvio_detectado, posible_causa)
         VALUES (${animalId}, 'SALUD', '${severidad}', ${AHORA} - interval '2 hours', 'SALUD', 0.5, 0.72, 'PENDIENTE',
           0.72, 1, 'Caída de actividad respecto de su perfil')
         RETURNING id;`)
  );
}

/** Deja al animal con perfil de salud (fuera de calibración) y un registro de cohesión de hoy, en escala 0 a 1. */
export function registrarCohesion(animalId: number, indice: number): void {
  sql(`INSERT INTO perfiles_referencia (animal_id, perfil_json, fecha_calculo) VALUES (${animalId}, '{}', ${AHORA});
       INSERT INTO registros_cohesion (animal_id, establecimiento_id, fecha, indice_cohesion, tiempo_fuera_grupo_s)
       SELECT id, establecimiento_id, ${AHORA}, ${indice}, 3600 FROM animales WHERE id = ${animalId};`);
}

/** Id de un animal del escenario por su identificador (A-102). */
export async function idAnimalEscenario(request: APIRequestContext, identificador: string): Promise<number> {
  const token = await tokenPorClave(request, 'productorA');
  const establecimientoId = await idEstablecimiento(request, token, 'Fixture A');
  const respuesta = await request.get(`/api/v1/animales?establecimientoId=${establecimientoId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(respuesta.status()).toBe(200);
  const animal = ((await respuesta.json()) as { id: number; idCaravanaOficial: string }[]).find(
    (a) => a.idCaravanaOficial === identificador
  );
  if (!animal) throw new Error(`No está el animal ${identificador} en Fixture A: ¿se cargó el escenario?`);
  return animal.id;
}

/** El veterinario de Fixture A confirma la alerta y le registra un diagnóstico, por API. */
export async function diagnosticarPorApi(
  request: APIRequestContext,
  alertaId: number,
  diagnostico: string,
  tratamiento: string
): Promise<number> {
  const headers = { Authorization: `Bearer ${await tokenPorClave(request, 'veterinarioA')}` };
  const confirmada = await request.put(`/api/v1/alertas/estado/${alertaId}?estado=CONFIRMADA`, { headers });
  expect(confirmada.status(), await confirmada.text()).toBe(200);
  const creado = await request.post(`/api/v1/alertas/${alertaId}/diagnosticos`, {
    headers,
    data: { diagnostico, tratamiento },
  });
  expect(creado.ok(), await creado.text()).toBeTruthy();
  return (await creado.json()).id as number;
}

/** Fila del Histórico de Alertas de un animal: la columna muestra su id interno (#123). */
export function filaAlerta(page: Page, animalId: number) {
  return page.getByRole('row').filter({ has: page.getByRole('cell', { name: `#${animalId}`, exact: true }) });
}

/** Abre el Histórico de Alertas y el detalle de la alerta de ese animal. */
export async function abrirDetalleAlerta(page: Page, animalId: number): Promise<void> {
  await page.goto('/alertas');
  await filaAlerta(page, animalId).first().getByRole('button', { name: /Ver Detalle/i }).click();
}

/** Expande un evento del historial clínico (el más reciente ya viene abierto). */
export async function expandirEvento(evento: Locator): Promise<void> {
  const cabecera = evento.locator('.hc-event-head');
  if ((await cabecera.getAttribute('aria-expanded')) !== 'true') await cabecera.click();
}
