import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, '../..');

const limpiarPath = path.join(rootDir, 'fixtures', 'escenario', 'limpiar.sql');
const escenarioPath = path.join(rootDir, 'fixtures', 'escenario', 'escenario.sql');

if (!fs.existsSync(limpiarPath) || !fs.existsSync(escenarioPath)) {
  console.error('No se encontraron los archivos SQL de escenario en fixtures/escenario.');
  process.exit(1);
}

const limpiarSql = fs.readFileSync(limpiarPath, 'utf-8');
const escenarioSql = fs.readFileSync(escenarioPath, 'utf-8');
const combinedSql = `${limpiarSql}\n${escenarioSql}`;

console.log('Cargando fixtures de escenario en contenedor vigia-db...');
const result = spawnSync(
  'docker',
  ['exec', '-i', 'vigia-db', 'psql', '-U', 'vigia_user', '-d', 'vigia', '-v', 'ON_ERROR_STOP=1'],
  {
    input: combinedSql,
    stdio: ['pipe', 'inherit', 'inherit'],
    shell: true,
  }
);

if (result.status !== 0) {
  console.error(`Error al cargar fixtures (código de salida: ${result.status}). Asegurate de que el contenedor vigia-db esté corriendo.`);
  process.exit(result.status || 1);
}

console.log('Fixtures de escenario cargadas correctamente.');
