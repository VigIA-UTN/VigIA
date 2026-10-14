import { describe, expect, it } from 'vitest';
import { aplicarAlertaEnAnimales, coincideFiltroTipo, esAnestro, severidadParaAnimal, type AlertaResponse, type AnimalAislado } from './alertaApi';

const alerta = (extra: Partial<AlertaResponse>): AlertaResponse => ({
    id: 1, tipo: 'AISLAMIENTO_SOCIAL', animalId: 10, tsGeneracion: '', estado: 'PENDIENTE',
    accionRecomendada: '', severidad: 'ALTA', ...extra,
});
const fila = (animalId: number, severidad: 'ALTA' | 'MEDIA', tsRegreso: string | null): AnimalAislado => ({
    animalId, identificador: String(animalId), alias: null, minutosFuera: 30, minutosFueraSeguidos: 20,
    distanciaGrupoM: 300, severidad, tsRegreso,
});

describe('esAnestro', () => {
    it('reconoce el subtipo que manda el backend', () =>
        expect(esAnestro({ tipo: 'CELO', subtipo: 'Anestro Detectado' })).toBe(true));
    it('reconoce el tipo crudo (mapper de AnimalServiceImpl)', () =>
        expect(esAnestro({ tipo: 'Anestro Detectado' })).toBe(true));
    it('un celo no es anestro', () =>
        expect(esAnestro({ tipo: 'CELO', subtipo: 'Celo Detectado' })).toBe(false));
});

describe('coincideFiltroTipo', () => {
    it('Geoespacial agrupa abigeato, fuera de perímetro y aislamiento', () => {
        expect(coincideFiltroTipo({ tipo: 'FUERA_DE_PERIMETRO' }, 'GEOESPACIAL')).toBe(true);
        expect(coincideFiltroTipo({ tipo: 'AISLAMIENTO_SOCIAL' }, 'GEOESPACIAL')).toBe(true);
        expect(coincideFiltroTipo({ tipo: 'ABIGEATO' }, 'GEOESPACIAL')).toBe(true);
    });
    it('Geoespacial no incluye celo', () =>
        expect(coincideFiltroTipo({ tipo: 'CELO' }, 'GEOESPACIAL')).toBe(false));
    it('Celo coincide solo con celo', () => {
        expect(coincideFiltroTipo({ tipo: 'CELO' }, 'CELO')).toBe(true);
        expect(coincideFiltroTipo({ tipo: 'SALUD' }, 'CELO')).toBe(false);
    });
    it('sin filtro coincide con todo', () =>
        expect(coincideFiltroTipo({ tipo: 'SALUD' }, '')).toBe(true));
});

describe('severidadParaAnimal', () => {
    const aislamiento = alerta({ animalesAislados: [fila(10, 'ALTA', null), fila(11, 'ALTA', '2026-10-13T10:00:00'), fila(12, 'MEDIA', null)] });

    it('un animal que ya volvió pesa como aviso (MEDIA), aunque la alerta sea ALTA', () =>
        expect(severidadParaAnimal(aislamiento, 11)).toBe('MEDIA'));
    it('un animal que sigue afuera usa su propia severidad', () => {
        expect(severidadParaAnimal(aislamiento, 10)).toBe('ALTA');
        expect(severidadParaAnimal(aislamiento, 12)).toBe('MEDIA');
    });
    it('una alerta que no es de aislamiento usa la severidad de la alerta', () =>
        expect(severidadParaAnimal(alerta({ tipo: 'CELO', severidad: 'BAJA' }), 10)).toBe('BAJA'));
    it('una alerta de aislamiento sin lista (vieja) usa la severidad de la alerta', () =>
        expect(severidadParaAnimal(alerta({ severidad: 'ALTA' }), 10)).toBe('ALTA'));
});

describe('aplicarAlertaEnAnimales', () => {
    const animal = (id: number, alertasAnimal: AlertaResponse[] = []) => ({ id, alertasAnimal });
    const ids = (animales: { id: number; alertasAnimal: AlertaResponse[] }[]) =>
        Object.fromEntries(animales.map(a => [a.id, a.alertasAnimal.map(al => al.id)]));

    it('una alerta de aislamiento llega a cada animal que lista', () => {
        const nueva = alerta({ id: 7, animalId: 10, animalesAislados: [fila(10, 'ALTA', null), fila(11, 'MEDIA', null)] });
        expect(ids(aplicarAlertaEnAnimales([animal(10), animal(11), animal(12)], nueva)))
            .toEqual({ 10: [7], 11: [7], 12: [] });
    });
    it('si cambia el principal, el anterior pierde la copia cuando ya no figura', () => {
        const vieja = alerta({ id: 7, animalId: 10 });
        const actualizada = alerta({ id: 7, animalId: 11, animalesAislados: [fila(11, 'ALTA', null)] });
        expect(ids(aplicarAlertaEnAnimales([animal(10, [vieja]), animal(11)], actualizada)))
            .toEqual({ 10: [], 11: [7] });
    });
    it('una alerta de celo solo llega a su animal', () => {
        const celo = alerta({ id: 8, tipo: 'CELO', animalId: 12 });
        expect(ids(aplicarAlertaEnAnimales([animal(10), animal(12)], celo))).toEqual({ 10: [], 12: [8] });
    });
    it('una actualización reemplaza la alerta en su lugar, con el regreso nuevo', () => {
        const otra = alerta({ id: 3, tipo: 'SALUD', animalId: 10 });
        const antes = alerta({ id: 7, animalesAislados: [fila(10, 'ALTA', null)] });
        const despues = alerta({ id: 7, animalesAislados: [fila(10, 'ALTA', '2026-10-13T10:00:00')] });
        const [resultado] = aplicarAlertaEnAnimales([animal(10, [otra, antes])], despues);
        expect(resultado.alertasAnimal.map(a => a.id)).toEqual([3, 7]);
        expect(resultado.alertasAnimal[1].animalesAislados?.[0].tsRegreso).toBe('2026-10-13T10:00:00');
    });
});
