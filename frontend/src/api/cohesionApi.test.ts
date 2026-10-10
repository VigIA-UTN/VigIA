import { describe, expect, it } from 'vitest';
import { estadoCohesion, mockCohesion } from './cohesionApi';

describe('estadoCohesion', () => {
  it('interpreta el índice según los umbrales 60 / 40', () => {
    expect(estadoCohesion(60).clave).toBe('NORMAL');
    expect(estadoCohesion(59).clave).toBe('BORDE');
    expect(estadoCohesion(40).clave).toBe('BORDE');
    expect(estadoCohesion(39).clave).toBe('CRITICO');
    expect(estadoCohesion(39).label).toBe('Alejado');
  });
});

describe('mockCohesion', () => {
  const DIA = 86400_000;
  const hasta = new Date(2026, 9, 9).getTime();

  it.each([1, 7, 30, 90])('serie de %i días: valores en 0–100, termina en el actual y tiene días sin datos', dias => {
    const r = mockCohesion(4, hasta - dias * DIA, hasta);
    const valores = r.serie.map(p => p.valor);
    expect(valores.some(v => v === null)).toBe(true);
    expect(valores.every(v => v === null || (v >= 0 && v <= 100))).toBe(true);
    expect(valores.at(-1)).toBe(r.actual);
  });
});
