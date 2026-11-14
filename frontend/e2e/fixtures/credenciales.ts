import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

export type RolUsuario = 'ADMINISTRADOR' | 'PRODUCTOR' | 'VETERINARIO' | 'CAMPERO';

export interface UsuarioFixture {
  clave: string;
  email: string;
  rol: RolUsuario;
  establecimientos: string[];
}

export interface CredencialesFixture {
  password: string;
  usuarios: UsuarioFixture[];
}

let credencialesCache: CredencialesFixture | null = null;

/**
 * Busca y carga `fixtures/escenario/credenciales.json` desde la raíz del repositorio.
 * Los tests leen las credenciales de acá, nunca las escriben en el código (convención VigIA).
 */
export function obtenerCredenciales(): CredencialesFixture {
  if (credencialesCache) {
    return credencialesCache;
  }

  // Busca hacia arriba partiendo del directorio actual o de este archivo
  let dirActual = process.cwd();
  try {
    const __filename = fileURLToPath(import.meta.url);
    dirActual = path.dirname(__filename);
  } catch {
    // Si falla import.meta.url, usa process.cwd()
  }

  let dir = dirActual;
  while (dir) {
    const candidato = path.join(dir, 'fixtures', 'escenario', 'credenciales.json');
    if (fs.existsSync(candidato)) {
      credencialesCache = JSON.parse(fs.readFileSync(candidato, 'utf-8')) as CredencialesFixture;
      return credencialesCache;
    }
    const padre = path.dirname(dir);
    if (padre === dir) break;
    dir = padre;
  }

  // Fallback directo relativo a frontend
  const fallback = path.resolve(process.cwd(), '../fixtures/escenario/credenciales.json');
  if (fs.existsSync(fallback)) {
    credencialesCache = JSON.parse(fs.readFileSync(fallback, 'utf-8')) as CredencialesFixture;
    return credencialesCache;
  }

  throw new Error('No se encontró fixtures/escenario/credenciales.json en el repositorio.');
}

/**
 * Obtiene un usuario fixture por su clave única (ej. "admin", "productorA", "productorAmbos").
 */
export function obtenerUsuarioPorClave(clave: string): UsuarioFixture {
  const creds = obtenerCredenciales();
  const usuario = creds.usuarios.find((u) => u.clave === clave);
  if (!usuario) {
    throw new Error(`No existe ningún usuario con clave "${clave}" en credenciales.json`);
  }
  return usuario;
}

/**
 * Obtiene un usuario fixture por rol (y opcionalmente por establecimiento asignado).
 * Por ejemplo, `obtenerUsuarioPorRol('PRODUCTOR')` retorna productorA por defecto.
 */
export function obtenerUsuarioPorRol(
  rol: RolUsuario | string,
  options?: { establecimiento?: string }
): UsuarioFixture {
  const creds = obtenerCredenciales();
  const usuariosRol = creds.usuarios.filter((u) => u.rol === rol);

  if (usuariosRol.length === 0) {
    throw new Error(`No se encontró ningún usuario con rol "${rol}" en credenciales.json`);
  }

  if (options?.establecimiento) {
    const encontrado = usuariosRol.find((u) =>
      u.establecimientos.includes(options.establecimiento!)
    );
    if (!encontrado) {
      throw new Error(
        `No se encontró usuario con rol "${rol}" asignado a "${options.establecimiento}"`
      );
    }
    return encontrado;
  }

  return usuariosRol[0];
}
