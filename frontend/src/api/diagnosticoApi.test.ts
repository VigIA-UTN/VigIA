import { beforeEach, describe, expect, it, vi } from 'vitest';
import axiosClient from './axiosClient';
import type { DiagnosticoResponse } from './diagnosticoApi';
import { crearDiagnostico, filtrarDiagnosticos, getDiagnosticos, mensajeErrorDiagnostico } from './diagnosticoApi';

vi.mock('./axiosClient', () => ({ default: { get: vi.fn(), post: vi.fn() } }));

const get = vi.mocked(axiosClient.get);
const post = vi.mocked(axiosClient.post);

describe('getDiagnosticos', () => {
    beforeEach(() => get.mockReset());

    it('pide el historial de un animal al endpoint de diagnósticos', async () => {
        get.mockResolvedValue({ data: [{ id: 1 }] });
        await expect(getDiagnosticos({ animalId: 6 })).resolves.toEqual([{ id: 1 }]);
        expect(get).toHaveBeenCalledWith('/alertas/diagnosticos', { params: { animalId: 6 } });
    });

    it('sin filtros pide todos los diagnósticos visibles', async () => {
        get.mockResolvedValue({ data: [] });
        await getDiagnosticos();
        expect(get).toHaveBeenCalledWith('/alertas/diagnosticos', { params: undefined });
    });
});

describe('crearDiagnostico', () => {
    it('registra el diagnóstico sobre la alerta y devuelve el creado', async () => {
        post.mockResolvedValue({ data: { id: 7, alertaId: 2 } });
        const body = { diagnostico: 'Neumonía leve', tratamiento: 'Reposo' };
        await expect(crearDiagnostico(2, body)).resolves.toEqual({ id: 7, alertaId: 2 });
        expect(post).toHaveBeenCalledWith('/alertas/2/diagnosticos', body);
    });
});

describe('mensajeErrorDiagnostico', () => {
    it('usa el texto plano que devuelve el backend', () =>
        expect(mensajeErrorDiagnostico({ response: { data: 'La alerta debe estar confirmada.' } }))
            .toBe('La alerta debe estar confirmada.'));
    it('usa message si el backend devuelve un objeto', () =>
        expect(mensajeErrorDiagnostico({ response: { data: { message: 'Algo falló' } } })).toBe('Algo falló'));
    it('cae a un mensaje genérico (403 de Spring sin message, error de red)', () => {
        expect(mensajeErrorDiagnostico({ response: { data: { error: 'Forbidden' } } }))
            .toBe('No se pudo registrar el diagnóstico.');
        expect(mensajeErrorDiagnostico(new Error('Network Error'))).toBe('No se pudo registrar el diagnóstico.');
    });
});

describe('filtrarDiagnosticos', () => {
    const fila = (id: number, animalAlias: string, animalCaravanaOficial: string, establecimientoNombre: string) =>
        ({ id, animalAlias, animalCaravanaOficial, establecimientoNombre }) as DiagnosticoResponse;
    const filas = [
        fila(1, 'Lola', '60006', 'La Aurora'),
        fila(2, 'Rubia', 'F4831', 'El Ombú'),
        fila(3, null as unknown as string, '70007', 'La Aurora'),
    ];

    it('sin filtros devuelve todo', () =>
        expect(filtrarDiagnosticos(filas, { search: '', establecimiento: 'Todos los establecimientos' })).toHaveLength(3));
    it('busca por alias sin importar mayúsculas', () =>
        expect(filtrarDiagnosticos(filas, { search: 'lOLa', establecimiento: 'Todos los establecimientos' }).map((d) => d.id)).toEqual([1]));
    it('busca por caravana', () =>
        expect(filtrarDiagnosticos(filas, { search: 'f48', establecimiento: 'Todos los establecimientos' }).map((d) => d.id)).toEqual([2]));
    it('filtra por establecimiento', () =>
        expect(filtrarDiagnosticos(filas, { search: '', establecimiento: 'La Aurora' }).map((d) => d.id)).toEqual([1, 3]));
    it('tolera animales sin alias', () =>
        expect(filtrarDiagnosticos(filas, { search: '70007', establecimiento: 'La Aurora' }).map((d) => d.id)).toEqual([3]));
});
