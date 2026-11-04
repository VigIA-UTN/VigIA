import axiosClient from './axiosClient';
import type { AlertaResponse, VentanaInseminacionResponse } from './alertaApi';
import type { IntervencionResponse } from './animalesApi';

/** Lo que el Dashboard muestra de cada animal (la cola y las ventanas llevan a su ficha). */
export interface AnimalResumen {
  id: number;
  idCaravanaOficial: string | null;
  alias: string | null;
  rodeoLoteNombre: string | null;
}

/** Un celo pendiente con su ventana de inseminación. */
export interface VentanaAlerta {
  alerta: AlertaResponse;
  animal: AnimalResumen | null;
  ventana: VentanaInseminacionResponse;
}

/** Los umbrales con los que el backend calculó el Dashboard. */
export interface UmbralesDashboard {
  horasSinSenial: number;
  bateriaBajaPorcentaje: number;
  horasVentanaPorVencer: number;
  diasDiagnosticoInseminada: number;
  colaMax: number;
  horasSaludSinAtender: number;
  diasVaciaLarga: number;
  diasGestacion: number;
}

export type TipoTendencia = 'CELO' | 'SALUD' | 'FUERA_DE_PERIMETRO';

export interface TendenciaResponse {
  /** Semanas ISO (`2026-W45`), la actual al final. */
  semanas: string[];
  tipos: TipoTendencia[];
  datos: Record<TipoTendencia, number[]>;
}

/** Pestaña "Centro de mando" (H0043), calculada en el backend. */
export interface CentroMandoResponse {
  alertasPendientes: { total: number; celo: number; salud: number; perimetro: number };
  ventanasAbiertas: { total: number; porVencer: number; perdidasUltimos30Dias: number };
  bajoObservacion: { total: number; enfermos: number };
  cobertura: {
    total: number;
    ok: number;
    calibrando: number;
    sinSenial: number;
    bateriaBaja: number;
    sinCaravana: number;
    porcentaje: number;
  };
  /** Ya ordenada por urgencia y recortada. Las caravanas sin señal vienen con tipo `CARAVANA` e id negativo. */
  cola: { alerta: AlertaResponse; animal: AnimalResumen | null; ventana: VentanaInseminacionResponse | null }[];
  /** Abiertas primero, cada grupo por tiempo restante. */
  ventanas: VentanaAlerta[];
  embudo: {
    totalReproductivos: number;
    /** Por estado reproductivo, en el orden del embudo (`SIN_ESTADO` si no tiene). */
    distribucion: Record<string, number>;
    diasAbiertosPromedio: number;
    vacasAbiertas: number;
    inseminadasSinDiagnostico: number;
  };
  tendencia: TendenciaResponse;
  umbrales: UmbralesDashboard;
}

export async function getCentroMando(establecimientoId: number, rodeoId?: number): Promise<CentroMandoResponse> {
  const params = rodeoId ? { rodeoId } : undefined;
  const response = await axiosClient.get<CentroMandoResponse>(
    `/establecimientos/${establecimientoId}/dashboard/centro-mando`,
    { params },
  );
  return response.data;
}

/** Pestaña "Salud y reproducción" (H0044), calculada en el backend. */
export interface SaludReproduccionResponse {
  periodoDias: number;
  indicadores: {
    diasAbiertosPromedio: number;
    vacasAbiertas: number;
    saludSinAtender: number;
    celosSinAtender: number;
    celosPorVencer: number;
  };
  /** Abiertas primero, cada grupo por tiempo restante. */
  ventanas: VentanaAlerta[];
  embudo: {
    totalReproductivos: number;
    distribucion: Record<string, number>;
    vaciasLargas: AnimalResumen[];
    inseminadasSinDiagnostico: AnimalResumen[];
    anestroSinIntervencion: AnimalResumen[];
    completitudPorcentaje: number;
  };
  /** De la más vieja a la más nueva. */
  alertasSaludSinAtender: { alerta: AlertaResponse; animal: AnimalResumen | null }[];
  /** Enfermos primero. */
  enObservacion: {
    animal: AnimalResumen;
    estadoSalud: 'ENFERMO' | 'EN_OBSERVACION';
    ultimaIntervencionTipo: IntervencionResponse['tipo'] | null;
    diasSinIntervencion: number | null;
  }[];
  partos: {
    /** Las próximas 12 semanas, cada una de 7 días desde hoy (`desde` en `YYYY-MM-DD`). */
    semanas: { desde: string; animales: AnimalResumen[] }[];
    /** Desde el lunes de esta semana y por un año; solo los días con partos. */
    porDia: { fecha: string; animales: AnimalResumen[] }[];
    gestantesSinFecha: number;
    vencidos: number;
    totalGestantes: number;
  };
  desempenyo: {
    reproductivos: number;
    celosDetectados: number;
    celosAtendidos: number;
    ventanasPerdidas: number;
    inseminadas: number;
    gestantes: number;
  };
  reincidencia: {
    animales: { animal: AnimalResumen | null; alertas: number }[];
    lotes: { nombre: string; salidas: number }[];
  };
  ultimaIntervencion: {
    masReciente: {
      animal: AnimalResumen | null;
      tipo: IntervencionResponse['tipo'];
      fechaHora: string;
      interventor: string | null;
    } | null;
    sinIntervencionHaceMas: { animal: AnimalResumen; dias: number | null }[];
  };
  umbrales: UmbralesDashboard;
}

export async function getSaludReproduccion(
  establecimientoId: number,
  periodoDias: number,
  rodeoId?: number,
): Promise<SaludReproduccionResponse> {
  const params = rodeoId ? { periodoDias, rodeoId } : { periodoDias };
  const response = await axiosClient.get<SaludReproduccionResponse>(
    `/establecimientos/${establecimientoId}/dashboard/salud-reproduccion`,
    { params },
  );
  return response.data;
}
