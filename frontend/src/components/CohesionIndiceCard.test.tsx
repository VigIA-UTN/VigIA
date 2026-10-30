import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import CohesionIndiceCard from '../components/CohesionIndiceCard';
import type { CohesionResponse } from '../api/cohesionApi';

// ── Mocks de infraestructura ───────────────────────────────────────────────────

/**
 * ResizeObserver no existe en jsdom: se provee un stub sincrónico.
 * Necesario para el useLayoutEffect que mide la pastilla del selector de período.
 */
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
vi.stubGlobal('ResizeObserver', ResizeObserverStub);

/**
 * La Web Animations API (Element.animate) no existe en jsdom.
 * CohesionIndiceCard la usa para animar la pastilla del selector al cambiar período.
 */
Element.prototype.animate = () => ({ cancel: () => {}, finish: () => {}, play: () => {} } as unknown as Animation);

// Mock de la función que llama al backend
vi.mock('../api/cohesionApi', async () => {
  const actual = await vi.importActual<typeof import('../api/cohesionApi')>('../api/cohesionApi');
  return {
    ...actual,
    getCohesionAnimal: vi.fn(),
  };
});

// Recharts usa SVG y ResizeObserver internamente; el mock garantiza que
// ResponsiveContainer no rompa por dimensiones 0×0 en jsdom.
vi.mock('recharts', async () => {
  const actual = await vi.importActual<typeof import('recharts')>('recharts');
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="recharts-container">{children}</div>
    ),
  };
});

// ── Helpers ────────────────────────────────────────────────────────────────────

import { getCohesionAnimal } from '../api/cohesionApi';

const mockGetCohesion = vi.mocked(getCohesionAnimal);

