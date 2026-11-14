# Inventario de funcionalidades — Manual de Usuario VigIA

Relevamiento previo a la redacción del manual. Fuente de verdad: el código del frontend (`frontend/src`) y los permisos del backend (`@PreAuthorize` de los controladores de `backend-api`). Los tests e2e son los de `frontend/e2e`; las Historias de Usuario son las del documento "Historias de Usuario - VigIA - UTN - 2026".

Roles: **Adm** = Administrador · **Prod** = Productor · **Vet** = Veterinario · **Camp** = Campero.

No existen usuarios sin rol: el registro, con formulario o con Google, siempre toma el rol de una invitación vigente.

Los desajustes entre lo que mostraba la pantalla y lo que permitía el servidor (alta de lotes del Adm, perímetros del Vet y el Camp, indicadores de salud del Camp) y la pantalla huérfana `/register` se corrigieron en la rama `fix/permisos-ui-y-registro`.

## Acceso y cuenta

| Funcionalidad | Pantalla/ruta | Roles que pueden usarla | Historia de Usuario relacionada | Cómo se prueba (test e2e) |
|---|---|---|---|---|
| Iniciar sesión con email y contraseña | `/login` → "Iniciar Sesión" | Todos | H0002 | `acceso/login.spec.ts` (H0002-01, H0002-02); `smoke/login-productor.spec.ts`; `rnf06/compatibilidad.spec.ts` (RNF06-01) |
| Bloqueo de la cuenta tras 5 intentos fallidos (30 minutos) | `/login` | Todos (solo ingreso con contraseña) | H0002 | `acceso/login.spec.ts` (H0002-03) |
| Pantalla de inicio según el rol (Adm → Establecimientos, resto → Dashboard) | `/` | Todos | H0002 | `acceso/login.spec.ts` (H0002-01, H0002-10) |
| Menú de navegación según el rol | Barra superior / menú lateral en celular | Todos | H0002 | `acceso/login.spec.ts` (H0002-10) |
| Cerrar sesión ("Salir") | Barra superior | Todos | H0002 | `acceso/login.spec.ts` (H0002-11) |
| Cierre automático de sesión tras 60 minutos de inactividad | Cualquier pantalla privada | Todos | H0002 | `acceso/login.spec.ts` (H0002-05, H0002-12) |
| Iniciar sesión con Google (SSO) | `/login` → "Iniciar sesión con Google" | Todos los que tengan cuenta o invitación | H0014 | `acceso/oauth2.spec.ts` (simula la vuelta desde Google: H0014-01, -03, -07, -08). El paso por Google no se automatiza |
| Registrarse desde el link de invitación (formulario o Google) | `/aceptar-invitacion?token=…` → "Registrarme y Aceptar" / "Registrarse con Google" | Invitado sin cuenta | H0001, H0014 | `acceso/registro.spec.ts` (H0001-01, -02, -04, -06) |
| Aceptar o rechazar invitaciones con la sesión iniciada | `/invitaciones` ("Mis Invitaciones Pendientes") | Vet, Camp | H0034 | `acceso/invitaciones.spec.ts` (H0034-01, -02) |
| Aceptar una invitación desde el link del mail estando logueado | `/aceptar-invitacion?token=…` → "Aceptar Invitación" | Destinatario de la invitación | H0034 | `acceso/invitaciones.spec.ts` (H0034-05, -06) |
| Elegir el establecimiento activo | Selector de la barra superior | Prod, Vet, Camp | H0035 | `acceso/cambio-establecimiento.spec.ts` (H0035-01, -06); `smoke/login-productor.spec.ts` |
| Editar nombre y apellido | `/perfil` ("Mi Perfil") → "Guardar Cambios" | Todos | H0037 | `acceso/perfil.spec.ts` (H0037-01) |
| Cambiar la contraseña | `/perfil` → "Actualizar Contraseña" | Todos menos quienes ingresan con Google | H0037 | `acceso/perfil.spec.ts` (H0037-02, -03) |
| Página de presentación pública | `/landing` (y `/` sin sesión) | Visitante | — | Sin e2e. Informativa, no se documenta paso a paso |

## Administración de la plataforma

