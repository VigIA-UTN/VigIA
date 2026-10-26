import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AlertaResponse } from './alertaApi';
import { suscribirAlertas } from './alertaStream';

class FakeEventSource {
    static readonly CLOSED = 2;
    static instancias: FakeEventSource[] = [];
    readyState = 1;
    onmessage: ((e: { data: string }) => void) | null = null;
    onopen: (() => void) | null = null;
    onerror: ((e: unknown) => void) | null = null;
    cerrada = false;
    constructor(public url: string) {
        FakeEventSource.instancias.push(this);
    }
    close() {
        this.cerrada = true;
        this.readyState = FakeEventSource.CLOSED;
    }
}

const alerta = { id: 7, tipo: 'CELO', animalId: 1, tsGeneracion: '', estado: 'PENDIENTE', accionRecomendada: '' } as AlertaResponse;

beforeEach(() => {
    FakeEventSource.instancias = [];
    vi.stubGlobal('EventSource', FakeEventSource);
    vi.spyOn(console, 'error').mockImplementation(() => {});
});
afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('suscribirAlertas', () => {
    it('comparte una sola conexión entre suscriptores del mismo establecimiento', () => {
        const a = vi.fn();
        const b = vi.fn();
        const bajaA = suscribirAlertas(1, 'tok', { onAlerta: a });
        const bajaB = suscribirAlertas(1, 'tok', { onAlerta: b });

        expect(FakeEventSource.instancias).toHaveLength(1);
        FakeEventSource.instancias[0].onmessage?.({ data: JSON.stringify(alerta) });
        expect(a).toHaveBeenCalledWith(alerta);
        expect(b).toHaveBeenCalledWith(alerta);
        bajaA();
        bajaB();
    });

    it('cierra la conexión recién con el último suscriptor', () => {
        const bajaA = suscribirAlertas(1, 'tok', { onAlerta: vi.fn() });
        const bajaB = suscribirAlertas(1, 'tok', { onAlerta: vi.fn() });
        const [source] = FakeEventSource.instancias;

        bajaA();
        expect(source.cerrada).toBe(false);
        bajaB();
        expect(source.cerrada).toBe(true);

        // El próximo suscriptor abre una nueva
        const bajaC = suscribirAlertas(1, 'tok', { onAlerta: vi.fn() });
        expect(FakeEventSource.instancias).toHaveLength(2);
        bajaC();
    });

    it('otro establecimiento u otro token usan su propia conexión', () => {
        const bajas = [
            suscribirAlertas(1, 'tok', { onAlerta: vi.fn() }),
            suscribirAlertas(2, 'tok', { onAlerta: vi.fn() }),
            suscribirAlertas(1, 'otro', { onAlerta: vi.fn() }),
        ];
        expect(FakeEventSource.instancias).toHaveLength(3);
        bajas.forEach((baja) => baja());
    });

    it('avisa la reconexión solo después de un corte', () => {
        const onReconectado = vi.fn();
        const baja = suscribirAlertas(1, 'tok', { onAlerta: vi.fn(), onReconectado });
        const [source] = FakeEventSource.instancias;

        source.onopen?.();
        expect(onReconectado).not.toHaveBeenCalled();
        source.onerror?.(new Event('error'));
        source.onopen?.();
        expect(onReconectado).toHaveBeenCalledTimes(1);
        baja();
    });

    it('si un suscriptor falla, los demás igual reciben la alerta', () => {
        const b = vi.fn();
        const bajaA = suscribirAlertas(1, 'tok', { onAlerta: () => { throw new Error('boom'); } });
        const bajaB = suscribirAlertas(1, 'tok', { onAlerta: b });

        FakeEventSource.instancias[0].onmessage?.({ data: JSON.stringify(alerta) });
        expect(b).toHaveBeenCalledWith(alerta);
        bajaA();
        bajaB();
    });

    it('una conexión que quedó CLOSED no se reutiliza', () => {
        const bajaA = suscribirAlertas(1, 'tok', { onAlerta: vi.fn() });
        const [source] = FakeEventSource.instancias;
        source.readyState = FakeEventSource.CLOSED;
        source.onerror?.(new Event('error'));

        const bajaB = suscribirAlertas(1, 'tok', { onAlerta: vi.fn() });
        expect(FakeEventSource.instancias).toHaveLength(2);
        bajaA();
        bajaB();
    });
});
