import { describe, expect, it, vi } from 'vitest';
import type { EstadoSaludResponse, LineaTiempoEventoResponse } from './animalesApi';
import { getHistorialSalud, getLineaTiempoAlertas } from './animalesApi';
import type { DiagnosticoResponse } from './diagnosticoApi';
import { getDiagnosticos } from './diagnosticoApi';
import axiosClient from './axiosClient';
import { construirHistorial, getHistorialClinico, registrarIntervencion } from './historialClinicoApi';

vi.mock('./animalesApi', () => ({ getLineaTiempoAlertas: vi.fn(), getHistorialSalud: vi.fn() }));
vi.mock('./diagnosticoApi', () => ({ getDiagnosticos: vi.fn() }));
vi.mock('./axiosClient', () => ({ default: { post: vi.fn() } }));

const alerta: LineaTiempoEventoResponse = {
    tipoEvento: 'ALERTA', id: 5, fecha: '2026-09-27T06:12:44.123456', tipo: 'SALUD', subtipo: 'SALUD', severidad: 'ALTA',
    estadoResolucion: 'CONFIRMADA', tipoIntervencion: null, descripcion: 'Revisar al animal', interventor: null, alertaAsociadaId: null,
};
const intervencion: LineaTiempoEventoResponse = {
    tipoEvento: 'INTERVENCION', id: 3, fecha: '2026-09-27T09:40:00', tipo: null, severidad: null, estadoResolucion: null,
    tipoIntervencion: 'TRATAMIENTO', descripcion: 'Oxitetraciclina IM', interventor: 'Juan Sosa', alertaAsociadaId: 5,
};
const estadoSalud: EstadoSaludResponse = {
    id: 2, animalId: 6, estado: 'ENFERMO', fechaRevision: '2026-09-27', diagnosticoNotas: 'Neumonía leve',
    tratamientoIndicado: null, origen: 'MANUAL', fechaRegistro: '2026-09-27T10:05:31', registradoPor: 'Laura Vet',
};
const diagnostico: DiagnosticoResponse = {
    id: 7, alertaId: 5, animalId: 6, animalAlias: 'Lola', animalCaravanaOficial: '60006', establecimientoNombre: 'La Aurora',
    diagnostico: 'Cuadro febril compatible con neumonía', tratamiento: '', fechaRegistro: '2026-09-28T11:20:00.5',
    veterinarioNombre: 'Laura Vet', disparador: 'SALUD', datoOrigen: 'Actividad 40% por debajo del perfil.',
};

describe('construirHistorial', () => {
    it('mapea una alerta con su severidad real y estado', () => {
        const [ev] = construirHistorial([alerta], [], []);
        expect(ev).toMatchObject({ id: 'a5', tipo: 'alerta', sev: 'ALTA', titulo: 'Animal en estado CRITICO', fecha: '2026-09-27T06:12', origen: 'AUTOMATICO', resumen: 'Revisar al animal' });
        expect(ev.detalles).toContainEqual(['Estado de la alerta', 'Confirmada']);
    });

    it('mapea una intervención con su tipo, autor y alerta de origen', () => {
        const [ev] = construirHistorial([intervencion], [], []);
        expect(ev).toMatchObject({ id: 'i3', tipo: 'intervencion', sub: 'TRATAMIENTO', titulo: 'Tratamiento', usuario: 'Juan Sosa', resumen: 'Oxitetraciclina IM' });
        expect(ev.fuente).toContain('#5');
    });

    it('mapea un estado de salud como diagnóstico con su estado', () => {
        const [ev] = construirHistorial([], [estadoSalud], []);
        expect(ev).toMatchObject({ id: 's2', tipo: 'diagnostico', estado: 'ENFERMO', origen: 'MANUAL', usuario: 'Laura Vet', fecha: '2026-09-27T10:05', fechaRegistro: '2026-09-27T10:05' });
        expect(ev.detalles).toContainEqual(['Tratamiento indicado', '—']);
    });

    it('un estado de salud cargado otro día queda en la fecha de revisión', () => {
        const [ev] = construirHistorial([], [{ ...estadoSalud, fechaRegistro: '2026-09-30T08:00:00' }], []);
        expect(ev).toMatchObject({ fecha: '2026-09-27T00:00', fechaRegistro: '2026-09-30T08:00' });
    });

    it('mapea un diagnóstico veterinario con su alerta y dato de origen', () => {
        const [ev] = construirHistorial([], [], [diagnostico]);
        expect(ev).toMatchObject({ id: 'd7', tipo: 'diagnostico', titulo: 'Cuadro febril compatible con neumonía', usuario: 'Laura Vet', rol: 'VETERINARIO', origen: 'MANUAL', fecha: '2026-09-28T11:20' });
        expect(ev.fuente).toContain('#5');
        expect(ev.detalles).toContainEqual(['Dato de origen de la alerta', 'Actividad 40% por debajo del perfil.']);
    });

    it('enlaza alertas, intervenciones y diagnósticos con su alerta de origen; los estados de salud no', () => {
        const porId = Object.fromEntries(construirHistorial([alerta, intervencion], [estadoSalud], [diagnostico]).map(e => [e.id, e.alertaId]));
        expect(porId).toEqual({ a5: 5, i3: 5, d7: 5, s2: undefined });
    });

    it('una intervención manual no tiene alerta que abrir', () =>
        expect(construirHistorial([{ ...intervencion, alertaAsociadaId: null }], [], [])[0].alertaId).toBeUndefined());

    it('ordena todas las fuentes de la más reciente a la más antigua', () =>
        expect(construirHistorial([alerta, intervencion], [estadoSalud], [diagnostico]).map(e => e.id))
            .toEqual(['d7', 's2', 'i3', 'a5']));
});

describe('getHistorialClinico', () => {
    it('combina alertas sanitarias + intervenciones, estados de salud y diagnósticos del animal', async () => {
        vi.mocked(getLineaTiempoAlertas).mockResolvedValue([alerta]);
        vi.mocked(getHistorialSalud).mockResolvedValue([estadoSalud]);
        vi.mocked(getDiagnosticos).mockResolvedValue([diagnostico]);
        const eventos = await getHistorialClinico(6);
        expect(getLineaTiempoAlertas).toHaveBeenCalledWith(6, { tipo: 'sanitario' });
        expect(getHistorialSalud).toHaveBeenCalledWith(6);
        expect(getDiagnosticos).toHaveBeenCalledWith({ animalId: 6 });
        expect(eventos.map(e => e.id)).toEqual(['d7', 's2', 'a5']);
    });
});

describe('registrarIntervencion', () => {
    it('registra la intervención sobre el animal y devuelve la creada', async () => {
        const creada = { id: 3, alertaId: null, animalId: 6, interventor: 'Laura Vet', descripcion: 'Ivermectina', fechaHora: '2026-10-01T09:30:00', tipo: 'TRATAMIENTO' };
        vi.mocked(axiosClient.post).mockResolvedValue({ data: creada });
        const body = { tipo: 'TRATAMIENTO' as const, fechaHora: '2026-10-01T09:30', descripcion: 'Ivermectina' };
        await expect(registrarIntervencion(6, body)).resolves.toEqual(creada);
        expect(axiosClient.post).toHaveBeenCalledWith('/animales/6/intervenciones', body);
    });
});
