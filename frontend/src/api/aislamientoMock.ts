import type { AlertaDetalleResponse, AlertaResponse, AnimalAislado } from './alertaApi';

// TODO backend: mock de la alerta AISLAMIENTO_SOCIAL (solo en DEV). Borrar cuando el backend la emita.
// Los animalId apuntan a ids de prueba; ajustarlos a animales existentes en tu base local.

const ANIMALES: AnimalAislado[] = [
  { animalId: 1, identificador: '12345', alias: 'La Vaca Lola', minutosFuera: 135, distanciaGrupoM: 340, severidad: 'ALTA' },
  { animalId: 2, identificador: 'F4830', alias: null, minutosFuera: 95, distanciaGrupoM: 210, severidad: 'ALTA' },
  { animalId: 3, identificador: '10872', alias: 'Manchada', minutosFuera: 50, distanciaGrupoM: 160, severidad: 'MEDIA' },
  { animalId: 4, identificador: '11203', alias: null, minutosFuera: 35, distanciaGrupoM: 140, severidad: 'MEDIA' },
  { animalId: 5, identificador: 'F4777', alias: 'Tres Arroyos 4', minutosFuera: 25, distanciaGrupoM: 120, severidad: 'MEDIA' },
];

const tsGeneracion = new Date(Date.now() - 12 * 60_000).toISOString();

export const MOCK_ALERTA_AISLAMIENTO: AlertaResponse = {
  id: -1,
  tipo: 'AISLAMIENTO_SOCIAL',
  animalId: ANIMALES[0].animalId,
  tsGeneracion,
  estado: 'PENDIENTE',
  severidad: 'ALTA',
  accionRecomendada: 'Revisar a los animales alejados del rodeo.',
  animalesAislados: ANIMALES,
};

export const MOCK_DETALLE_AISLAMIENTO: AlertaDetalleResponse = {
  id: -1,
  animalId: ANIMALES[0].animalId,
  animalCaravanaOficial: ANIMALES[0].identificador,
  animalAlias: ANIMALES[0].alias ?? '',
  animalRaza: '',
  tsGeneracion,
  ventanaOptimaHoras: '',
  ventanaInicioIns: '',
  ventanaFinIns: '',
  datoDisparador: 'AISLAMIENTO_SOCIAL',
  estadoActual: 'PENDIENTE',
  accionRecomendada: 'Recorrer la zona y verificar el estado de los animales alejados.',
  descripcionDatoOrigen: 'La posición de estos animales se mantuvo lejos del grupo principal del rodeo durante más tiempo que lo habitual.',
  detalleAislamiento: {
    animalesAislados: ANIMALES,
    tsInicio: new Date(Date.now() - 135 * 60_000).toISOString(),
    nombreLote: 'Lote Norte',
  },
};
