# Demo E2E en producción

La demo vive en prod junto a los datos reales, separada por establecimiento. Reiniciar `backend-api` con el perfil `demo` borra solo la demo y la vuelve a sembrar con fechas de ahora, igual que el seed de `dev` en local.

| Archivo | Qué es |
|---|---|
| `backend/backend-api/src/main/resources/db/demo/limpiar_demo.sql` | Borra la demo. Lo corre `DataInitializer` al arrancar con el perfil `demo` |
| `limpiar_alertas_perimetro.sql` | Borra las alertas de perímetro recientes del establecimiento de los sensores reales |
| `nuevas_vaquillonas.csv` | Las 5 vaquillonas del Paso 1.4 |

## Qué es demo y qué es real

- **Demo:** el establecimiento `Tambo La Esperanza` y todo lo que cuelga de él, incluido lo creado en vivo (lote Cuarentena, #301 a #305, perímetros, alertas, intervenciones, diagnósticos, invitaciones).
- **Usuarios demo:**
  - `juan@laesperanza.com`, `carlos@laesperanza.com` y `laura@vet.com`;
  - los mails de `VIGIA_DEMO_EMAILS_EXTRA` (el de Pedro);
  - cualquier usuario cuyo único vínculo sea la demo.

  Un usuario que además es empleado de un establecimiento real solo pierde el vínculo con la demo. Un ADMINISTRADOR nunca se borra.
- **Caravanas demo:** `VIGIA-*`.
- **Todo lo demás es real.** La limpieza aborta sin borrar nada si algo de la demo está mezclado con datos reales:
  - un usuario demo es empleado de un establecimiento real;
  - una caravana `VIGIA-*` está en un establecimiento real;
  - hay dos establecimientos con el nombre de la demo;
  - al final cambió algún conteo de datos reales.

  En ese caso `backend-api` arranca igual, sin sembrar, y deja el motivo en el log: `limpiar_demo.sql aborto ...`.

Con `demo` no se crea `admin@vigia.dev`: prod ya tiene su administrador. La demo tiene un solo establecimiento (ya no se siembra "Estancia La Aurora", tampoco con `dev`), así que en el Paso 1.1 del guion Juan no tiene otro establecimiento para cambiar en el selector.

## Configuración en Railway (solo `backend-api`)

```
SPRING_PROFILES_ACTIVE=prod,demo
VIGIA_DEMO_EMAILS_EXTRA=<mail de Pedro>
```

Mientras el perfil `demo` esté puesto, **cada reinicio o redeploy de `backend-api` resetea la demo**. No deployen durante la presentación, y sáquenlo cuando terminen.

## Antes de la primera vez

1. Backup de prod (`pg_dump` o el backup de Railway).
2. Prueba en seco de la limpieza: muestra qué borraría y deshace todo.

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c "SET vigia.demo_emails_extra = '<mail de Pedro>'" -c "BEGIN" -f backend/backend-api/src/main/resources/db/demo/limpiar_demo.sql -c "ROLLBACK"
```

## Antes de cada ensayo o de la presentación

1. Reiniciar `backend-api` en Railway y esperar en el log `[DataInitializer] SEED DEMO completado.`. Reinicien **antes** de que alguien abra sesión: el reinicio corta las actualizaciones en vivo de las pantallas abiertas.
2. Borrar las alertas de perímetro recientes del establecimiento de los sensores. Si no, el cooldown de 6 hs no deja salir la alerta en vivo cuando se ensayó el mismo día.

```bash
psql "$DATABASE_URL" -1 -v ON_ERROR_STOP=1 -v est='<nombre del establecimiento de sensores>' -f demo/limpiar_alertas_perimetro.sql
```

3. Simuladores desde una PC, contra el worker de prod. En `simulador/.env`:

```
API_URL=https://<worker de prod>/api/v1/ingesta/uplink
TTN_WEBHOOK_API_KEY=<la de prod>
```

Después, lo mismo que en local (ver `simulador/README.md`):
- `simulador.py` de fondo, arrancado después del `SEED DEMO completado`;
- un script por acto;
- el Acto 4 dentro de los ~25 min posteriores al reinicio.