| Funcionalidad | Pantalla/ruta | Roles que pueden usarla | Historia de Usuario relacionada | Cómo se prueba (test e2e) |
|---|---|---|---|---|
| Crear un establecimiento | `/establecimientos` → "Nuevo" | Adm | H0003 | `acceso/administrador.spec.ts` (H0003-01, -02) |
| Buscar establecimientos y ver su detalle (mapa y lotes) | `/establecimientos` → ⋮ → "Ver detalle" | Adm, Prod | H0003 | `acceso/administrador.spec.ts` |
| Desactivar / activar un establecimiento (con "Deshacer") | `/establecimientos` | Adm | H0003 | Sin e2e |
| Editar nombre y ubicación del establecimiento | `/establecimientos/:id/configuracion` | Adm, Prod (el Vet la ve sin poder editar) | H0003 | `acceso/administrador.spec.ts` (H0003-05, H0003-07) |
| Invitar a un productor | `/invitaciones` ("Productores Invitados") → "Nuevo" | Adm | H0032 | `acceso/administrador.spec.ts` (H0032-01, -03) |
| Consultar y eliminar invitaciones de productores | `/invitaciones` | Adm | H0032 | Sin e2e |
| Activar / desactivar usuarios | `/usuarios` | Adm | H0036 | `acceso/administrador.spec.ts` (H0036-02) |
| Registrar caravanas | `/caravanas` → "Nueva caravana" | Adm | H0040 | Sin e2e de la pantalla |
| Dar de baja y reactivar caravanas; filtros | `/caravanas` | Adm | H0040 | `rodeo/caravanas-gestion.spec.ts` (H0040-06, -07) |

## Equipo de trabajo

| Funcionalidad | Pantalla/ruta | Roles que pueden usarla | Historia de Usuario relacionada | Cómo se prueba (test e2e) |
|---|---|---|---|---|
| Invitar a un veterinario o campero | `/invitaciones` ("Personal Invitado") → "Nuevo" | Prod | H0033 | Sin e2e de la creación (la aceptación está en `acceso/invitaciones.spec.ts` y `acceso/registro.spec.ts` H0001-02) |
| Consultar y eliminar invitaciones de empleados | `/invitaciones` | Prod | H0033 | Sin e2e |
| Ver empleados y quitarlos del establecimiento | `/empleados` → "Quitar" | Prod | H0033 | Sin e2e |

## Rodeo

