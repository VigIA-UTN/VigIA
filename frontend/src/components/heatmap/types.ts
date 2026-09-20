/** [lat, lng, intensity 0..1] as expected by leaflet.heat */
export type HeatLatLngTuple = [number, number, number?];

/** Design palette: cyan -> lime -> yellow -> orange. Single source for layer and legend. */
export const HEAT_GRADIENT: Record<number, string> = {
    0.2: '#13D4FB',
    0.5: '#94C11E',
    0.8: '#FFC700',
    1.0: '#F28C38',
};

export type HeatScope = 'todo' | 'rodeo' | 'animal';

export interface HeatPoint {
    lat: number;
    lng: number;
    /** 0..1 intensity */
    weight: number;
    /** ISO timestamp of the reading */
    ts: string;
    animalId: number;
    rodeoId: number | null;
}

export interface HeatFilter {
    scope: HeatScope;
    rodeoId: number | null;
    animalId: number | null;
    /** inclusive day range, epoch ms at local midnight */
    from: number;
    to: number;
}

/** Animal as the heatmap panel needs it (built by the host page from AnimalResponse). */
export interface HeatAnimal {
    id: number;
    caravana: string;
    alias: string | null;
    rodeoId: number | null;
}

export interface HeatRodeo {
    id: number;
    nombre: string;
}
