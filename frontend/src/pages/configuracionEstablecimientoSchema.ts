import { z } from 'zod';

export const DEFAULT_DIAS = 45;
export const DEFAULT_SEVERIDAD = 'MEDIA';

// Mismos valores que EstablecimientoServiceImpl (UMBRAL_AISLAMIENTO_MIN/MAX/DEFAULT) y ml-geo.
export const AISLAMIENTO_MIN = 10;
export const AISLAMIENTO_MAX = 1000;
export const DEFAULT_AISLAMIENTO = 100;

export const schema = z.object({
  nombre: z
    .string()
    .trim()
    .min(1, 'El nombre es obligatorio')
    .min(3, 'Debe tener al menos 3 caracteres'),
  latitud: z
    .number({ message: 'Ingresá una latitud válida' })
    .min(-90, 'Debe estar entre -90 y 90')
    .max(90, 'Debe estar entre -90 y 90'),
  longitud: z
    .number({ message: 'Ingresá una longitud válida' })
    .min(-180, 'Debe estar entre -180 y 180')
    .max(180, 'Debe estar entre -180 y 180'),
  diasDeteccionAnestro: z
    .number({ message: 'Debe ser un número entero' })
    .int('Debe ser un número entero')
    .min(1, 'Debe ser al menos 1 día')
    .max(365, 'No puede superar los 365 días'),
  severidadAnestro: z.enum(['BAJA', 'MEDIA', 'ALTA']),
  umbralAislamientoM: z
    .number({ message: 'Debe ser un número entero' })
    .int('Debe ser un número entero')
    .min(AISLAMIENTO_MIN, `Debe ser al menos ${AISLAMIENTO_MIN} metros`)
    .max(AISLAMIENTO_MAX, `No puede superar los ${AISLAMIENTO_MAX} metros`),
});

export type FormData = z.infer<typeof schema>;

// guardable: false → la sección todavía no tiene integración con el backend (se edita pero no se envía).
export const SECCIONES = [
  { id: 'general', label: 'General', icon: 'bi-building', fields: ['nombre'], guardable: false },
  { id: 'ubicacion', label: 'Ubicación', icon: 'bi-geo-alt', fields: ['latitud', 'longitud'], guardable: false },
  { id: 'anestro', label: 'Anestro', icon: 'bi-calendar-check', fields: ['diasDeteccionAnestro', 'severidadAnestro'], guardable: true },
  { id: 'aislamiento', label: 'Aislamiento', icon: 'bi-bounding-box-circles', fields: ['umbralAislamientoM'], guardable: true },
] as const satisfies readonly { id: string; label: string; icon: string; fields: readonly (keyof FormData)[]; guardable: boolean }[];

export type SeccionId = (typeof SECCIONES)[number]['id'];