| Funcionalidad | Pantalla/ruta | Roles que pueden usarla | Historia de Usuario relacionada | Cómo se prueba (test e2e) |
|---|---|---|---|---|
| Listado de animales con vistas General / Celo / Salud, buscador y orden | `/animales` | Prod, Vet, Camp | H0006 | `rodeo/listado-rodeo.spec.ts` (H0006-04 a -10) |
| Alta manual de un animal (con peso inicial y caravana opcionales) | `/animales` → "+ Animal" | Prod | H0004, H0042, H0041 | `rodeo/alta-animal.spec.ts` (H0004-06, -07, -08); `rnf06/compatibilidad.spec.ts` (RNF06-02) |
| Crear un rodeo/lote | `/animales` → ⋮ → "Rodeo"; detalle del establecimiento → "+ Nuevo Lote" | Prod | H0038 | `rodeo/rodeos-y-baja-animal.spec.ts` (H0038-04, -05) |
| Carga masiva de animales (CSV o Excel) | `/animales` → ⋮ → "Cargar animales" | Prod | H0005 | `rodeo/carga-masiva.spec.ts` (H0005-05, -06, -07) |
| Carga masiva de estados reproductivos (CSV o Excel) | `/animales` → ⋮ → "Cargar estados" | Prod | H0007 | Sin e2e |
| Editar un animal | `/animales/:id` → pestaña "Ficha" → "Editar" | Prod | H0039 | `rodeo/rodeos-y-baja-animal.spec.ts` (H0039-05) |
| Dar de baja un animal | `/animales` → botón papelera | Prod | H0039 | `rodeo/rodeos-y-baja-animal.spec.ts` (H0039-06) |
| Vincular una caravana a un animal | `/animales` → "Vincular" (o en el alta) | Prod, Camp | H0041 | `rodeo/caravanas-gestion.spec.ts` (H0041-05) |
| Desvincular una caravana (con "Deshacer") | `/animales` → botón ✕ en la columna Caravana | Prod, Camp | H0041 | `rodeo/caravanas-gestion.spec.ts` (H0041-06) |
| Asignar caravanas libres automáticamente y descargar el resultado en Excel | `/animales` → ⋮ → "Asignar caravanas" | Prod, Camp | — (no tiene HU) | Sin e2e |
| Exportar la planilla de sensores de los animales en pantalla | `/animales` → ⋮ → "Exportar sensores" | Prod, Camp | — (no tiene HU) | Sin e2e |
| Ficha del animal: datos generales, caravana, estado de salud | `/animales/:id` → pestaña "Ficha" | Prod, Vet, Camp | H0006, H0039 | `rodeo/listado-rodeo.spec.ts` (H0006-08) |
| Indicadores de salud con colores | `/animales/:id` → pestaña "Ficha" | Prod, Vet, Camp | H0020 | `salud-reproduccion/sanitario.spec.ts` (H0020) |
| Aviso de período de calibración (7 días) | `/animales/:id` | Prod, Vet, Camp | H0017, H0041 | `salud-reproduccion/sanitario.spec.ts` (H0026-11) |
| Actividad motora de los últimos 7 días | `/animales/:id` → pestaña "Actividad" | Prod, Vet, Camp | H0015 | `rodeo/actividad-tab.spec.ts` (H0015-04 a -09) |
| Mapa de calor individual del animal | `/animales/:id` → pestaña "Actividad" | Prod, Vet, Camp | H0015, H0025 | Sin e2e |
| Registrar peso y ver su evolución (kg / lbs) | `/animales/:id` → pestaña "Actividad" → "Registrar Peso" | Prod, Vet, Camp | H0042 | `rodeo/registro-peso.spec.ts` (H0042-04 a -07) |
| Índice de cohesión con el rodeo | `/animales/:id` → pestaña "Actividad" | Prod, Vet, Camp | H0026 | `salud-reproduccion/sanitario.spec.ts` (H0026-10, -11, -12) |
| Alertas sanitarias del animal | `/animales/:id` → pestaña "Actividad" → "Ver detalle" | Prod, Vet, Camp | H0018 | Sin e2e específico |

## Reproducción

| Funcionalidad | Pantalla/ruta | Roles que pueden usarla | Historia de Usuario relacionada | Cómo se prueba (test e2e) |
|---|---|---|---|---|
| Historial reproductivo del animal | `/animales/:id` → pestaña "Reproducción" | Prod, Vet, Camp (solo consulta) | H0023 | `salud-reproduccion/reproduccion.spec.ts` (H0023-04, -06) |
| Registrar un estado reproductivo (Vacía, Gestante, Celo, Anestro) | Pestaña "Reproducción" → "Registrar estado" | Prod, Vet | H0007 | `rodeo/estado-reproductivo.spec.ts` (H0007-07, -08); `reproduccion.spec.ts` (H0023-05) |
| Editar el último estado reproductivo | Pestaña "Reproducción" → "Editar" | Prod, Vet | H0007, H0023 | `rodeo/estado-reproductivo.spec.ts` (H0007-09) |
| Línea de tiempo reproductiva (alertas, estados, intervenciones) | Pestaña "Reproducción" | Prod, Vet, Camp | H0024 | `alertas/linea-tiempo.spec.ts` (H0024-05 a -08) |
| Alerta de celo: detalle del evento | Detalle de alerta (desde Alertas, mapa, Animales o Dashboard) | Prod, Vet, Camp | H0008, H0009 | `salud-reproduccion/reproduccion.spec.ts` (H0009-07); `alertas/dato-disparador.spec.ts` (H0022-04) |
| Ventana de inseminación con cuenta regresiva | Detalle de alerta de celo | Prod, Vet, Camp | H0016 | `salud-reproduccion/reproduccion.spec.ts` (H0016-05 a -08) |
| Aviso de dato desactualizado (caravana sin transmitir > 2 h) | Listado de alertas, panel del mapa, detalle | Prod, Vet, Camp | H0008 | `salud-reproduccion/reproduccion.spec.ts` (H0008-09, -10) |
| Confirmar celo / Confirmar celo e inseminación | Detalle de alerta de celo | Prod, Vet, Camp | H0010, H0021 | `alertas/resolucion.spec.ts`; `alertas/intervencion.spec.ts` (H0021-05 a -07); `rnf06` (RNF06-03) |
| Archivado automático al vencer la ventana | (automático) | — | H0016 | `salud-reproduccion/reproduccion.spec.ts` (H0016-08) |
| Alerta de anestro y su configuración (días sin ciclo, prioridad) | Alertas; `/establecimientos/:id/configuracion` → "Anestro" | Alerta: Prod, Vet, Camp. Configuración: Prod, Vet | H0030 | Sin e2e |

