import { describe, expect, it } from 'vitest';
import type { AnimalResponse, RegistroPesoResponse } from '../api/animalesApi';
import { calcularGananciaPeso } from './gananciaPeso';

const HOY = new Date(2026, 9, 15, 14, 30); // 15/10/2026, a media tarde

const animal = (id: number, estadoActual: AnimalResponse['estadoActual'] = 'ACTIVO') =>
    ({ id, estadoActual }) as AnimalResponse;

let nextId = 1;
/** Pesada de `animalId` hace `diasAtras` días (fecha local 'YYYY-MM-DD', como la manda el backend). */
const pesada = (animalId: number, diasAtras: number, valorKg = 400): RegistroPesoResponse => {
    const d = new Date(HOY.getFullYear(), HOY.getMonth(), HOY.getDate() - diasAtras);
    const fecha = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    return { id: nextId++, animalId, fecha, valorKg, unidad: 'kg', fechaRegistro: `${fecha}T09:00:00` };
};

describe('calcularGananciaPeso — cobertura', () => {
    it('sin pesadas: 0 de n', () => {
        const r = calcularGananciaPeso([animal(1), animal(2)], [], HOY);
        expect(r.cobertura).toEqual({ pesados: 0, total: 2, pct: 0 });
    });

    it('todos pesados en los últimos 90 días: 100 %', () => {
        const r = calcularGananciaPeso([animal(1), animal(2)], [pesada(1, 10), pesada(2, 0)], HOY);
        expect(r.cobertura).toEqual({ pesados: 2, total: 2, pct: 100 });
    });

    it('la pesada de hace exactamente 90 días cuenta; la de hace 91 no', () => {
        const r = calcularGananciaPeso([animal(1), animal(2)], [pesada(1, 90), pesada(2, 91)], HOY);
        expect(r.cobertura).toEqual({ pesados: 1, total: 2, pct: 50 });
    });

    it('un animal con varias pesadas cuenta una sola vez', () => {
        const r = calcularGananciaPeso([animal(1), animal(2), animal(3)], [pesada(1, 5), pesada(1, 40)], HOY);
        expect(r.cobertura).toEqual({ pesados: 1, total: 3, pct: 33 });
    });

    it('los animales inactivos no cuentan, ni en el total ni como pesados', () => {
        const r = calcularGananciaPeso([animal(1), animal(2, 'INACTIVO')], [pesada(2, 5)], HOY);
        expect(r.cobertura).toEqual({ pesados: 0, total: 1, pct: 0 });
    });

    it('ignora pesadas de animales fuera de la lista (otro lote)', () => {
        const r = calcularGananciaPeso([animal(1)], [pesada(99, 5)], HOY);
        expect(r.cobertura).toEqual({ pesados: 0, total: 1, pct: 0 });
    });

    it('sin animales: 0 de 0', () => {
        expect(calcularGananciaPeso([], [pesada(1, 5)], HOY).cobertura).toEqual({ pesados: 0, total: 0, pct: 0 });
    });
});

/** Animal `id` con una pesada hace 30 días (400 kg) y otra hoy, ganando `adpv` kg/día. */
const par = (id: number, adpv: number) => [pesada(id, 30, 400), pesada(id, 0, 400 + adpv * 30)];
const ids = (lista: AnimalResponse[]) => lista.map(a => a.id);

