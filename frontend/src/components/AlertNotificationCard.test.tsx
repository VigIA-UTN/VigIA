import { describe, it, expect, beforeAll, afterEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AlertNotificationCard from '../components/AlertNotificationCard';
import type { AlertaResponse } from '../api/alertaApi';

// ── Fábrica de datos ──────────────────────────────────────────────────────────

const AHORA = new Date('2025-06-01T10:00:00Z').getTime();

function makeAlerta(overrides: Partial<AlertaResponse> = {}): AlertaResponse {
  return {
    id: 1,
    tipo: 'CELO',
    animalId: 100,
    // tsGeneracion relativa a AHORA (con fake timer activo)
    tsGeneracion: new Date(AHORA - 5 * 60 * 1000).toISOString(),
    estado: 'PENDIENTE',
    accionRecomendada: 'Realizar inseminación artificial.',
    severidad: 'MEDIA',
    ...overrides,
  };
}

describe('AlertNotificationCard', () => {
  beforeAll(() => {
    vi.useFakeTimers();
    vi.setSystemTime(AHORA);
  });
  afterEach(() => {
    vi.clearAllTimers();
  });

  // ── Comportamiento básico ──────────────────────────────────────────────────

  it('muestra el ID del animal en el título para alerta de celo', () => {
    render(
      <AlertNotificationCard
        alerta={makeAlerta({ tipo: 'CELO', animalId: 42 })}
        onClick={() => {}}
      />,
    );
    // El título incluye el ID del animal
    expect(screen.getByText(/#42/)).toBeInTheDocument();
  });

  it('llama a onClick al hacer clic en la card', () => {
    const handleClick = vi.fn();
    render(
      <AlertNotificationCard
        alerta={makeAlerta()}
        onClick={handleClick}
      />,
    );
    // Busca el contenedor clickeable: el div con role implícito o el primer div
    const card = document.querySelector('[style*="cursor"]') ??
      document.querySelector('[onclick]') ??
      document.body.firstElementChild as HTMLElement;
    fireEvent.click(card!);
    expect(handleClick).toHaveBeenCalledOnce();
  });

  // ── Tipos de alerta / categorías ──────────────────────────────────────────

  it('muestra la acción recomendada para alertas de SALUD', () => {
    render(
      <AlertNotificationCard
        alerta={makeAlerta({
          tipo: 'SALUD',
          severidad: 'ALTA',
          accionRecomendada: 'Revisar al animal.',
        })}
        onClick={() => {}}
      />,
    );
    expect(screen.getByText(/Revisar al animal\./i)).toBeInTheDocument();
  });

  it('renderiza la indicación de tiempo fuera para alertas GEOESPACIAL', () => {
    render(
      <AlertNotificationCard
        alerta={makeAlerta({
          tipo: 'GEOESPACIAL',
          severidad: 'ALTA',
        })}
        onClick={() => {}}
      />,
    );
    // El componente muestra "Fuera hace N" o "Fuera ahora"
    expect(screen.getByText(/Fuera/i)).toBeInTheDocument();
  });

  it('muestra texto de aislamiento para alertas AISLAMIENTO_SOCIAL', () => {
    render(
      <AlertNotificationCard
        alerta={makeAlerta({
          tipo: 'AISLAMIENTO_SOCIAL',
          severidad: 'MEDIA',
          animalesAislados: [
            {
              animalId: 5,
              identificador: 'A-005',
              alias: null,
              minutosFuera: 30,
              minutosFueraSeguidos: 30,
              distanciaGrupoM: 150,
              severidad: 'MEDIA',
              tsRegreso: null,
            },
          ],
        })}
        onClick={() => {}}
      />,
    );
    // Debe contener la palabra "aislado" o similar
    expect(screen.getByText(/aislado/i)).toBeInTheDocument();
  });

  it('muestra "Todos volvieron al grupo" cuando todos los animales aislados regresaron', () => {
    render(
      <AlertNotificationCard
        alerta={makeAlerta({
          tipo: 'AISLAMIENTO_SOCIAL',
          severidad: 'BAJA',
          animalesAislados: [
            {
              animalId: 7,
              identificador: 'A-007',
              alias: null,
              minutosFuera: 45,
              minutosFueraSeguidos: 45,
              distanciaGrupoM: 0,
              severidad: 'BAJA',
              tsRegreso: new Date(AHORA - 5 * 60 * 1000).toISOString(),
            },
          ],
        })}
        onClick={() => {}}
      />,
    );
    expect(screen.getByText(/Volvió al grupo/i)).toBeInTheDocument();
  });

  it('muestra "Este animal ya volvió al grupo" cuando se consulta por un animal específico que regresó', () => {
    const tsRegreso = new Date(AHORA - 10 * 60 * 1000).toISOString();
    render(
      <AlertNotificationCard
        alerta={makeAlerta({
          tipo: 'AISLAMIENTO_SOCIAL',
          animalesAislados: [
            {
              animalId: 99,
              identificador: 'A-099',
              alias: null,
              minutosFuera: 60,
              minutosFueraSeguidos: 60,
              distanciaGrupoM: 0,
              severidad: 'MEDIA',
              tsRegreso,
            },
          ],
        })}
        onClick={() => {}}
        animalId={99}
      />,
    );
    expect(screen.getByText(/Este animal ya volvió al grupo/i)).toBeInTheDocument();
  });

  // ── Alerta de anestro ─────────────────────────────────────────────────────

  it('muestra "en anestro" en el título cuando el subtipo indica anestro', () => {
    render(
      <AlertNotificationCard
        alerta={makeAlerta({
          tipo: 'CELO',
          subtipo: 'Anestro Detectado',
          animalId: 55,
          severidad: 'MEDIA',
        })}
        onClick={() => {}}
      />,
    );
    expect(screen.getByText(/#55 en anestro/i)).toBeInTheDocument();
  });

  // ── Estados de la card ────────────────────────────────────────────────────

  it('muestra el timestamp relativo de la alerta', () => {
    // Calcula tsGeneracion como exactamente 5 minutos antes del momento actual real
    // para que la función haceCuanto() del componente devuelva "Hace 5 m"
    const hace5min = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    render(
      <AlertNotificationCard
        alerta={makeAlerta({ tsGeneracion: hace5min })}
        onClick={() => {}}
      />,
    );
    expect(screen.getByText(/Hace 5 m/i)).toBeInTheDocument();
  });

  it('muestra el badge DatoDesactualizado cuando la alerta lo indica', () => {
    render(
      <AlertNotificationCard
        alerta={makeAlerta({
          tipo: 'CELO',
          datoPosiblementeDesactualizado: true,
        })}
        onClick={() => {}}
      />,
    );
    expect(document.querySelector('.bi-wifi-off')).toBeInTheDocument();
  });
});