function makeCohesionResponse(overrides: Partial<CohesionResponse> = {}): CohesionResponse {
  return {
    actual: 75,
    promedioRodeo: 70,
    promedioPeriodoAnterior: 72,
    serie: [],
    ...overrides,
  };
}

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('CohesionIndiceCard', () => {
  beforeAll(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2025-06-01T12:00:00Z').getTime());
  });
  afterAll(() => {
    vi.useRealTimers();
  });
  afterEach(() => {
    vi.clearAllMocks();
  });

  // ── Estado de calibración ──────────────────────────────────────────────────

  it('muestra mensaje de calibración cuando enCalibracion=true', () => {
    render(<CohesionIndiceCard animalId={1} enCalibracion />);
    expect(screen.getByText(/Disponible al finalizar la calibración/i)).toBeInTheDocument();
    // No llama a la API
    expect(mockGetCohesion).not.toHaveBeenCalled();
  });

  it('NO muestra el selector de período cuando enCalibracion=true', () => {
    render(<CohesionIndiceCard animalId={1} enCalibracion />);
    expect(screen.queryByRole('group', { name: /Período/i })).not.toBeInTheDocument();
  });

  // ── Estado de carga ────────────────────────────────────────────────────────

  it('muestra "Cargando" mientras espera la respuesta de la API', async () => {
    // La promesa nunca resuelve → queda en estado loading
    mockGetCohesion.mockReturnValue(new Promise(() => {}));

    render(<CohesionIndiceCard animalId={1} />);
    // El texto de carga aparece antes de que se resuelva el fetch
    expect(screen.getByText(/Cargando índice de cohesión/i)).toBeInTheDocument();
  });

  // ── Estado de error ────────────────────────────────────────────────────────

  it('muestra error de permiso (403) cuando el backend rechaza la solicitud', async () => {
    const err403 = Object.assign(new Error('Forbidden'), {
      isAxiosError: true,
      response: { status: 403 },
    });
    mockGetCohesion.mockRejectedValue(err403);

    await act(async () => {
      render(<CohesionIndiceCard animalId={2} />);
      // Permite que el efecto y la promesa rechazada se procesen
      await Promise.resolve();
    });

    expect(screen.getByText(/No tenés permiso para ver el índice de cohesión/i)).toBeInTheDocument();
  });

  it('muestra error genérico cuando la API falla por un motivo distinto a 403', async () => {
    mockGetCohesion.mockRejectedValue(new Error('Network Error'));

    await act(async () => {
      render(<CohesionIndiceCard animalId={3} />);
      await Promise.resolve();
    });

    expect(screen.getByText(/No se pudo cargar el índice de cohesión/i)).toBeInTheDocument();
  });

  // ── Sin datos ──────────────────────────────────────────────────────────────

  it('muestra "No hay datos" cuando la API devuelve actual=null', async () => {
    mockGetCohesion.mockResolvedValue(makeCohesionResponse({ actual: null }));

    await act(async () => {
      render(<CohesionIndiceCard animalId={4} />);
      await Promise.resolve();
    });

    expect(screen.getByText(/No hay datos de cohesión en el período/i)).toBeInTheDocument();
  });

  // ── Datos normales ─────────────────────────────────────────────────────────

  it('muestra el valor actual del índice de cohesión', async () => {
    mockGetCohesion.mockResolvedValue(makeCohesionResponse({ actual: 75 }));

    await act(async () => {
      render(<CohesionIndiceCard animalId={5} />);
      await Promise.resolve();
    });

    // El valor 75 se muestra en un span grande
    expect(screen.getByText('75')).toBeInTheDocument();
  });

  it('muestra el estado "Cohesionado" cuando el índice es ≥ 60', async () => {
    mockGetCohesion.mockResolvedValue(makeCohesionResponse({ actual: 80 }));

    await act(async () => {
      render(<CohesionIndiceCard animalId={6} />);
      await Promise.resolve();
    });

    expect(screen.getByText('Cohesionado')).toBeInTheDocument();
  });

  it('muestra el estado "Al límite" cuando el índice está entre 40 y 59', async () => {
    mockGetCohesion.mockResolvedValue(makeCohesionResponse({ actual: 50 }));

    await act(async () => {
      render(<CohesionIndiceCard animalId={7} />);
      await Promise.resolve();
    });

    expect(screen.getByText('Al límite')).toBeInTheDocument();
  });

  it('muestra el estado "Alejado" cuando el índice es < 40', async () => {
    mockGetCohesion.mockResolvedValue(makeCohesionResponse({ actual: 30 }));

    await act(async () => {
      render(<CohesionIndiceCard animalId={8} />);
      await Promise.resolve();
    });

    expect(screen.getByText('Alejado')).toBeInTheDocument();
  });

  it('muestra el promedio del rodeo', async () => {
    mockGetCohesion.mockResolvedValue(makeCohesionResponse({ actual: 75, promedioRodeo: 68 }));

    await act(async () => {
      render(<CohesionIndiceCard animalId={9} />);
      await Promise.resolve();
    });

    expect(screen.getByText('68')).toBeInTheDocument();
  });

  // ── Selector de período ────────────────────────────────────────────────────

  it('muestra el selector de período cuando enCalibracion es false', async () => {
    mockGetCohesion.mockResolvedValue(makeCohesionResponse());

    await act(async () => {
      render(<CohesionIndiceCard animalId={10} />);
      await Promise.resolve();
    });

    expect(screen.getByRole('group', { name: /Período/i })).toBeInTheDocument();
  });

  it('cambia el período al hacer clic en un botón del selector', async () => {
    mockGetCohesion.mockResolvedValue(makeCohesionResponse());

    await act(async () => {
      render(<CohesionIndiceCard animalId={11} />);
      await Promise.resolve();
    });

    const btn30d = screen.getByRole('button', { name: '30 d' });
    // El botón no está presionado inicialmente (el período inicial es 7d)
    expect(btn30d).toHaveAttribute('aria-pressed', 'false');

    await act(async () => {
      fireEvent.click(btn30d);
      await Promise.resolve();
    });

    expect(btn30d).toHaveAttribute('aria-pressed', 'true');
    // Debe haber llamado a la API una vez más con el nuevo período
    expect(mockGetCohesion).toHaveBeenCalledTimes(2);
  });

  // ── Delta período anterior ─────────────────────────────────────────────────

  it('muestra el delta positivo respecto al período anterior', async () => {
    mockGetCohesion.mockResolvedValue(
      makeCohesionResponse({ actual: 75, promedioPeriodoAnterior: 70 }),
    );

    await act(async () => {
      render(<CohesionIndiceCard animalId={12} />);
      await Promise.resolve();
    });

    // Delta = 75 - 70 = +5 pts
    expect(screen.getByText(/\+5 pts vs\. período anterior/i)).toBeInTheDocument();
  });
});
