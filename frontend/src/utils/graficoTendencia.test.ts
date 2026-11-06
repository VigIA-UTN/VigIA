import { describe, expect, it } from 'vitest';
import { valoresGrillaY } from './graficoTendencia';

describe('valoresGrillaY', () => {
    it('con máximo 1 no repite el valor: la mitad redondeada coincide con el máximo', () => {
        expect(valoresGrillaY(1)).toEqual([0, 1]);
    });

    it('con máximo mayor a 1 devuelve cero, la mitad y el máximo', () => {
        expect(valoresGrillaY(2)).toEqual([0, 1, 2]);
        expect(valoresGrillaY(3)).toEqual([0, 2, 3]);
        expect(valoresGrillaY(10)).toEqual([0, 5, 10]);
    });
});
