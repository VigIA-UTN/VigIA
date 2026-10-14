import { describe, expect, it } from 'vitest';
import { coincideFiltroTipo, esAnestro } from './alertaApi';

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
