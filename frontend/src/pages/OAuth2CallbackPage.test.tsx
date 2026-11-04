import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import OAuth2CallbackPage from './OAuth2CallbackPage';
import { resetAuthStore } from '../test/mocks/zustand';

function Destino() {
  return <p data-testid="destino">{useLocation().pathname}</p>;
}

function destinoDelCallback(query: string) {
  render(
    <MemoryRouter initialEntries={[`/oauth2/callback?${query}`]}>
      <Routes>
        <Route path="/oauth2/callback" element={<OAuth2CallbackPage />} />
        <Route path="*" element={<Destino />} />
      </Routes>
    </MemoryRouter>,
  );
  return screen.getByTestId('destino').textContent;
}

describe('OAuth2CallbackPage', () => {
  beforeEach(() => {
    resetAuthStore();
  });

  it.each([
    ['ADMINISTRADOR', '/establecimientos'],
    ['PRODUCTOR', '/dashboard'],
    ['VETERINARIO', '/dashboard'],
    ['CAMPERO', '/dashboard'],
    ['PENDIENTE', '/invitaciones'],
  ])('H0014-07 - SSO - %s entra a %s', (rol, destino) => {
    expect(destinoDelCallback(`token=fake-token&email=usuario@test.com&rol=${rol}`)).toBe(destino);
  });

  it('H0014-07 - SSO - Un error del proveedor vuelve al login', () => {
    expect(destinoDelCallback('error=sin-invitacion')).toBe('/login');
  });
});
