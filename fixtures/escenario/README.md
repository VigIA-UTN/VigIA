# Escenario de pruebas (aislamiento, RBAC y E2E)

Un set de datos chico y determinista, siempre igual, para las pruebas que necesitan saber de antemano qué hay en la base.
Es independiente del seed de demo (`DataInitializer`): no lo usa ni lo reemplaza, y tampoco lo toca.

| Archivo | Qué es |
|---|---|
| `escenario.sql` | Inserta el escenario |
| `limpiar.sql` | Borra el escenario y todo lo que se haya creado encima (alertas, estados, telemetría, invitaciones). El resto de la base no cambia |
| `credenciales.json` | Usuarios, contraseña, rol y establecimientos. Los tests leen las credenciales de acá, nunca las escriben en el código |

## Cargar y limpiar

Desde la raíz del repo, con el stack de Docker levantado. Las dos operaciones se pueden repetir las veces que haga falta.

Cargar (primero limpia lo anterior):

```bash
cat fixtures/escenario/limpiar.sql fixtures/escenario/escenario.sql | docker exec -i vigia-db psql -U vigia_user -d vigia -v ON_ERROR_STOP=1
```

Solo limpiar:

```bash
docker exec -i vigia-db psql -U vigia_user -d vigia -v ON_ERROR_STOP=1 < fixtures/escenario/limpiar.sql
```

**Levantá la API con el perfil `prod`, no `dev`.** Con `dev` (el de `infra/.env`), `DataInitializer` borra toda la base cada vez que arranca la API, y con ella el escenario:

```bash
SPRING_PROFILES_ACTIVE=prod docker compose -f infra/docker-compose.yml up -d
```

En los tests de integración se carga solo. `EscenarioFixture.cargar(dataSource)` corre los dos scripts y `EscenarioFixture.credenciales()` lee el JSON (ver `EscenarioFixtureIT`).

## Qué hay

Todos los usuarios usan la contraseña de `credenciales.json`. Los nombres de usuario, establecimiento y caravana usan prefijos propios (`*@fixture.vigia.test`, `Fixture %`, `FX-*`), así que el escenario convive con la demo o con otros datos, y `limpiar.sql` borra solo lo suyo.

**Usuarios**

- `admin`: ADMINISTRADOR, rol global, sin empleado en ningún establecimiento.
- En cada establecimiento, un PRODUCTOR, un VETERINARIO y un CAMPERO (`productor.a`, `veterinario.b`, …).
- `productor.ambos`: PRODUCTOR con empleado en A y en B (H0035).

**Fixture A y Fixture B**, con la misma forma:

- Rodeos `Rodeo A1` y `Rodeo A2`.
- `Perímetro A`, un cuadrado de ~1 km asignado al rodeo 1.
- Seis animales con caravana vinculada, `FX-A-01` a `FX-A-06`. Los animales son `A-101` a `A-106`.
- Dos caravanas libres (`DISPONIBLE`), `FX-A-L1` y `FX-A-L2`.

| Animal | Rodeo | Telemetría | Para qué |
|---|---|---|---|
| 101 Calibrada | 1 | 8 días a 5 min | Calibración de salud completa (7 días) |
| 102 Celo | 1 | 24 h | Alerta de celo pendiente |
| 103 Sanitaria | 2 | 24 h | Alerta sanitaria pendiente |
| 104 Aislada | 1 | 24 h; las últimas 3 h a ~280 m del rodeo | Alerta de aislamiento pendiente, con el episodio abierto |
| 105 Fuera | 1 | 24 h; la última hora afuera del perímetro | Alerta de perímetro pendiente |
| 106 Sin datos | 1 | 24 h que terminan hace 3 h | Alerta de celo con dato desactualizado (> 2 h sin datos) |

Las vacas 101 a 105 tienen su último dato al momento de la carga. Todas tienen estado reproductivo: si no, el job de anestro las alertaría.

**Fechas.** Todo se ubica relativo al momento de la carga. Si el stack queda corriendo, las alertas envejecen y el dato desactualizado se vuelve más viejo; para volver al punto de partida, cargá de nuevo. La telemetría queda con las inferencias en `COMPLETADO`, así los jobs no generan alertas nuevas sobre el historial.