## Salud

| Funcionalidad | Pantalla/ruta | Roles que pueden usarla | Historia de Usuario relacionada | Cómo se prueba (test e2e) |
|---|---|---|---|---|
| Detalle de alerta sanitaria (severidad, posible causa, gráficos comparativos) | Detalle de alerta | Prod, Vet, Camp (el Camp no puede confirmarla ni descartarla) | H0017, H0018 | `salud-reproduccion/sanitario.spec.ts` (H0018-06, -07) |
| Confirmar una alerta sanitaria y cargar el diagnóstico | Detalle de alerta → "Confirmar alerta" → "Registrar diagnóstico" | Confirmar: Prod, Vet. Diagnóstico: Vet | H0031 | `salud-reproduccion/diagnostico.spec.ts` (H0031-05 a -08) |
| Registrar el estado de salud manualmente | Pestaña "Historial clínico" → "Registrar estado de salud" | Prod, Vet | H0019 | `salud-reproduccion/sanitario.spec.ts` (H0019-05, -06) |
| Registrar una intervención desde la ficha | Pestaña "Historial clínico" → "Registrar intervención" | Vet | H0021, H0031 | Sin e2e |
| Historial clínico con filtros (alertas, diagnósticos, estados de salud, intervenciones) | Pestaña "Historial clínico" | Prod, Vet, Camp | H0031 | `salud-reproduccion/diagnostico.spec.ts` (H0031-07) |
| Listado de diagnósticos | `/diagnosticos` | Vet | H0031 | Sin e2e |

## Alertas

| Funcionalidad | Pantalla/ruta | Roles que pueden usarla | Historia de Usuario relacionada | Cómo se prueba (test e2e) |
|---|---|---|---|---|
| Histórico de alertas con filtros por estado, tipo y severidad, y orden por columna | `/alertas` | Prod, Vet, Camp | H0011, H0028 | `alertas/filtros.spec.ts` (H0011-07 a -11); `alertas/panel.spec.ts` (H0028-05, -08) |
| Panel de alertas del mapa (filtros Celo / Salud / Geoespacial y severidad) | `/mapa` → "Alertas" | Prod, Vet, Camp | H0011, H0028 | `alertas/filtros.spec.ts` (H0011-10); `alertas/panel.spec.ts` (H0028-06, -07) |
| Confirmar o descartar una alerta, con nota opcional | Detalle de alerta | Prod, Vet; Camp salvo sanitarias. La nota no se le muestra al Vet | H0010 | `alertas/resolucion.spec.ts` (H0010-07, -08, -09) |
| Aviso de alerta ya procesada por otro usuario | Detalle de alerta | Prod, Vet, Camp | H0010 | `alertas/resolucion.spec.ts` (H0010-10, -11) |
| Registrar la intervención al confirmar un celo | Panel "Registrar intervención" | Prod, Vet, Camp | H0021 | `alertas/intervencion.spec.ts` |
| Dato de origen y acción recomendada | Detalle de alerta y tarjetas | Prod, Vet, Camp | H0022, H0029 | `alertas/dato-disparador.spec.ts`; `alertas/notificacion.spec.ts` (H0029-06, -07) |
| Alertas nuevas en pantalla sin recargar | Mapa, Alertas, Dashboard | Prod, Vet, Camp | H0028, H0008 | `alertas/notificacion.spec.ts` (H0028-09, -10) |
| Aviso por mail de cada alerta nueva | Correo de cada usuario del establecimiento | Prod, Vet, Camp | H0008, H0017, H0027 | Sin e2e |
| Alerta de salida de perímetro (hora de salida, última posición, tiempo fuera) | Detalle de alerta geoespacial | Prod, Vet, Camp | H0027 | `alertas/dato-disparador.spec.ts` (H0022-05); `dashboard-mapa/mapa.spec.ts` (H0013-10) |
| Alerta de aislamiento social (agrupa a los animales alejados) | Detalle de alerta | Prod, Vet, Camp | H0026 | `alertas/panel.spec.ts` (H0028-07) |
| Configurar el umbral de aislamiento | `/establecimientos/:id/configuracion` → "Aislamiento" | Prod, Vet | H0026 | Sin e2e |

