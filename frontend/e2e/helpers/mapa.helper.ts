import { spawnSync } from 'node:child_process';
import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { emailUnico, invitarProductor, PASSWORD_E2E, tokenPorClave, type UsuarioCreado } from './api.helper';
import { contextoRodeo, crearAnimal, crearCaravana, sufijoUnico, type AnimalE2, type CaravanaE2 } from './rodeo.helper';
import { AHORA, sql } from './salud.helper';

/**
 * Datos para los flujos de mapa y Dashboard (E5).
 *
 * Ningún spec modifica los animales ni las alertas de Fixture B, así que sus valores se pueden verificar exactos. Lo
 * que depende de la frescura del dato (los pines) usa animales propios de Fixture A: a los 15 minutos de cargar el
 * escenario, todos sus animales pasan a "sin datos recientes".
 */

/** Adentro del perímetro de Fixture A (centro -36.5 / -61.5, ±0.0045) y a la vista con el zoom inicial del mapa. */
export const POSICION_FIXTURE_A = { lat: -36.5005, lng: -61.5 };

const URL_INGESTA = 'http://127.0.0.1:8081/api/v1/ingesta/uplink';

export interface AnimalEnMapa extends AnimalE2 {
  caravana: CaravanaE2;
}

/** Tiene menos de un año: el job de anestro no la evalúa y no le aparece ninguna alerta que el test no creó. */
function fechaNacimientoJoven(): string {
  const fecha = new Date();
  fecha.setMonth(fecha.getMonth() - 6);
  return fecha.toISOString().slice(0, 10);
}

/** Animal nuevo de un rodeo, sin caravana ni telemetría. */
export async function animalJoven(request: APIRequestContext, rodeoLoteId?: number): Promise<AnimalE2> {
  return crearAnimal(request, await contextoRodeo(request), { rodeoLoteId, fechaNacimiento: fechaNacimientoJoven() });
}

/**
 * Animal nuevo de Fixture A con caravana y su última posición hace `minutosAtras`. La telemetría va marcada como
 * procesada, igual que la del escenario, para que los jobs de inferencia no generen alertas sobre ella.
 */
export async function animalEnMapa(request: APIRequestContext, minutosAtras = 0): Promise<AnimalEnMapa> {
  const ctx = await contextoRodeo(request);
  const caravana = await crearCaravana(request, ctx);
  const animal = await crearAnimal(request, ctx, { caravanaId: caravana.id, fechaNacimiento: fechaNacimientoJoven() });
  sql(`INSERT INTO telemetria (fecha_hora, caravana_id, latitud, longitud, move_state, bateria,
         estado_inferencia_celo, estado_inferencia_salud, estado_inferencia_geo)
       VALUES (${AHORA} - make_interval(mins => ${minutosAtras}), ${caravana.id}, ${POSICION_FIXTURE_A.lat},
         ${POSICION_FIXTURE_A.lng}, 3, 80, 'COMPLETADO', 'COMPLETADO', 'COMPLETADO');`);
  return { ...animal, caravana };
}

/** Alerta de salida del perímetro pendiente, generada hace 30 minutos. */
export function crearAlertaPerimetro(animalId: number): number {
  return Number(
    sql(`INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, valor_umbral, valor_detectado, estado)
         VALUES (${animalId}, 'Fuera de Perímetro', 'ALTA', ${AHORA} - interval '30 minutes', 'FUERA_DE_PERIMETRO', 1.0, 0.0,
           'PENDIENTE')
         RETURNING id;`)
  );
}

/** Manda una posición por el webhook de ingesta, como lo hace una caravana a través de TTN. */
export async function enviarPosicion(
  request: APIRequestContext,
  caravana: CaravanaE2,
  posicion: { lat: number; lng: number },
  frameCounter: number
): Promise<void> {
  const apiKey = spawnSync('docker', ['exec', 'vigia-backend-worker', 'printenv', 'TTN_WEBHOOK_API_KEY'], {
    encoding: 'utf-8',
  }).stdout.trim();
  const respuesta = await request.post(URL_INGESTA, {
    headers: { Authorization: `Bearer ${apiKey}` },
    data: {
      end_device_ids: { dev_eui: caravana.codigo },
      uplink_message: {
        received_at: new Date().toISOString(),
        f_cnt: frameCounter,
        decoded_payload: { type: 'GNSSPosition', latitude: posicion.lat, longitude: posicion.lng, moveState: 3, battery: '80%' },
      },
    },
  });
  expect(await respuesta.text()).not.toContain('Ignorado');
}

/**
 * Deja en el mapa solo a estos animales, con los datos reales del backend. Fixture A acumula los animales que crean
 * los otros tests, en el mismo lugar, y sus pines se taparían entre sí.
 */
export async function mostrarSoloEnMapa(page: Page, ...animales: { id: number }[]): Promise<void> {
  const ids = animales.map((a) => a.id);
  await page.route(/\/api\/v1\/animales\/mapa(\?|$)/, async (ruta) => {
    const respuesta = await ruta.fetch();
    const todos = (await respuesta.json()) as { id: number }[];
    await ruta.fulfill({ response: respuesta, json: todos.filter((a) => ids.includes(a.id)) });
  });
}

/** Pines de animales del mapa; con `estado`, solo los de ese estado (normal, borde, critico, sindatos). */
export function pines(page: Page, estado?: 'normal' | 'borde' | 'critico' | 'sindatos') {
  return page.locator(estado ? `img.leaflet-marker-icon[src*="pin-${estado}-"]` : 'img.leaflet-marker-icon');
}

/**
 * Productor nuevo de un establecimiento nuevo sin rodeos ni animales, para los estados vacíos. El nombre y el email
 * usan los prefijos del escenario: limpiar.sql los borra en la próxima carga.
 */
export async function productorDeEstablecimientoVacio(request: APIRequestContext): Promise<UsuarioCreado> {
  const nombre = `Fixture Vacío ${sufijoUnico()}`;
  const token = await tokenPorClave(request, 'admin');
  const alta = await request.post('/api/v1/establecimientos', {
    headers: { Authorization: `Bearer ${token}` },
    data: { nombre, latitud: -36.8, longitud: -61.8 },
  });
  expect(alta.status(), await alta.text()).toBe(201);

  const email = emailUnico('productor-vacio');
  const invitacion = await invitarProductor(request, nombre, email);
  const registro = await request.post('/api/v1/auth/register', {
    data: { nombre: 'Productor', apellido: 'Vacío', password: PASSWORD_E2E, token: invitacion.token },
  });
  expect(registro.status(), await registro.text()).toBe(201);
  return { email, password: PASSWORD_E2E, rol: 'PRODUCTOR' };
}