describe('calcularGananciaPeso — ADPV', () => {
    it('ADPV = (última − anterior) / días entre ambas', () => {
        const r = calcularGananciaPeso([animal(1)], [pesada(1, 40, 400), pesada(1, 10, 418)], HOY);
        expect(r.adpvMediana).toBeCloseTo(0.6);
        expect(r.n).toBe(1);
    });

    it('clasifica en los umbrales: < −0,2 pierde, −0,2 a < 0,2 estable, ≥ 0,2 gana', () => {
        const animales = [1, 2, 3, 4, 5].map(id => animal(id));
        const pesos = [...par(1, -0.21), ...par(2, -0.2), ...par(3, 0.19), ...par(4, 0.2), ...par(5, 0.8)];
        const r = calcularGananciaPeso(animales, pesos, HOY);
        expect(ids(r.pierde)).toEqual([1]);
        expect(ids(r.estable)).toEqual([2, 3]);
        expect(ids(r.gana)).toEqual([4, 5]);
    });

    it('la pesada anterior debe estar a ≥ 14 días de la última; si no, se usa una más vieja', () => {
        // Sin la regla: (418 − 420) / 7 = −0,29 → pierde. Con la regla: (418 − 400) / 45 = 0,4 → gana.
        const r = calcularGananciaPeso([animal(1)], [pesada(1, 50, 400), pesada(1, 12, 420), pesada(1, 5, 418)], HOY);
        expect(r.adpvMediana).toBeCloseTo(0.4);
        expect(ids(r.gana)).toEqual([1]);
    });

    it('excluye animales con una sola pesada o sin par a ≥ 14 días', () => {
        const r = calcularGananciaPeso([animal(1), animal(2)], [pesada(1, 5), pesada(2, 20), pesada(2, 10)], HOY);
        expect(r.n).toBe(0);
        expect(r.adpvMediana).toBeNull();
    });

    it('excluye animales cuya última pesada tiene más de 120 días', () => {
        const r = calcularGananciaPeso([animal(1), animal(2)], [...par(1, 0.5), pesada(2, 200, 400), pesada(2, 121, 440)], HOY);
        expect(r.n).toBe(1);
        expect(ids(r.gana)).toEqual([1]);
    });

    it('con dos pesadas el mismo día usa la cargada último', () => {
        const pesos = [pesada(1, 30, 400), pesada(1, 0, 999), pesada(1, 0, 412)];
        const r = calcularGananciaPeso([animal(1)], pesos, HOY);
        expect(r.adpvMediana).toBeCloseTo(0.4);
    });

    it('mediana con cantidad impar y par de animales', () => {
        const impar = calcularGananciaPeso([1, 2, 3].map(id => animal(id)), [...par(1, -0.5), ...par(2, 0.3), ...par(3, 0.9)], HOY);
        expect(impar.adpvMediana).toBeCloseTo(0.3);
        const parAnimales = calcularGananciaPeso([1, 2].map(id => animal(id)), [...par(1, 0.2), ...par(2, 0.6)], HOY);
        expect(parAnimales.adpvMediana).toBeCloseTo(0.4);
    });

    it('variación: mediana de hoy menos la mediana calculada hace 90 días, con ≥ 5 animales en ambas', () => {
        const animales = [1, 2, 3, 4, 5].map(id => animal(id));
        // Antes (al día −90): par 130→100 días atrás a 0,1 kg/día. Hoy: par 40→10 días atrás a 0,5 kg/día.
        const pesos = animales.flatMap(a => [pesada(a.id, 130, 400), pesada(a.id, 100, 403), pesada(a.id, 40, 420), pesada(a.id, 10, 435)]);
        const r = calcularGananciaPeso(animales, pesos, HOY);
        expect(r.adpvMediana).toBeCloseTo(0.5);
        expect(r.variacion).toBeCloseTo(0.4);
    });

    it('variación null si hace 90 días había menos de 5 animales con par válido', () => {
        const animales = [1, 2, 3, 4, 5].map(id => animal(id));
        const pesos = animales.flatMap(a => [pesada(a.id, 40, 420), pesada(a.id, 10, 435)]);
        pesos.push(pesada(1, 130, 400), pesada(1, 100, 403));
        expect(calcularGananciaPeso(animales, pesos, HOY).variacion).toBeNull();
    });

    it('variación null si hoy hay menos de 5 animales con par válido', () => {
        const animales = [1, 2, 3, 4].map(id => animal(id));
        const pesos = animales.flatMap(a => [pesada(a.id, 130, 400), pesada(a.id, 100, 403), pesada(a.id, 40, 420), pesada(a.id, 10, 435)]);
        expect(calcularGananciaPeso(animales, pesos, HOY).variacion).toBeNull();
    });

    it('ignora animales inactivos y de otro lote', () => {
        const r = calcularGananciaPeso([animal(1), animal(2, 'INACTIVO')], [...par(1, 0.5), ...par(2, -1), ...par(99, -1)], HOY);
        expect(r.n).toBe(1);
        expect(r.pierde).toEqual([]);
    });
});