## Mapa y Dashboard

| Funcionalidad | Pantalla/ruta | Roles que pueden usarla | Historia de Usuario relacionada | Cómo se prueba (test e2e) |
|---|---|---|---|---|
| Mapa del rodeo en tiempo real con pines por estado | `/mapa` | Prod, Vet, Camp | H0012, H0013 | `dashboard-mapa/mapa.spec.ts` (H0012-06 a -12, H0013-05 a -08); `rnf06` (RNF06-04) |
| Panel del animal desde el pin (datos, última posición, alertas) | `/mapa` → clic en un pin | Prod, Vet, Camp | H0012, H0013 | `dashboard-mapa/mapa.spec.ts` (H0012-08, H0013-09) |
| Resumen de alertas por tipo (Reproductivo / Sanitario / Geoespacial) | `/mapa` | Prod, Vet, Camp | H0013, H0028 | `dashboard-mapa/mapa.spec.ts` (H0013-11) |
| Mapa de calor con alcance y período | `/mapa` → "Mapa de calor" | Prod, Vet, Camp | H0025 | `dashboard-mapa/mapa-calor.spec.ts` (H0025-05 a -12) |
| Perímetros: crear, editar, activar/desactivar y eliminar | `/mapa` → "Perímetros" | Prod (Vet y Camp solo consultan) | H0027 | `alertas/perimetros.spec.ts` (H0027-06 a -09) |
| Dashboard — Centro de mando | `/dashboard` → "Centro de mando" | Prod, Vet, Camp | H0043 | `dashboard-mapa/dashboard.spec.ts` (H0043-07 a -13); `rnf06` (RNF06-05) |
| Dashboard — Salud y reproducción | `/dashboard` → "Salud y reproducción" | Prod, Vet, Camp | H0044 | `dashboard-mapa/dashboard.spec.ts` (H0044-09 a -15) |
| Dashboard — Resumen ejecutivo | `/dashboard` → "Resumen ejecutivo" | Prod, Vet, Camp | H0045 | `dashboard-mapa/dashboard.spec.ts` (H0045-06 a -10) |
| Configuración del establecimiento (datos, ubicación, anestro, aislamiento) | Engranaje ⚙ de la barra / `/establecimientos/:id/configuracion` | Prod (todo); Vet (anestro y aislamiento); Adm (datos y ubicación) | H0003, H0030, H0026 | `acceso/administrador.spec.ts` (H0003-05, -07) |

## Calibración y procesos automáticos (sin pantalla propia)

| Funcionalidad | Dónde se ve | Roles | Historia de Usuario relacionada | Cómo se prueba (test e2e) |
|---|---|---|---|---|
| Calibración de 7 días: no hay alertas sanitarias ni índice de cohesión hasta que termina | Aviso en la ficha del animal; KPI "En calibración" del Dashboard | Prod, Vet, Camp | H0017, H0041 | `salud-reproduccion/sanitario.spec.ts` (H0026-11) |
| Detección automática de celo (estado "Posible celo") | Alertas, historial reproductivo | — | H0008 | `alertas/notificacion.spec.ts` (H0029-06, alerta generada por el worker) |
| Detección de salida de perímetro en el momento | Alertas | — | H0027 | `dashboard-mapa/mapa.spec.ts` |
| Archivado de anestro al registrarse un ciclo; silencio de 21 días tras descartarla | Alertas | — | H0030 | Sin e2e |
