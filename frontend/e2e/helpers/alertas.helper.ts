import { expect, type Page } from '@playwright/test';
import { obtenerUsuarioPorClave } from '../fixtures/credenciales';
import { AHORA, sql } from './salud.helper';

/**
 * Datos y accesos para los flujos del ciclo de vida de las alertas (E4).
 *
 * Las alertas las generan los jobs del worker y no hay API para crearlas: los casos que necesitan una alerta propia la
 * insertan por SQL sobre un animal nuevo de Fixture A, con los mismos campos que arman los detectores. Así resolverla
 * (que no se puede deshacer) no cambia lo que esperan los demás specs, que corren en varios navegadores.
 */

export type DisparadorE4 = 'CELO' | 'SALUD' | 'FUERA_DE_PERIMETRO' | 'ABIGEATO';
export type SeveridadE4 = 'ALTA' | 'MEDIA' | 'BAJA';

const TIPO_POR_DISPARADOR: Record<DisparadorE4, string> = {
  CELO: 'Celo Detectado',
  SALUD: 'SALUD',
  FUERA_DE_PERIMETRO: 'Fuera de Perímetro',
  ABIGEATO: 'Abigeato',
};

export interface OpcionesAlerta {
  severidad?: SeveridadE4;
  /** Antigüedad de la alerta en minutos. Por defecto 30. */
  minutosAtras?: number;
  accionRecomendada?: string | null;
  valorDetectado?: number;
  valorUmbral?: number;
}

/** Alerta pendiente de cualquier tipo sobre un animal, con valor detectado y umbral conocidos. */
export function crearAlerta(animalId: number, disparador: DisparadorE4, opciones: OpcionesAlerta = {}): number {
  const {
    severidad = 'ALTA',
    minutosAtras = 30,
    accionRecomendada = null,
    valorDetectado = 0.8,
    valorUmbral = 0.5,
  } = opciones;
  const accion = accionRecomendada === null ? 'NULL' : `'${accionRecomendada.replace(/'/g, "''")}'`;
  // El celo trae su ventana de inseminación (de 6 a 24 h desde la detección), como lo arma CeloDetector.
  const [inicioVentana, finVentana] =
    disparador === 'CELO' ? ["x.ts + interval '6 hours'", "x.ts + interval '24 hours'"] : ['NULL', 'NULL'];
  return Number(
    sql(`INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, valor_umbral, valor_detectado,
           estado, accion_recomendada, ventana_inicio_ins, ventana_fin_ins)
         SELECT ${animalId}, '${TIPO_POR_DISPARADOR[disparador]}', '${severidad}', x.ts, '${disparador}', ${valorUmbral},
           ${valorDetectado}, 'PENDIENTE', ${accion}, ${inicioVentana}, ${finVentana}
         FROM (SELECT ${AHORA} - make_interval(mins => ${minutosAtras}) AS ts) x
         RETURNING id;`)
  );
}

/** Email de un usuario del escenario por su clave (productorA, veterinarioA...). */
export function email(clave: string): string {
  return obtenerUsuarioPorClave(clave).email;
}

export interface ResolucionEnBase {
  estado: string;
  /** Email de quien la resolvió; vacío si nadie. */
  usuario: string;
  motivoDescarte: string;
}

/** Cómo quedó la resolución de la alerta en la base. */
export function resolucionEnBase(alertaId: number): ResolucionEnBase {
  const [estado, usuario, motivoDescarte] = sql(
    `SELECT al.estado, coalesce(u.email, ''), coalesce(al.motivo_descarte, '')
     FROM alertas al LEFT JOIN usuarios u ON u.id = al.usuario_confirmacion_id WHERE al.id = ${alertaId};`
  ).split('|');
  return { estado, usuario, motivoDescarte };
}

/** Abre el mapa y el panel lateral de alertas. */
export async function abrirPanelAlertas(page: Page): Promise<void> {
  await page.goto('/mapa');
  await page.getByRole('button', { name: 'Alertas', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Alertas', exact: true })).toBeVisible();
}

/** Tarjeta de un animal en el panel de alertas del mapa ("#123 en estado CRITICO"). */
export function tarjetaPanel(page: Page, animalId: number) {
  return page.getByText(new RegExp(`^#${animalId} en estado`));
}

/** Abre el detalle de la alerta de ese animal desde el panel del mapa. */
export async function abrirDetalleDesdePanel(page: Page, animalId: number): Promise<void> {
  await abrirPanelAlertas(page);
  await tarjetaPanel(page, animalId).click();
}
