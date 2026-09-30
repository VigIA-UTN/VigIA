import { describe, expect, it } from 'vitest';
import { esAnestro } from './alertaApi';

describe('esAnestro', () => {
    it('reconoce el subtipo que manda el backend', () =>
        expect(esAnestro({ tipo: 'CELO', subtipo: 'Anestro Detectado' })).toBe(true));
    it('reconoce el tipo crudo (mapper de AnimalServiceImpl)', () =>
        expect(esAnestro({ tipo: 'Anestro Detectado' })).toBe(true));
    it('un celo no es anestro', () =>
        expect(esAnestro({ tipo: 'CELO', subtipo: 'Celo Detectado' })).toBe(false));
});
