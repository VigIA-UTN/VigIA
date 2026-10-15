import type { AnimalResponse, RegistroPesoResponse } from '../api/animalesApi';

/** Cobertura: animales pesados en los últimos N días. */
export const DIAS_COBERTURA = 90;
/** Por debajo de este % la línea de cobertura se marca en ámbar. */
export const COBERTURA_BAJA_PCT = 50;
/** Animales cuya última pesada es más vieja quedan fuera del ADPV. */
export const DIAS_PESADA_VIGENTE = 120;
/** Separación mínima entre las dos pesadas: con menos, domina el llenado ruminal (±5 % del peso). */
export const DIAS_MIN_ENTRE_PESADAS = 14;
/** kg/día. Rodeos de tambo y cría (mayoría vacas adultas): mantener peso es normal → "estable". */
export const ADPV_PIERDE = -0.2;
export const ADPV_GANA = 0.2;
/** La variación compara el ADPV de hoy con el calculado hace N días... */
export const DIAS_COMPARACION = 90;
/** ...y solo se muestra si ambos tienen al menos N animales (con menos, es ruido). */
export const N_MIN_COMPARACION = 5;

interface Adpv {
    /** Mediana del ADPV (kg/día) de los animales con un par de pesadas válido; null si no hay ninguno. */
    adpvMediana: number | null;
    n: number;
    gana: AnimalResponse[];
    estable: AnimalResponse[];
    pierde: AnimalResponse[];
}

export interface GananciaPeso extends Adpv {
    /** ADPV de hoy menos el de hace DIAS_COMPARACION días; null si alguno tiene < N_MIN_COMPARACION animales. */
    variacion: number | null;
    cobertura: { pesados: number; total: number; pct: number };
}

/** Días entre una fecha 'YYYY-MM-DD' (local) y `hoy`. */
function diasAtras(fecha: string, hoy: Date): number {
    const [y, m, d] = fecha.slice(0, 10).split('-').map(Number);
    const hoy0 = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());
    return Math.round((hoy0.getTime() - new Date(y, m - 1, d).getTime()) / (24 * 60 * 60 * 1000));
}

/** ADPV por animal a la fecha `hoy`: última pesada vs. la más reciente a ≥ DIAS_MIN_ENTRE_PESADAS de ella. */
function calcularAdpv(activos: AnimalResponse[], pesos: RegistroPesoResponse[], hoy: Date): Adpv {
    const porAnimal = new Map<number, RegistroPesoResponse[]>();
    pesos.forEach(p => porAnimal.set(p.animalId, [...(porAnimal.get(p.animalId) ?? []), p]));

    const gana: AnimalResponse[] = [], estable: AnimalResponse[] = [], pierde: AnimalResponse[] = [];
    const adpvs: number[] = [];
    activos.forEach(a => {
        // Más reciente primero; el mismo día, la cargada último (id mayor)
        const lista = (porAnimal.get(a.id) ?? []).sort((x, y) => y.fecha.localeCompare(x.fecha) || y.id - x.id);
        if (lista.length < 2) return;
        const dUlt = diasAtras(lista[0].fecha, hoy);
        if (dUlt > DIAS_PESADA_VIGENTE) return;
        const anterior = lista.find(p => diasAtras(p.fecha, hoy) - dUlt >= DIAS_MIN_ENTRE_PESADAS);
        if (!anterior) return;

        const adpv = (lista[0].valorKg - anterior.valorKg) / (diasAtras(anterior.fecha, hoy) - dUlt);
        adpvs.push(adpv);
        (adpv < ADPV_PIERDE ? pierde : adpv >= ADPV_GANA ? gana : estable).push(a);
    });

    adpvs.sort((x, y) => x - y);
    const mitad = Math.floor(adpvs.length / 2);
    const adpvMediana = adpvs.length === 0 ? null
        : adpvs.length % 2 === 1 ? adpvs[mitad] : (adpvs[mitad - 1] + adpvs[mitad]) / 2;

    return { adpvMediana, n: adpvs.length, gana, estable, pierde };
}

/**
 * Métricas de la card "Ganancia de peso" del dashboard.
 * `animales` ya viene filtrado por lote; los inactivos se descartan acá.
 */
export function calcularGananciaPeso(
    animales: AnimalResponse[],
    pesos: RegistroPesoResponse[],
    hoy = new Date(),
): GananciaPeso {
    const activos = animales.filter(a => a.estadoActual !== 'INACTIVO');
    const pesadosRecientes = new Set(
        pesos.filter(p => diasAtras(p.fecha, hoy) <= DIAS_COBERTURA).map(p => p.animalId),
    );
    const pesados = activos.filter(a => pesadosRecientes.has(a.id)).length;
    const total = activos.length;

    const actual = calcularAdpv(activos, pesos, hoy);
    // El mismo cálculo como se veía hace DIAS_COMPARACION días: solo con las pesadas que ya existían
    const antes = calcularAdpv(
        activos,
        pesos.filter(p => diasAtras(p.fecha, hoy) >= DIAS_COMPARACION),
        new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() - DIAS_COMPARACION),
    );
    const variacion = actual.n >= N_MIN_COMPARACION && antes.n >= N_MIN_COMPARACION
        ? actual.adpvMediana! - antes.adpvMediana!
        : null;

    return {
        ...actual,
        variacion,
        cobertura: { pesados, total, pct: total > 0 ? Math.round((pesados / total) * 100) : 0 },
    };
}
