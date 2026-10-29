import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import DatoDesactualizadoBadge from '../components/DatoDesactualizadoBadge';
import type { AlertaResponse } from '../api/alertaApi';

/**
 * Fábrica mínima de AlertaResponse para los tests.
 */
function makeAlerta(overrides: Partial<AlertaResponse> = {}): AlertaResponse {
  return {
    id: 1,
    tipo: 'CELO',
    animalId: 42,
    tsGeneracion: new Date().toISOString(),
    estado: 'PENDIENTE',
    accionRecomendada: 'Realizar inseminación.',
    ...overrides,
  };
}

describe('DatoDesactualizadoBadge', () => {
  it('no renderiza nada cuando la alerta no tiene dato desactualizado', () => {
    const { container } = render(
      <DatoDesactualizadoBadge alerta={makeAlerta()} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('no renderiza nada si el estado es CONFIRMADA aunque haya ultima señal vieja', () => {
    // Señal antigua (hace 5 horas), umbral 2 horas, pero estado CONFIRMADA → no se muestra
    const hace5h = new Date(Date.now() - 5 * 60 * 60 * 1000).toISOString();
    const { container } = render(
      <DatoDesactualizadoBadge
        alerta={makeAlerta({
          estado: 'CONFIRMADA',
          caravanaUltimaSenial: hace5h,
          horasUmbralDatoDesactualizado: 2,
        })}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renderiza el badge cuando datoPosiblementeDesactualizado=true', () => {
    render(
      <DatoDesactualizadoBadge
        alerta={makeAlerta({ datoPosiblementeDesactualizado: true })}
      />,
    );
    // El badge siempre muestra el ícono bi-wifi-off
    expect(document.querySelector('.bi-wifi-off')).toBeInTheDocument();
  });

  it('renderiza "Sin transmisiones de la caravana" cuando no hay ultima señal', () => {
    render(
      <DatoDesactualizadoBadge
        alerta={makeAlerta({ datoPosiblementeDesactualizado: true, caravanaUltimaSenial: null })}
      />,
    );
    expect(screen.getByText(/Sin transmisiones de la caravana/i)).toBeInTheDocument();
  });

  it('calcula dinámicamente que el dato está desactualizado si la señal supera el umbral', () => {
    // Última señal hace 3 horas, umbral 2 horas → debe mostrarse
    const hace3h = new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString();
    render(
      <DatoDesactualizadoBadge
        alerta={makeAlerta({
          estado: 'PENDIENTE',
          caravanaUltimaSenial: hace3h,
          horasUmbralDatoDesactualizado: 2,
        })}
      />,
    );
    expect(document.querySelector('.bi-wifi-off')).toBeInTheDocument();
  });

  it('NO muestra badge si la señal está dentro del umbral', () => {
    // Última señal hace 30 minutos, umbral 2 horas → no debe mostrarse
    const hace30min = new Date(Date.now() - 30 * 60 * 1000).toISOString();
    const { container } = render(
      <DatoDesactualizadoBadge
        alerta={makeAlerta({
          estado: 'PENDIENTE',
          caravanaUltimaSenial: hace30min,
          horasUmbralDatoDesactualizado: 2,
        })}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('el título del badge menciona el umbral de horas configurado', () => {
    render(
      <DatoDesactualizadoBadge
        alerta={makeAlerta({ datoPosiblementeDesactualizado: true, horasUmbralDatoDesactualizado: 4 })}
      />,
    );
    const badge = document.querySelector('[title]');
    expect(badge?.getAttribute('title')).toContain('4 h');
  });
});
