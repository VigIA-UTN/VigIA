// Mock en memoria — no hay endpoint de backend todavía. La forma de las
// funciones espeja lo que sería un cliente HTTP real, para que conectarlas
// más adelante sea un cambio contenido a este archivo.

export interface DiagnosticoResponse {
  id: number;
  alertaId: number;
  animalId: number;
  animalAlias: string;
  animalCaravanaOficial: string;
  establecimientoNombre: string;
  diagnostico: string;
  tratamiento: string;
  fechaRegistro: string; // ISO 8601
  veterinarioNombre: string;
}

let nextId = 1000;

const mockDiagnosticos: DiagnosticoResponse[] = [
  {
    id: nextId++,
    alertaId: 4821,
    animalId: 12345,
    animalAlias: 'La Vaca Lola',
    animalCaravanaOficial: '#12345',
    establecimientoNombre: 'Tres Arroyos',
    diagnostico: 'Cuadro febril compatible con neumonía',
    tratamiento: 'Oxitetraciclina LA 20 mg/kg IM, dosis única. Controlar temperatura en 48 h.',
    fechaRegistro: '2026-09-26T11:20:00',
    veterinarioNombre: 'Dra. Paula Ferreyra',
  },
  {
    id: nextId++,
    alertaId: 4821,
    animalId: 12345,
    animalAlias: 'La Vaca Lola',
    animalCaravanaOficial: '#12345',
    establecimientoNombre: 'Tres Arroyos',
    diagnostico: 'Control a las 48 h: evolución favorable',
    tratamiento: 'Sin nueva medicación. Mantener en potrero cercano hasta el alta.',
    fechaRegistro: '2026-09-28T09:05:00',
    veterinarioNombre: 'Dra. Paula Ferreyra',
  },
  {
    id: nextId++,
    alertaId: 4790,
    animalId: 44831,
    animalAlias: 'Rubia',
    animalCaravanaOficial: '#F4831',
    establecimientoNombre: 'La Esperanza',
    diagnostico: 'Mastitis clínica en cuarto posterior',
    tratamiento: 'Intramamario con cefalexina cada 12 h por 3 días.',
    fechaRegistro: '2026-09-24T10:15:00',
    veterinarioNombre: 'Dr. Martín Aguirre',
  },
  {
    id: nextId++,
    alertaId: 4755,
    animalId: 44880,
    animalAlias: 'Colorada',
    animalCaravanaOficial: '#F4880',
    establecimientoNombre: 'El Ombú',
    diagnostico: 'Queratoconjuntivitis',
    tratamiento: 'Oxitetraciclina tópica, aislar del rodeo 5 días.',
    fechaRegistro: '2026-09-21T08:40:00',
    veterinarioNombre: 'Dra. Paula Ferreyra',
  },
  {
    id: nextId++,
    alertaId: 4731,
    animalId: 44844,
    animalAlias: 'Negra',
    animalCaravanaOficial: '#F4844',
    establecimientoNombre: 'La Esperanza',
    diagnostico: 'Pietín en miembro anterior',
    tratamiento: 'Pediluvio con sulfato de cobre, revisar en 7 días.',
    fechaRegistro: '2026-09-18T14:00:00',
    veterinarioNombre: 'Dr. Martín Aguirre',
  },
];

export function getDiagnosticosPorAnimal(animalId: number): DiagnosticoResponse[] {
  return mockDiagnosticos
    .filter((d) => d.animalId === animalId)
    .sort((a, b) => new Date(b.fechaRegistro).getTime() - new Date(a.fechaRegistro).getTime());
}

export function getDiagnosticos(filters?: { search?: string; establecimiento?: string }): DiagnosticoResponse[] {
  const q = filters?.search?.trim().toLowerCase() ?? '';
  const est = filters?.establecimiento;
  return mockDiagnosticos
    .filter((d) => !est || est === 'Todos los establecimientos' || d.establecimientoNombre === est)
    .filter((d) => !q || d.animalAlias.toLowerCase().includes(q) || d.animalCaravanaOficial.toLowerCase().includes(q))
    .sort((a, b) => new Date(b.fechaRegistro).getTime() - new Date(a.fechaRegistro).getTime());
}

export function crearDiagnostico(input: Omit<DiagnosticoResponse, 'id' | 'fechaRegistro'>): DiagnosticoResponse {
  const nuevo: DiagnosticoResponse = {
    ...input,
    id: nextId++,
    fechaRegistro: new Date().toISOString(),
  };
  mockDiagnosticos.unshift(nuevo);
  return nuevo;
}
