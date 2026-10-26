import { describe, expect, it } from 'vitest';
import { schema } from './configuracionEstablecimientoSchema';

const VALIDO = {
  nombre: 'La Esperanza',
  latitud: -37.38,
  longitud: -60.28,
  diasDeteccionAnestro: 45,
  severidadAnestro: 'MEDIA',
  umbralAislamientoM: 100,
};

const errorEn = (campo: keyof typeof VALIDO, valor: unknown) => {
  const r = schema.safeParse({ ...VALIDO, [campo]: valor });
  return r.success ? null : r.error.issues[0].message;
};

describe('schema de configuración del establecimiento', () => {
  it('acepta los valores por defecto', () => {
    expect(schema.safeParse(VALIDO).success).toBe(true);
  });

  it('umbral de aislamiento: entero entre 10 y 1000 metros', () => {
    expect(errorEn('umbralAislamientoM', 10)).toBeNull();
    expect(errorEn('umbralAislamientoM', 1000)).toBeNull();
    expect(errorEn('umbralAislamientoM', 9)).toBe('Debe ser al menos 10 metros');
    expect(errorEn('umbralAislamientoM', 1001)).toBe('No puede superar los 1000 metros');
    expect(errorEn('umbralAislamientoM', 10.5)).toBe('Debe ser un número entero');
    expect(errorEn('umbralAislamientoM', NaN)).toBe('Debe ser un número entero');
  });

  it('días de anestro: entero entre 1 y 365', () => {
    expect(errorEn('diasDeteccionAnestro', 1)).toBeNull();
    expect(errorEn('diasDeteccionAnestro', 365)).toBeNull();
    expect(errorEn('diasDeteccionAnestro', 0)).toBe('Debe ser al menos 1 día');
    expect(errorEn('diasDeteccionAnestro', 366)).toBe('No puede superar los 365 días');
  });

  it('nombre y coordenadas', () => {
    expect(errorEn('nombre', '  ')).toBe('El nombre es obligatorio');
    expect(errorEn('nombre', 'ab')).toBe('Debe tener al menos 3 caracteres');
    expect(errorEn('latitud', 91)).toBe('Debe estar entre -90 y 90');
    expect(errorEn('longitud', -181)).toBe('Debe estar entre -180 y 180');
    expect(errorEn('latitud', NaN)).toBe('Ingresá una latitud válida');
  });
});
