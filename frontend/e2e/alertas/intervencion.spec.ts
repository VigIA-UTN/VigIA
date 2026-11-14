import { test, expect } from '@playwright/test';
import {
  abrirDetalleAlerta,
  abrirDetalleDesdePanel,
  animalJoven,
  crearAlerta,
  email,
  intervencionEnBase,
  intervenirPorApi,
  loginPorClave,
  resolucionEnBase,
} from '../helpers';

/**
 * H0021 - Registrar una intervención asociada a una alerta. El formulario se abre al confirmar un celo con "Confirmar
 * celo e inseminación": la intervención es opcional, pero con tipo y descripción obligatorios si se registra.
 */
test.describe('H0021 - Registrar intervención asociada a alerta (UI)', () => {
  // El histórico y el panel cargan todas las alertas pendientes del establecimiento: con la base cargada de otros specs
  // superan los 30 s por defecto.
  test.describe.configure({ timeout: 60_000 });

  test('H0021-05 - Confirmar el celo y registrar la inseminación desde el panel del mapa', async ({ page, request }) => {
    const animal = await animalJoven(request);
    const alertaId = crearAlerta(animal.id, 'CELO');
    await loginPorClave(page, 'veterinarioA');

    await abrirDetalleDesdePanel(page, animal.id);
    await page.getByRole('button', { name: /Confirmar celo e inseminación/ }).click();

    // El formulario abre con el tipo de una confirmación de celo: Inseminación, que no se puede cambiar.
    await expect(page.getByRole('heading', { name: 'Registrar intervención' })).toBeVisible();
    await expect(page.getByRole('combobox').filter({ hasText: 'Inseminación' })).toBeDisabled();
    await page.getByPlaceholder(/Describí la intervención realizada/).fill('IA con semen Angus, un pajuela');
    await page.getByRole('button', { name: 'Registrar intervención' }).click();

    await expect.poll(() => intervencionEnBase(alertaId).cantidad).toBe(1);
    expect(intervencionEnBase(alertaId)).toEqual({
      tipo: 'INSEMINACION',
      descripcion: 'IA con semen Angus, un pajuela',
      interventor: email('veterinarioA'),
      cantidad: 1,
    });
    expect(resolucionEnBase(alertaId).estado).toBe('CONFIRMADA');
    // El formulario se cierra al registrar.
    await expect(page.getByRole('heading', { name: 'Registrar intervención' })).toHaveCount(0);
  });

  test('H0021-06 - La descripción es obligatoria y registrar es opcional', async ({ page, request }) => {
    const [conDescripcion, sinRegistrar] = [await animalJoven(request), await animalJoven(request)];
    const [alertaA, alertaB] = [crearAlerta(conDescripcion.id, 'CELO'), crearAlerta(sinRegistrar.id, 'CELO')];
    await loginPorClave(page, 'productorA');

    await abrirDetalleAlerta(page, conDescripcion.id);
    await page.getByRole('button', { name: /Confirmar celo e inseminación/ }).click();
    const registrar = page.getByRole('button', { name: 'Registrar intervención' });
    const descripcion = page.getByPlaceholder(/Describí la intervención realizada/);

    // Sin descripción, o con solo espacios, no se puede registrar.
    await expect(registrar).toBeDisabled();
    await descripcion.fill('    ');
    await expect(registrar).toBeDisabled();
    await descripcion.fill('Inseminación a las 7 h');
    await expect(registrar).toBeEnabled();
    await registrar.click();
    await expect.poll(() => intervencionEnBase(alertaA).cantidad).toBe(1);

    // Cerrar el formulario sin completar deja la alerta confirmada y sin intervención.
    await abrirDetalleAlerta(page, sinRegistrar.id);
    await page.getByRole('button', { name: /Confirmar celo e inseminación/ }).click();
    await expect(page.getByRole('heading', { name: 'Registrar intervención' })).toBeVisible();
    await page.getByRole('button', { name: /Cancelar/ }).click();
    await expect(page.getByRole('heading', { name: 'Registrar intervención' })).toHaveCount(0);
    expect(resolucionEnBase(alertaB).estado).toBe('CONFIRMADA');
    expect(intervencionEnBase(alertaB).cantidad).toBe(0);
  });

  test('H0021-07 - Si la alerta ya tiene intervención, el formulario muestra el error del servidor', async ({
    page,
    request,
  }) => {
    const animal = await animalJoven(request);
    const alertaId = crearAlerta(animal.id, 'CELO');
    await loginPorClave(page, 'veterinarioA');
    await abrirDetalleAlerta(page, animal.id);
    await page.getByRole('button', { name: /Confirmar celo e inseminación/ }).click();
    await expect(page.getByRole('heading', { name: 'Registrar intervención' })).toBeVisible();

    // Mientras el formulario está abierto, otra persona registra la intervención de esa alerta.
    await intervenirPorApi(request, alertaId, { tipo: 'OTRO', descripcion: 'La registró el productor' }, 'productorA');

    await page.getByPlaceholder(/Describí la intervención realizada/).fill('Mi inseminación');
    await page.getByRole('button', { name: 'Registrar intervención' }).click();

    await expect(page.getByText('Ya existe una intervención registrada para esta alerta.')).toBeVisible();
    // El formulario sigue abierto y la intervención guardada es la primera.
    await expect(page.getByRole('heading', { name: 'Registrar intervención' })).toBeVisible();
    expect(intervencionEnBase(alertaId)).toMatchObject({ descripcion: 'La registró el productor', cantidad: 1 });
  });
});
