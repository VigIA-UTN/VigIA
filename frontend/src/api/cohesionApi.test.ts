import { beforeEach, describe, expect, it, vi } from 'vitest';
import axiosClient from './axiosClient';
import { estadoCohesion, getCohesionAnimal } from './cohesionApi';

vi.mock('./axiosClient', () => ({ default: { get: vi.fn() } }));

const get = vi.mocked(axiosClient.get);

describe('estadoCohesion', () => {
  it('interpreta el índice según los umbrales 60 / 40', () => {
    expect(estadoCohesion(60).clave).toBe('NORMAL');
    expect(estadoCohesion(59).clave).toBe('BORDE');
    expect(estadoCohesion(40).clave).toBe('BORDE');
    expect(estadoCohesion(39).clave).toBe('CRITICO');
    expect(estadoCohesion(39).label).toBe('Alejado');
  });
});

describe('getCohesionAnimal', () => {
  beforeEach(() => get.mockReset());

  it('pide el rango como instantes ISO y devuelve el cuerpo tal cual', async () => {
    const cuerpo = { actual: null, promedioRodeo: null, promedioPeriodoAnterior: null, serie: [] };
    get.mockResolvedValue({ data: cuerpo });
    const desde = Date.UTC(2026, 9, 8, 3);
    const hasta = Date.UTC(2026, 9, 15, 2, 59);

    await expect(getCohesionAnimal(7, desde, hasta)).resolves.toBe(cuerpo);
    expect(get).toHaveBeenCalledWith('/animales/7/cohesion', {
      params: { desde: '2026-10-08T03:00:00.000Z', hasta: '2026-10-15T02:59:00.000Z' },
    });
  });
});
