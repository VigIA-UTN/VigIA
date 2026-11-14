# Manual de Usuario

**PROYECTO:** "Desarrollo de un sistema de detección de celo y estado de salud en bovinos mediante sensores inerciales y de posición (VigIA)"

**Integrantes – Año 2026**

| Legajo | Nombre | E-Mail |
|---|---|---|
| | | |
| | | |
| | | |
| | | |
| | | |

**Profesores:**

- Director de Cátedra: Mag. Ing. Gabriela Salem
- Profesor a cargo del curso: Mag. Ing. Gabriela Salem
- Profesores a cargo del proyecto: Ing. Andrea Alegretti, Ing. Pablo Abramowicz

## Historial de Revisión

| Fecha | Versión | Descripción | Rol | Autor |
|---|---|---|---|---|
| 13/11/2026 | 0.1 | Primera versión del Manual de Usuario. | | |

# 1. ¿Qué es VigIA?

VigIA es una plataforma web de monitoreo ganadero. Cada animal lleva una caravana electrónica con un sensor de movimiento y un GPS. La caravana transmite datos durante todo el día y VigIA los convierte en información concreta para el establecimiento:

- avisa cuándo una vaca está en celo y cuánto tiempo queda para inseminarla;
- avisa cuándo un animal se comporta distinto de lo habitual, lo que puede anticipar un problema de salud;
- avisa cuándo un animal sale del perímetro del campo o se aparta del rodeo;
- avisa cuándo una vaca lleva demasiados días sin mostrar celo (anestro).

Además, VigIA reúne en un solo lugar el registro del rodeo: los datos de cada animal, su historial reproductivo, su historial clínico, su peso y su ubicación en el mapa.

VigIA está pensada para las personas que trabajan en el establecimiento: el productor, el veterinario y el personal de campo (campero). No hace falta tener conocimientos de informática para usarla.

# 2. Objetivos de VigIA

- Detectar a tiempo los celos, para no perder la ventana de inseminación.
- Detectar en forma temprana los cambios de comportamiento que pueden indicar una enfermedad.
- Controlar la ubicación del rodeo, aun de noche, y avisar si un animal sale del campo o queda aislado.
- Llevar un registro ordenado y trazable del rodeo: estados reproductivos, estados de salud, diagnósticos, intervenciones y pesadas.
- Dar a cada integrante del equipo la información que necesita según su rol, y avisarle por la plataforma y por correo electrónico cuando algo requiere atención.

# 3. Prerrequisitos

Para utilizar VigIA usted necesita:

- **Un dispositivo con navegador web actualizado:** computadora, tablet o celular. VigIA funciona en Google Chrome, Mozilla Firefox y Microsoft Edge. No es necesario instalar ningún programa.
- **Conexión a internet.** Una conexión móvil 4G es suficiente.
- **Una cuenta en VigIA.** Las cuentas se crean únicamente por invitación: el administrador de la plataforma invita al productor, y el productor invita a su veterinario y a sus camperos. La invitación llega por correo electrónico.
- **Una dirección de correo electrónico** para recibir la invitación y los avisos de alertas. Si usa una cuenta de Google, puede ingresar con ella.
- **Caravanas VigIA colocadas en los animales.** El administrador de la plataforma da de alta las caravanas de cada establecimiento. Sin caravana vinculada, el animal figura en el rodeo pero no genera datos ni alertas automáticas.

# 4. Desarrollo del manual de usuario

En este manual los textos que aparecen en la pantalla (botones, menús, títulos y campos) se escriben entre comillas, tal como los muestra VigIA. Por ejemplo: presione "Guardar".

## 4.1. Inicio de sesión

### 4.1.1. Ingresar con email y contraseña

**Para qué sirve:** acceder a VigIA con su cuenta.

**Quién puede hacerlo:** todos los usuarios registrados.

1. Abra el navegador e ingrese a la dirección de VigIA.
2. En la pantalla "Iniciá sesión en VigIA", escriba su correo en el campo "Email".
3. Escriba su contraseña en el campo "Contraseña". Puede presionar el ícono del ojo para ver lo que escribió.
4. Presione "Iniciar Sesión".

![](img/01-login.png)

*Imagen 1: Pantalla de inicio de sesión.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Email" | Correo con el que se registró en VigIA. | Sí | Un correo con formato válido (por ejemplo, nombre@dominio.com). |
| "Contraseña" | Su contraseña de VigIA. | Sí | Cualquier texto. |

**Resultado esperado:** VigIA abre la página principal que corresponde a su rol (ver 4.2).

**Errores posibles y cómo resolverlos:**

| Mensaje | Qué significa | Qué hacer |
|---|---|---|
| "Ingresá un email válido" / "La contraseña es requerida" | Falta un dato o el correo está mal escrito. | Complete los dos campos y revise el correo. |
| "Credenciales incorrectas. Verificá tu email y contraseña." | El correo o la contraseña no coinciden. | Vuelva a escribirlos con cuidado. |
| "Tu cuenta fue bloqueada por múltiples intentos fallidos. Revisá tu correo o contactá al administrador." | Se ingresó una contraseña incorrecta 5 veces seguidas. | Espere 30 minutos e intente de nuevo, o contacte al administrador. |
| "La cuenta no está activa. Contactá al administrador." | El administrador desactivó su usuario. | Contacte al administrador de la plataforma. |
| "No se pudo conectar al servidor." | No hay conexión con VigIA. | Revise su conexión a internet e intente más tarde. |

![](img/02-login-credenciales-incorrectas.png)

*Imagen 2: Mensaje de credenciales incorrectas.*

![](img/03-login-campos-vacios.png)

*Imagen 3: Validación de campos vacíos en el inicio de sesión.*

### 4.1.2. Ingresar con Google

**Para qué sirve:** acceder con su cuenta de Google, sin recordar una contraseña aparte.

**Quién puede hacerlo:** cualquier usuario que ya tenga cuenta en VigIA con ese correo, o que tenga una invitación pendiente a ese correo.

1. En la pantalla de inicio de sesión, presione "Iniciar sesión con Google".
2. Elija su cuenta de Google y autorice el acceso.
3. VigIA muestra "Iniciando sesión con SSO..." y luego abre su página principal.

**Resultado esperado:** ingresa a VigIA. Si es la primera vez y tenía una invitación, la cuenta se crea con el nombre y apellido de Google y con el rol de la invitación.

![](img/04a-login-google-seleccion-cuenta.png)

*Imagen 4: Pantalla de Google para elegir la cuenta.*

**Errores posibles:**

| Mensaje | Qué significa | Qué hacer |
|---|---|---|
| "No se puede registrar mediante SSO sin una invitación previa." | Su correo de Google no tiene cuenta en VigIA ni una invitación vigente. | Pida una invitación al productor o al administrador. |
| "La cuenta no está activa." | El administrador desactivó su usuario. | Contacte al administrador de la plataforma. |
| "Hubo un problema al iniciar sesión con Google. Intentá de nuevo." | La autenticación con Google no se completó. | Vuelva a intentarlo. |

![](img/04b-login-google-sin-invitacion.png)

*Imagen 5: Ingreso con Google rechazado por falta de invitación.*

### 4.1.3. Registrarse a partir de una invitación

**Para qué sirve:** crear su cuenta en VigIA la primera vez.

**Quién puede hacerlo:** la persona que recibió la invitación por correo electrónico.

1. Abra el correo "Invitación a unirse como … en VigIA" que recibió.
2. Presione el botón "Aceptar invitación" del correo.

![](img/80-mail-invitacion.png)

*Imagen 6: Correo de invitación a VigIA.*

3. Se abre la pantalla "Te han invitado a unirte a" con el nombre del establecimiento. Los campos "Correo" y "Rol" ya vienen completos y no se pueden modificar.
4. Elija una de las dos opciones:
   - Presione "Registrarse con Google" para usar su cuenta de Google, o
   - complete "Nombre", "Apellido" y "Contraseña" y presione "Registrarme y Aceptar".

![](img/81-aceptar-invitacion-registro.png)

*Imagen 7: Registro a partir de una invitación.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Correo" | Correo al que se envió la invitación. | — | No se modifica. |
| "Rol" | Rol que tendrá en el establecimiento. | — | No se modifica. |
| "Nombre" | Su nombre. | Sí | Texto. |
| "Apellido" | Su apellido. | Sí | Texto. |
| "Contraseña" | La contraseña con la que ingresará. | Sí | Entre 8 y 32 caracteres, con al menos una mayúscula, una minúscula y un número o símbolo. |

**Resultado esperado:** la cuenta queda creada, asociada al establecimiento con el rol de la invitación, y VigIA abre su página principal.

![](img/82-registro-validaciones.png)

*Imagen 8: Mensajes de validación del registro.*

**Errores posibles y cómo resolverlos:**

| Mensaje | Qué hacer |
|---|---|
| "El nombre es requerido" / "El apellido es requerido" | Complete el campo. |
| "Debe tener al menos 8 caracteres", "No puede tener más de 32 caracteres", "Debe contener al menos una mayúscula", "Debe contener al menos una minúscula", "Debe contener al menos un número o símbolo" | Elija una contraseña que cumpla la regla indicada. |
| "Algo salió mal" con "El enlace de invitación es inválido o ha expirado." o "La invitación ha expirado" | La invitación vence a los 7 días o ya fue usada. Pida una nueva a quien lo invitó. |
| "No se proporcionó ningún token de invitación." | El enlace está incompleto. Abra el enlace desde el correo, sin modificarlo. |
| "Estás logueado como …, pero esta invitación fue enviada a …" | Tiene abierta la sesión de otro usuario. Presione "Cerrar sesión" y vuelva a abrir el enlace. |

### 4.1.4. Aceptar o rechazar invitaciones a otros establecimientos

**Para qué sirve:** sumarse a otro establecimiento sin crear una cuenta nueva.

**Quién puede hacerlo:** veterinarios y camperos que ya tienen cuenta y reciben una invitación de otro productor.

1. Cuando lo inviten, recibirá el correo "Nueva invitación pendiente en VigIA".
2. Ingrese a VigIA y presione "Invitaciones" en la barra superior.
3. En "Mis Invitaciones Pendientes" verá cada invitación con el establecimiento, el "Rol sugerido", "Enviado por", la "Fecha de envío" y la fecha en que "Vence el".
4. Presione "Aceptar Invitación" para sumarse, o "Rechazar" para no aceptarla. Si rechaza, confirme con "Sí, rechazar".

![](img/83-mis-invitaciones-pendientes.png)

*Imagen 9: Invitaciones pendientes de un veterinario.*

**Resultado esperado:** al aceptar, el establecimiento aparece en el selector de la barra superior (ver 4.2.1).

También puede aceptar la invitación desde el enlace del correo: si tiene la sesión iniciada con el mismo correo, VigIA muestra "Ya iniciaste sesión como …" con los botones "Aceptar Invitación" y "Rechazar o Ignorar".

**Errores posibles:** "Error al aceptar invitación." o "Error al procesar tu invitación. Quizás ya fue usada o está expirada.": la invitación venció o ya fue usada. Pida una nueva.

### 4.1.5. Cerrar sesión

1. Presione "Salir" en el extremo derecho de la barra superior. En el celular, abra el menú (ícono de tres líneas) y presione "Cerrar sesión".

**Resultado esperado:** VigIA vuelve a la pantalla de inicio de sesión.

Por seguridad, VigIA cierra la sesión sola si usted no usa la aplicación durante 60 minutos.

### 4.1.6. Mi Perfil

**Para qué sirve:** consultar sus datos, corregir su nombre y apellido y cambiar su contraseña.

**Quién puede hacerlo:** todos los usuarios.

**Modificación de datos personales**

1. Presione "Mi Perfil" en la barra superior.
2. En "Datos Personales", corrija "Nombre" y "Apellido".
3. Presione "Guardar Cambios".

**Modificación de la contraseña**

1. En "Seguridad", complete "Contraseña Actual", "Nueva Contraseña" y "Confirmar Nueva Contraseña".
2. Presione "Actualizar Contraseña".

![](img/78-mi-perfil.png)

*Imagen 10: Pantalla Mi Perfil.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Correo Electrónico" | Correo de su cuenta. | — | No se modifica. |
| "Nombre" / "Apellido" | Sus datos personales. | Sí | Texto. |
| "Rol en VigIA" | Su rol. | — | No se modifica. |
| "Contraseña Actual" | La contraseña que usa hoy. | Sí | La contraseña vigente. |
| "Nueva Contraseña" | La contraseña nueva. | Sí | Mínimo 8 caracteres (máximo 32), con mayúscula, minúscula y un número o símbolo. |
| "Confirmar Nueva Contraseña" | Repetición de la nueva. | Sí | Igual a "Nueva Contraseña". |

**Resultado esperado:** "Perfil actualizado correctamente." o "Contraseña cambiada exitosamente.".

**Errores posibles:** "Las contraseñas no coinciden." (escriba la misma contraseña en los dos campos); si la contraseña actual es incorrecta, VigIA muestra el error y no hace el cambio.

Si usted ingresa con Google, la sección "Seguridad" muestra "Inicio de sesión con Google": su contraseña se administra desde su cuenta de Google.

![](img/79-perfil-usuario-google.png)

*Imagen 11: Mi Perfil de un usuario que ingresa con Google.*

## 4.2. Página principal

### 4.2.1. Elementos comunes

Todas las pantallas tienen una barra superior con:

- **Selector de establecimiento** (a la izquierda): si usted trabaja en más de un establecimiento, elija aquí con cuál trabajar. Todo lo que ve (animales, alertas, mapa, Dashboard, empleados) corresponde al establecimiento elegido. VigIA recuerda la elección al recargar la página. Solo se ofrecen los establecimientos activos.
- **Engranaje** (al lado del selector, solo productor y veterinario): abre la configuración del establecimiento elegido (ver 4.3).
- **Menú de secciones**, que depende del rol.
- **"Mi Perfil"** y **"Salir"** (a la derecha).

En el celular, las secciones se abren desde el ícono de tres líneas ("Abrir menú").

### 4.2.2. Productor

Al ingresar, el productor ve el **Dashboard** en la pestaña "Centro de mando", con lo que requiere atención hoy (ver 4.12).

Menú: "Establecimientos", "Empleados", "Animales", "Mapa", "Alertas" e "Invitaciones".

![](img/10-dashboard-centro-de-mando.png)

*Imagen 12: Página principal del productor (Dashboard, Centro de mando).*

### 4.2.3. Veterinario

El veterinario también ingresa al Dashboard. Su menú es: "Animales", "Diagnósticos", "Mapa", "Alertas" e "Invitaciones".

![](img/84-inicio-veterinario.png)

*Imagen 13: Página principal del veterinario.*

### 4.2.4. Campero

El campero ingresa al Dashboard. Su menú es: "Animales", "Mapa", "Alertas" e "Invitaciones".

![](img/86-inicio-campero.png)

*Imagen 14: Página principal del campero.*

### 4.2.5. Administrador

El administrador de la plataforma no trabaja dentro de un establecimiento: los administra a todos. Al ingresar ve la pantalla "Establecimientos". Su menú es: "Establecimientos", "Caravanas", "Usuarios" e "Invitaciones".

![](img/90-admin-establecimientos.png)

*Imagen 15: Página principal del administrador.*

### 4.2.6. Roles y permisos

| Tarea | Administrador | Productor | Veterinario | Campero |
|---|:-:|:-:|:-:|:-:|
| Crear, desactivar y activar establecimientos | ✔ | | | |
| Editar nombre y ubicación del establecimiento | ✔ | ✔ | (solo ve) | |
| Configurar anestro y aislamiento | | ✔ | ✔ | |
| Invitar productores | ✔ | | | |
| Invitar veterinarios y camperos; quitar empleados | | ✔ | | |
| Activar y desactivar usuarios | ✔ | | | |
| Registrar, dar de baja y reactivar caravanas | ✔ | | | |
| Crear rodeos/lotes | | ✔ | | |
| Dar de alta, editar y dar de baja animales; cargas masivas | | ✔ | | |
| Vincular, desvincular y asignar caravanas a animales | | ✔ | | ✔ |
| Ver animales, fichas, mapa, alertas y Dashboard | | ✔ | ✔ | ✔ |
| Registrar peso | | ✔ | ✔ | ✔ |
| Registrar estado reproductivo | | ✔ | ✔ | |
| Registrar estado de salud | | ✔ | ✔ | |
| Registrar intervenciones desde la ficha del animal | | | ✔ | |
| Confirmar o descartar alertas de celo, perímetro, aislamiento y anestro | | ✔ | ✔ | ✔ |
| Confirmar o descartar alertas sanitarias | | ✔ | ✔ | |
| Registrar la intervención al confirmar un celo | | ✔ | ✔ | ✔ |
| Registrar diagnósticos y ver la sección "Diagnósticos" | | | ✔ | |
| Crear, editar, activar y eliminar perímetros | | ✔ | | |

## 4.3. Configuración general: configuración del establecimiento

**Para qué sirve:** definir los datos del establecimiento y los umbrales con los que VigIA genera las alertas de anestro y de aislamiento.

**Quién puede hacerlo:** el productor (todas las secciones); el veterinario (solo "Anestro" y "Aislamiento"); el administrador (solo "General" y "Ubicación").

### 4.3.1. Visualización

1. Presione el engranaje de la barra superior. El administrador y el productor también pueden entrar desde "Establecimientos" → menú ⋮ de la tarjeta → "Configuración".
2. Se abre "Configuración del establecimiento". A la izquierda, en "SECCIONES", puede saltar a "General", "Ubicación", "Anestro" o "Aislamiento".

![](img/70-configuracion-establecimiento.png)

*Imagen 16: Configuración del establecimiento: datos generales y ubicación.*

### 4.3.2. Modificación de los datos generales y la ubicación

1. En "Datos generales", corrija el "Nombre".
2. En "Ubicación", escriba "Latitud" y "Longitud", o haga clic sobre el mapa para elegir el punto ("Hacé click en el mapa para elegir la ubicación").
3. Presione "Guardar Cambios". El pie de página indica qué secciones tienen "Cambios sin guardar".

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Nombre" | Nombre del establecimiento. | Sí | Entre 3 y 80 caracteres. |
| "Latitud" | Coordenada del casco. | Sí | Número entre -90 y 90. |
| "Longitud" | Coordenada del casco. | Sí | Número entre -180 y 180. |

### 4.3.3. Modificación del umbral de anestro

1. En "Días sin ciclo", ingrese la "Cantidad de días" sin actividad de ciclo reproductivo a partir de la cual VigIA genera una alerta de anestro.
2. En "Prioridad de la alerta", elija el "Nivel de prioridad": "Baja", "Media" o "Alta".
3. Presione "Guardar Cambios".

### 4.3.4. Modificación del umbral de aislamiento

1. En "Umbral de aislamiento", ingrese la "Distancia mínima al rodeo" en metros. Un animal que permanece a esa distancia del resto del rodeo se considera aislado.
2. Para volver al valor por defecto, presione "Restablecer a 100 m".
3. Presione "Guardar Cambios".

![](img/71-configuracion-anestro-aislamiento.png)

*Imagen 17: Configuración de anestro y aislamiento.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Cantidad de días" | Días sin celo, inseminación ni parto antes de alertar anestro. | Sí | Número entero entre 1 y 365. Por defecto, 45. |
| "Nivel de prioridad" | Severidad de la alerta de anestro. | Sí | "Baja", "Media" o "Alta". |
| "Distancia mínima al rodeo" | Distancia para considerar aislado a un animal. | Sí | Número entero entre 10 y 1000 metros. Por defecto, 100. |

Un valor de distancia menor detecta antes, pero puede generar más falsas alarmas.

**Errores posibles:** "No tenés permiso para editar la configuración de este establecimiento." (su rol no puede modificar esa sección); los mensajes de validación de la tabla, por ejemplo "Debe ser al menos 1 día" o "No puede superar los 1000 metros".

## 4.4. Establecimientos

### 4.4.1. Alta de un establecimiento

**Quién puede hacerlo:** el administrador.

1. En "Establecimientos", presione "Nuevo".
2. Complete "Nombre", "Latitud" y "Longitud".
3. Presione "Confirmar".

![](img/91-crear-establecimiento.png)

*Imagen 18: Alta de un establecimiento.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Nombre" | Nombre del establecimiento. | Sí | Al menos 3 caracteres ("El nombre es obligatorio", "Debe tener al menos 3 caracteres"). |
| "Latitud" | Coordenada del casco (ejemplo: -34.6037). | Sí | Número entre -90 y 90. |
| "Longitud" | Coordenada del casco (ejemplo: -58.3816). | Sí | Número entre -180 y 180. |

**Resultado esperado:** el establecimiento aparece en el listado como activo. El siguiente paso es invitar a su productor (ver 4.5.1).

### 4.4.2. Visualización de detalles

**Quién puede hacerlo:** el administrador y el productor (el productor ve solo los suyos).

1. En "Establecimientos", busque por nombre o número en "Buscar por nombre o #id...".
2. Presione "Ver detalle" (administrador) o el menú ⋮ → "Ver detalle" (productor).
3. El detalle muestra la ubicación en el mapa, el botón "Configuración" y la lista de "Lotes" con su estado y fecha de alta.

![](img/72-establecimientos-productor.png)

*Imagen 19: Establecimientos del productor.*

![](img/73-establecimiento-detalle-lotes.png)

*Imagen 20: Detalle del establecimiento con sus lotes.*

Desde este detalle el productor puede crear un lote: escriba el nombre en "Nombre del nuevo lote" y presione "+ Nuevo Lote" (ver también 4.7.2).

### 4.4.3. Modificación

La modificación del nombre y la ubicación se hace desde la configuración del establecimiento (ver 4.3.2).

### 4.4.4. Baja (desactivación) y reactivación

**Quién puede hacerlo:** el administrador.

1. En la tarjeta del establecimiento, presione "Desactivar".
2. Confirme con "Desactivar" en el mensaje "¿Desactivar …?".
3. Durante unos segundos aparece la opción "Deshacer" por si se equivocó.
4. Para volver a habilitarlo, presione "Activar" en la tarjeta.

**Resultado esperado:** un establecimiento desactivado deja de estar disponible para sus usuarios, pero no se borra su historial.

## 4.5. Usuarios e invitaciones

### 4.5.1. Alta de un productor (invitación)

**Quién puede hacerlo:** el administrador.

1. Presione "Invitaciones". Se abre "Productores Invitados".
2. Presione "Nuevo".
3. Complete "Email del Productor" y elija el "Nombre de Establecimiento".
4. Presione "Invitar".

![](img/96-invitar-productor.png)

*Imagen 21: Invitación de un productor.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Email del Productor" | Correo de la persona a invitar. | Sí | Correo válido ("Debe ser un email válido"). |
| "Nombre de Establecimiento" | Establecimiento que va a gestionar. | Sí | Un establecimiento existente ("Seleccione un establecimiento"). |

**Resultado esperado:** "Invitación enviada correctamente a …". El productor recibe un correo con un enlace válido por 7 días. Si el correo ya pertenece a un productor registrado, VigIA lo asocia directamente: "El usuario ya estaba registrado y fue asociado al establecimiento. Se le envió un email de notificación.".

### 4.5.2. Alta de un empleado (invitación)

**Quién puede hacerlo:** el productor, para el establecimiento elegido en la barra superior.

1. Presione "Invitaciones". Se abre "Personal Invitado".
2. Presione "Nuevo".
3. Complete "Email del Empleado" y elija el "Rol": "Veterinario" o "Campero".
4. Presione "Generar Invitación".

![](img/76-invitar-empleado.png)

*Imagen 22: Invitación de un empleado.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Email del Empleado" | Correo de la persona a invitar. | Sí | Correo válido ("Debe ser un email válido"). |
| "Rol" | Función que tendrá en el establecimiento. | Sí | "Veterinario" o "Campero" ("Debe ser un rol válido"). |

**Resultado esperado:** "Invitación enviada correctamente a …" y la invitación aparece en la lista como "PENDIENTE".

![](img/77-invitacion-enviada.png)

*Imagen 23: Invitación enviada.*

**Errores posibles:** si ya existe una invitación pendiente y vigente para ese correo y establecimiento, VigIA no permite crear otra y muestra el motivo.

### 4.5.3. Visualización y baja de invitaciones

1. En "Productores Invitados" (administrador) o "Personal Invitado" (productor), busque por correo en "Buscar por email..." y filtre por estado: "PENDIENTE", "USADA" o "EXPIRADA". El administrador también puede filtrar por establecimiento.
2. Para eliminar una invitación pendiente, presione el ícono de papelera ("Eliminar invitación") y confirme con "Eliminar". Esta acción no se puede deshacer.

![](img/75-personal-invitado.png)

*Imagen 24: Listado de invitaciones del productor.*

![](img/95-productores-invitados.png)

*Imagen 25: Listado de invitaciones del administrador.*

### 4.5.4. Empleados: visualización y baja

**Quién puede hacerlo:** el productor.

1. Presione "Empleados". Verá nombre, email, rol y establecimiento de cada persona. Su propio usuario se marca con "Vos".
2. Use "Buscar por nombre, email o establecimiento..." y el filtro de roles para encontrar a alguien.
3. Para quitar a un veterinario o campero del establecimiento, presione "Quitar" y confirme con "Sí, quitar".

![](img/74-empleados.png)

*Imagen 26: Empleados del establecimiento.*

**Resultado esperado:** la persona pierde el acceso a ese establecimiento, pero conserva su cuenta y se mantienen sus registros. Los productores no se pueden quitar desde aquí: los gestiona el administrador.

### 4.5.5. Usuarios de la plataforma: activación y desactivación

**Quién puede hacerlo:** el administrador.

1. Presione "Usuarios". Puede buscar por nombre o email y filtrar por rol.
2. Presione "Desactivar" para bloquear el acceso de un usuario, o "Activar" para habilitarlo de nuevo.

![](img/94-usuarios.png)

*Imagen 27: Gestión de usuarios.*

**Resultado esperado:** un usuario desactivado no puede iniciar sesión ("La cuenta no está activa"); su historial se conserva.

## 4.6. Caravanas

**Quién puede hacerlo:** el administrador.

### 4.6.1. Alta de una caravana

1. Presione "Caravanas" y luego "Nueva caravana".
2. Complete los datos y presione "Guardar".

![](img/93-nueva-caravana.png)

*Imagen 28: Alta de una caravana.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Dev EUI*" | Identificador electrónico único que viene impreso en la caravana. | Sí | Texto único (ejemplo: 0004A30B00C1F5E2). |
| "Código" | Número visible de la caravana. | No | Texto único (ejemplo: CAR-0145). |
| "Alias" | Nombre corto para reconocerla. | No | Texto. |
| "Establecimiento*" | Establecimiento al que se entrega. | Sí | Un establecimiento activo. |

**Resultado esperado:** "Caravana registrada exitosamente." La caravana queda "LIBRE", lista para vincular a un animal.

**Errores posibles:** "El Dev EUI es obligatorio."; "Ya existe una caravana con este codigo".

### 4.6.2. Visualización

El listado muestra "Código", "Alias", "Establecimiento", "Estado" ("LIBRE", "ASIGNADA" o "BAJA"), "Batería", "Última señal" y "Animal vinculado". Puede buscar por código y filtrar por estado y por establecimiento.

![](img/92-caravanas.png)

*Imagen 29: Listado de caravanas.*

### 4.6.3. Baja y reactivación

1. Presione "Dar de baja" en la fila de la caravana y confirme con "Dar de baja". Si estaba vinculada a un animal, se desvincula.
2. Para reactivarla, presione "Reactivar" y confirme. La caravana vuelve a "LIBRE". El animal que tenía antes no se restaura: vuelva a vincularla desde "Animales".

## 4.7. Rodeo y animales

### 4.7.1. Visualización del listado de animales

**Quién puede hacerlo:** productor, veterinario y campero.

1. Presione "Animales".
2. Elija la vista:
   - "General": identificador, nombre, caravana (con batería y última señal), rodeo, estado, salud y estado reproductivo.
   - "Celo": estado reproductivo y fechas de último parto y de última inseminación.
   - "Salud": solo los animales "En observación" o "Enfermo".
3. Escriba en "Buscar por identificador, nombre o lote..." para filtrar.
4. Haga clic en el título de una columna para ordenar.
5. En "Acciones": "Ver" abre la ficha del animal; "Alerta" (si el animal tiene una alerta activa) abre su detalle.

![](img/30-animales-listado.png)

*Imagen 30: Listado de animales, vista General.*

![](img/32-animales-vista-celo.png)

*Imagen 31: Listado de animales, vista Celo.*

El botón ⋮ ("Más acciones") agrupa las acciones de creación, importación y caravanas.

![](img/31-animales-menu-acciones.png)

*Imagen 32: Menú de acciones del listado de animales.*

### 4.7.2. Alta de un rodeo/lote

**Quién puede hacerlo:** el productor.

1. En "Animales", presione ⋮ → "Rodeo".
2. Escriba el "Nombre*" y presione "Guardar".

![](img/35-nuevo-rodeo.png)

*Imagen 33: Alta de un rodeo/lote.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Nombre*" | Nombre del rodeo o lote (por ejemplo, "Vaquillonas"). | Sí | Texto. |

**Resultado esperado:** el lote queda activo y disponible para asignar animales, perímetros y filtros.

### 4.7.3. Alta manual de un animal

**Quién puede hacerlo:** el productor.

1. En "Animales", presione "+ Animal".
2. Complete el formulario "Nuevo Animal".
3. Presione "Guardar".

![](img/33-alta-animal-formulario.png)

*Imagen 34: Formulario de alta de un animal.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Identificador*" | Número de caravana oficial o número interno del animal. | Sí | Texto, único dentro del establecimiento. |
| "Nombre" | Nombre o alias del animal. | No | Texto. |
| "Fecha de Nacimiento*" | Fecha de nacimiento. | Sí | Una fecha que no sea futura. |
| "Raza" | Raza del animal. | No | Texto. |
| "Lote*" | Rodeo o lote al que pertenece. | Sí | Un lote activo del establecimiento. |
| "Peso inicial (kg)" | Peso al momento del alta. Queda como primera pesada del historial. | No | Número mayor o igual a 0, con un decimal. |
| "Caravana" | Caravana electrónica a vincular. | No | Una caravana libre del establecimiento, o "Sin caravana (vincular después)". |

**Resultado esperado:** "Animal registrado exitosamente." y el animal aparece en el listado. Si le vinculó una caravana, comienza su período de calibración de 7 días (ver Anexo A).

**Errores posibles y cómo resolverlos:**

| Mensaje | Qué hacer |
|---|---|
| "Ingresá el identificador del animal." / "Ingresá la fecha de nacimiento." / "Seleccioná un lote." | Complete el dato faltante. |
| "La fecha de nacimiento no puede ser futura." | Corrija la fecha. |
| Identificador ya registrado en el establecimiento | Use otro identificador o busque el animal existente en el listado. |
| "El rodeo/lote no está activo." | Elija otro lote o cree uno nuevo (ver 4.7.2). |

![](img/34-alta-animal-identificador-duplicado.png)

*Imagen 35: Error por identificador repetido.*

### 4.7.4. Alta masiva de animales (CSV o Excel)

**Para qué sirve:** dar de alta muchos animales de una vez a partir de una planilla.

**Quién puede hacerlo:** el productor.

1. En "Animales", presione ⋮ → "Cargar animales".
2. Descargue la plantilla con "Descargar template CSV" o "Descargar template Excel".
3. Complete la planilla, una fila por animal, respetando el orden de las columnas.
4. En "Archivo*", seleccione la planilla.
5. Presione "Subir Archivo".

![](img/36-carga-masiva-animales.png)

*Imagen 36: Carga masiva de animales.*

| Columna | Qué es | Obligatoria | Valores aceptados |
|---|---|---|---|
| IdCaravana | Identificador del animal. | Sí | Texto que no se repita en el archivo ni en el establecimiento. |
| Alias | Nombre del animal. | No | Texto que no se repita. |
| FechaNacimiento | Fecha de nacimiento. | Sí | Formato AAAA-MM-DD (por ejemplo, 2023-08-15), no futura. |
| Raza | Raza. | No | Texto. |
| RodeoLote | Lote del animal. | Sí | Nombre de un lote existente y activo. La carga no crea lotes nuevos. |
| Sensor | Caravana a vincular. | No | DevEUI, código o alias de una caravana libre del establecimiento, sin repetir. |

El archivo puede ser .csv, .xlsx o .xls, de hasta 5 MB. Las filas vacías se ignoran.

**Resultado esperado:** "¡Carga procesada correctamente!" con la cantidad de filas "Procesados", "Exitosos" y "Fallidos".

**Si alguna fila tiene errores, no se guarda ningún animal.** VigIA muestra "Carga fallida. Se encontraron errores y no se guardó ningún registro." y el "Detalle de errores:" fila por fila. Corrija esas filas y vuelva a subir el archivo completo.

![](img/37-carga-masiva-reporte-errores.png)

*Imagen 37: Reporte de una carga masiva con errores.*

Mensajes posibles por fila: "El IdCaravana es obligatorio.", "ID Caravana duplicado en el archivo: …", "El ID Caravana … ya existe en el establecimiento.", "Alias duplicado en el archivo: …", "El Alias … ya existe en el establecimiento.", "La fecha de nacimiento es obligatoria.", "Formato de fecha inválido. Use YYYY-MM-DD.", "La fecha de nacimiento no puede ser futura.", "El rodeo/lote es obligatorio.", "El rodeo/lote '…' no existe o no está activo en el establecimiento.", "El sensor '…' no existe en el establecimiento.", "El sensor '…' está dado de baja.", "El sensor '…' ya está vinculado a otro animal.", "El sensor '…' coincide con varias caravanas. Indicá su DevEUI.".

Mensajes sobre el archivo completo: "El archivo está vacío", "El archivo excede el tamaño máximo permitido (5MB)", "El archivo debe ser un Excel (.xlsx, .xls) o CSV (.csv)".

### 4.7.5. Vinculación y desvinculación de caravanas

**Quién puede hacerlo:** el productor y el campero.

**Vincular una caravana**

1. En el listado, en la columna "Caravana" del animal que dice "Sin vincular", presione "Vincular".
2. En "Caravana disponible*", elija una caravana libre.
3. Presione "Vincular".

**Desvincular una caravana**

1. En la columna "Caravana", presione la ✕ ("Desvincular").
2. Confirme con "Sí, desvincular".
3. Durante 5 segundos aparece "Caravana desvinculada de …" con la opción "Deshacer".

![](img/40-desvincular-caravana.png)

*Imagen 38: Confirmación para desvincular una caravana.*

**Asignar caravanas automáticamente**

1. Presione ⋮ → "Asignar caravanas".
2. En "Rodeo / Lote", elija un lote o "Todos los lotes". VigIA indica cuántos animales no tienen caravana y cuántas caravanas libres hay.
3. Presione "Asignar". Las caravanas libres se vinculan al azar a los animales sin caravana.
4. En "Caravanas asignadas", presione "Descargar Excel" para obtener la lista y colocar cada caravana en su animal.

![](img/39-asignar-caravanas.png)

*Imagen 39: Asignación automática de caravanas.*

**Exportar sensores:** ⋮ → "Exportar sensores (N)" descarga una planilla Excel con los animales con caravana que se ven en el listado.

**Errores posibles:** "Seleccioná una caravana disponible."; "No hay caravanas disponibles en este establecimiento." (pida al administrador que registre más caravanas); "No se pudo deshacer la acción. La caravana ya podría estar vinculada a otro animal.".

### 4.7.6. Visualización de detalles: ficha del animal

**Quién puede hacerlo:** productor, veterinario y campero.

1. En el listado, presione "Ver" en el animal. También puede entrar desde el mapa ("Ver ficha completa") o desde una alerta ("Ver ficha del animal").
2. La ficha tiene cuatro pestañas:
   - **"Actividad"** (se abre por defecto): actividad motora de los últimos 7 días, "Mapa de Calor — Posición", "Evolución de Peso", "Índice de cohesión" y "Alertas sanitarias".
   - **"Ficha"**: "Información General", caravana vinculada (con batería y última señal) e "Indicadores de Salud".
   - **"Reproducción"**: "Línea de tiempo reproductiva" e "Historial Reproductivo" (ver 4.8).
   - **"Historial clínico"**: alertas, diagnósticos, estados de salud e intervenciones (ver 4.9).

Si el animal está en calibración, la ficha muestra "Período de calibración en proceso" con los días que faltan (ver Anexo A).

![](img/50-animal-actividad.png)

*Imagen 40: Ficha del animal, pestaña Actividad.*

![](img/51-animal-actividad-peso-cohesion.png)

*Imagen 41: Mapa de calor, evolución de peso e índice de cohesión del animal.*

![](img/53-animal-ficha.png)

*Imagen 42: Ficha del animal, pestaña Ficha.*

**Indicadores de Salud.** Muestran tres indicadores con colores: "Estado de movimiento", "Estado reproductivo" y "Estado de salud". Verde significa "Normal", amarillo "Atención", rojo "Alerta" y gris "Sin datos". Pase el puntero sobre cada indicador para leer qué significa.

**Índice de cohesión.** Indica cuán cerca del rodeo se mueve el animal. Puede elegir el período ("24 h", "7 d", "30 d", "90 d" o "Personalizado"). El gráfico compara "Este animal" con el "Promedio del rodeo" y marca los umbrales "cohesionado" y "alejado". Está "Disponible al finalizar la calibración.".

**Mapa de calor del animal.** Muestra las zonas donde más tiempo estuvo el animal en el período elegido (por defecto, los últimos 7 días).

### 4.7.7. Modificación de un animal

**Quién puede hacerlo:** el productor.

1. En la ficha del animal, abra la pestaña "Ficha".
2. Presione "Editar".
3. Corrija los datos y presione "Guardar" (o "Cancelar" para descartar los cambios).

![](img/54-animal-ficha-edicion.png)

*Imagen 43: Edición de un animal.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Identificador Oficial*" | Identificador del animal. | Sí | Texto único en el establecimiento. |
| "Nombre/Alias" | Nombre del animal. | No | Texto. |
| "Fecha de Nacimiento*" | Fecha de nacimiento. | Sí | Fecha no futura. |
| "Raza" | Raza. | No | Texto. |
| "Rodeo/Lote*" | Lote del animal. | Sí | Un lote activo. |

**Errores posibles:** los mismos del alta (ver 4.7.3), y "No se puede editar un animal inactivo.".

### 4.7.8. Baja de un animal

**Quién puede hacerlo:** el productor.

1. En el listado, presione el ícono de papelera del animal.
2. Confirme con "Sí, dar de baja".

**Resultado esperado:** el animal deja de mostrarse en el listado y en el mapa, su caravana queda libre y se conserva su historial.

### 4.7.9. Registro de peso

**Quién puede hacerlo:** productor, veterinario y campero.

1. En la ficha del animal, pestaña "Actividad", vaya a "Evolución de Peso".
2. Presione "Registrar Peso".
3. Complete el formulario y presione "Registrar".

![](img/52-registrar-peso.png)

*Imagen 44: Registro de peso.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Fecha de pesaje*" | Día de la pesada. | Sí | Una fecha que no sea futura. |
| "Peso*" | Peso medido. | Sí | Número mayor que 0 (mínimo 0.1). |
| Unidad | Unidad del valor ingresado. | Sí | "kg" o "lbs". VigIA guarda siempre en kilogramos. |

**Resultado esperado:** "¡Peso registrado correctamente!" El gráfico muestra la evolución con el "Mínimo", el "Máximo" y el "Promedio". Puede ver el gráfico en kg o en lbs.

**Errores posibles:** "El peso debe ser un número positivo."; "La fecha es obligatoria."; "Error al registrar el peso. Verifique los datos ingresados.".

## 4.8. Reproducción

### 4.8.1. Visualización del historial reproductivo

**Quién puede hacerlo:** productor, veterinario y campero (el campero solo consulta).

1. En la ficha del animal, abra la pestaña "Reproducción".
2. La "Línea de tiempo reproductiva" ubica en orden cronológico los estados, las alertas y las intervenciones. Puede filtrar el período ("30 días", "90 días", "12 meses", "Todo"). Al hacer clic en un evento se abre su resumen, con el enlace "Ver alerta #…" cuando corresponde.
3. El "Historial Reproductivo" lista cada registro con "Estado", "Último Parto", "Última Inseminación", "Origen" ("Automático", "Manual" o "Carga masiva"), "Registrado por" y "Fecha Registro".

![](img/55-animal-reproduccion.png)

*Imagen 45: Pestaña Reproducción con la línea de tiempo.*

### 4.8.2. Alta de un estado reproductivo

**Quién puede hacerlo:** productor y veterinario.

1. En la pestaña "Reproducción", presione "Registrar estado".
2. Complete el formulario y presione "Registrar".

![](img/56-registrar-estado-reproductivo.png)

*Imagen 46: Registro de un estado reproductivo.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Estado Reproductivo*" | Situación actual del animal. | Sí | "Vacía", "Gestante", "Celo" o "Anestro". |
| "Fecha Último Parto" | Fecha del último parto. | No | Fecha no futura, no anterior al último parto ya registrado. |
| "Fecha Última Inseminación" | Fecha de la última inseminación. | No | Fecha no futura. No puede ser posterior al último parto, salvo que el estado sea "Gestante". |

Los estados "Posible celo" e "Inseminada" no se cargan a mano: los registra VigIA cuando detecta un celo y cuando se registra una inseminación.

**Resultado esperado:** "Estado registrado correctamente." Si registra "Celo", VigIA genera además una alerta de celo con su ventana de inseminación (ver 4.10.2).

**Errores posibles:** "La fecha de última inseminación no puede ser posterior al último parto sin un estado de gestación."; "La fecha de último parto no puede ser anterior a la ya registrada (…)."; "La fecha de último parto no puede ser futura."; "La fecha de última inseminación no puede ser futura.".

### 4.8.3. Modificación del último estado

1. En el "Historial Reproductivo", presione "Editar" en el primer registro de la lista (el más reciente).
2. Corrija los datos y presione "Actualizar".

Solo se puede editar el registro más reciente, y no si corresponde a una inseminación.

### 4.8.4. Alta masiva de estados reproductivos

**Quién puede hacerlo:** el productor.

1. En "Animales", presione ⋮ → "Cargar estados".
2. Descargue la plantilla ("Descargar template CSV" o "Descargar template Excel"), complétela, selecciónela en "Archivo*" y presione "Subir Archivo".

![](img/38-carga-masiva-estados.png)

*Imagen 47: Carga masiva de estados reproductivos.*

| Columna | Qué es | Obligatoria | Valores aceptados |
|---|---|---|---|
| IdCaravana | Identificador del animal. | Una de las dos | Identificador existente. |
| Alias | Nombre del animal. | Una de las dos | Alias existente. |
| Estado | Estado reproductivo. | Sí | VACIA, GESTANTE, CELO, ANESTRO, INSEMINADA o PARIDA. |
| FechaUltimoParto | Fecha del último parto. | No | Formato AAAA-MM-DD, no futura. |
| FechaUltimaInseminacion | Fecha de la última inseminación. | No | Formato AAAA-MM-DD, no futura. |

**Resultado esperado:** "Carga procesada" con "Procesados", "Exitosos" y "Fallidos". Si alguna fila tiene errores, no se guarda ningún registro y se informan los "Errores:" fila por fila.

## 4.9. Salud

### 4.9.1. Visualización del estado de salud

- **Listado:** la columna "Salud" de "Animales" y la vista "Salud" muestran los animales "En observación" o "Enfermo".
- **Ficha:** el indicador "Estado de salud" y el dato "Estado de Salud" de la pestaña "Ficha".
- **Alertas sanitarias del animal:** en la pestaña "Actividad", la sección "Alertas sanitarias" lista los desvíos detectados con su severidad y estado. "Ver detalle" abre la alerta.

### 4.9.2. Alta de un estado de salud

**Quién puede hacerlo:** productor y veterinario.

1. En la ficha del animal, abra "Historial clínico".
2. Presione "Registrar estado de salud".
3. Complete el formulario y presione "Registrar".

![](img/58-registrar-estado-salud.png)

*Imagen 48: Registro manual del estado de salud.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Fecha de revisión" | Día en que se revisó al animal. | No | Fecha no futura. |
| "Estado General*" | Estado del animal. | Sí | "Sano", "En observación" o "Enfermo". |
| "Diagnóstico / Notas" | Observaciones clínicas. | No | Texto de hasta 1000 caracteres. |
| "Tratamiento indicado" | Tratamiento indicado. | No | Texto de hasta 1000 caracteres. |

**Resultado esperado:** "Estado de salud registrado correctamente." El estado se ve en la ficha, en el listado y en el historial clínico.

**Errores posibles:** "El estado general es obligatorio.".

### 4.9.3. Alta de una intervención desde la ficha

**Quién puede hacerlo:** el veterinario.

1. En "Historial clínico", presione "Registrar intervención".
2. Complete el formulario y presione "Registrar".

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Tipo*" | Clase de intervención. | Sí | "Tratamiento", "Vacunación", "Revisión veterinaria" u "Otro". |
| "Fecha y hora" | Momento de la intervención. | No | Fecha y hora no futuras. |
| "Descripción*" | Qué se hizo. | Sí | Texto de hasta 1000 caracteres. |

**Resultado esperado:** "Intervención registrada correctamente.".

![](img/99-registrar-intervencion-ficha.png)

*Imagen 49: Registro de una intervención desde la ficha del animal.*

**Errores posibles:** "El tipo y la descripción son obligatorios.".

### 4.9.4. Visualización del historial clínico

1. En la ficha del animal, abra "Historial clínico".
2. Use "Tipo de evento" para mostrar u ocultar "Alertas", "Diagnósticos", "Estados de salud" e "Intervenciones", y "Período" para acotar las fechas. "Limpiar filtros" vuelve a mostrar todo.
3. Presione un evento para ver su detalle, el "Dato de origen" y quién lo registró. "Expandir todo" abre todos los eventos.

![](img/57-animal-historial-clinico.png)

*Imagen 50: Historial clínico del animal.*

El historial no se puede modificar ni borrar: solo se agregan registros nuevos.

### 4.9.5. Diagnósticos

**Quién puede hacerlo:** el veterinario.

Presione "Diagnósticos" en el menú. La pantalla lista todos los diagnósticos con "Fecha", "Animal", "Establecimiento", "Diagnóstico", "Indicación o tratamiento", "Veterinario" y la "Alerta" asociada. Puede buscar por nombre o caravana y filtrar por establecimiento. Los diagnósticos se cargan desde una alerta sanitaria (ver 4.10.5).

![](img/85-diagnosticos.png)

*Imagen 51: Listado de diagnósticos.*

## 4.10. Alertas

### 4.10.1. Visualización del histórico de alertas

**Quién puede hacerlo:** productor, veterinario y campero.

1. Presione "Alertas". Se abre "Histórico de Alertas".
2. Use los filtros:
   - "Estado": "Pendientes" (opción por defecto), "Confirmadas", "Descartadas" o "Todos los estados".
   - "Tipo": "Celo", "Salud" o "Geoespacial".
   - "Severidad": "Alta", "Media" o "Baja".
3. Las alertas se ordenan por severidad; haga clic en un título de columna para ordenar por otro dato.
4. Presione "Ver Detalle" para abrir una alerta, o "Refrescar" para actualizar la lista.

![](img/60-alertas-historico.png)

*Imagen 52: Histórico de alertas.*

Las alertas nuevas aparecen solas, sin recargar la página. También se ven en el panel "Alertas" del mapa (ver 4.11.2) y en la "Cola de atención" del Dashboard (ver 4.12).

### 4.10.2. Visualización del detalle de una alerta

El detalle se abre a la derecha de la pantalla. Arriba muestra el animal, la severidad y la fecha de detección; "Ver ficha del animal" abre su ficha. Todas las alertas incluyen el "Dato de origen" (qué detectó VigIA) y la "Acción recomendada". El contenido varía según el tipo:

**Alerta de celo.** Muestra la "Ventana de inseminación" con su hora de inicio y de fin, una barra de progreso y un contador:

- antes de que abra la ventana: "Tiempo hasta inicio de ventana óptima";
- con la ventana abierta: "Tiempo restante en ventana óptima";
- en las últimas 2 horas, destacado: "Menos de 2 horas — actuar con urgencia";
- vencida: "Ventana vencida — La oportunidad de inseminación ha expirado".

También muestra "Información adicional" y los "Últimos estados reproductivos".

![](img/61-alerta-celo-detalle.png)

*Imagen 53: Detalle de una alerta de celo con la ventana de inseminación.*

**Dato posiblemente desactualizado.** Si la caravana del animal no transmite hace más de 2 horas, la alerta muestra "Dato posiblemente desactualizado" y, en los listados, "Sin señal …". Verifique el animal en el campo antes de inseminar.

![](img/63-alerta-dato-desactualizado.png)

*Imagen 54: Alerta de celo con dato posiblemente desactualizado.*

**Alerta sanitaria.** Muestra la severidad, la "Posible causa" y gráficos que comparan el "Comportamiento actual vs. perfil de referencia" del animal (nivel de movimiento y distancia recorrida por bloques de 6 horas).

![](img/64-alerta-sanitaria-detalle.png)

*Imagen 55: Detalle de una alerta sanitaria.*

**Alerta de salida del perímetro.** Muestra el "Detalle de salida del perímetro": "Tiempo fuera del perímetro" (actualizado en vivo), "Hora de salida", "Lote / Rodeo" y "Última posición conocida", con el enlace "Ver en Google Maps".

![](img/65-alerta-fuera-de-perimetro.png)

*Imagen 56: Detalle de una alerta de salida del perímetro.*

**Alerta de aislamiento social.** Muestra el "Tiempo acumulado fuera del grupo" en las últimas horas y la lista de animales que se alejaron, con el tiempo fuera y la distancia al grupo. Si varios animales se alejaron juntos, aparecen en una sola alerta.

![](img/66-alerta-aislamiento.png)

*Imagen 57: Detalle de una alerta de aislamiento.*

**Alerta de anestro.** Indica que la vaca superó los días sin ciclo configurados (ver 4.3.3). El "Dato de origen" informa cuántos días lleva sin ciclo registrado y la acción recomendada es la revisión veterinaria. No tiene ventana de inseminación. Se confirma con "Confirmar anestro".

![](img/67-alerta-anestro.png)

*Imagen 58: Detalle de una alerta de anestro.*

### 4.10.3. Confirmar un celo

**Quién puede hacerlo:** productor, veterinario y campero.

1. Abra el detalle de la alerta de celo.
2. Opcionalmente, escriba una nota en "Notas" (hasta 200 caracteres).
3. Elija una opción:
   - "Confirmar celo": registra el celo en el historial reproductivo del animal.
   - "Confirmar celo e inseminación": confirma el celo y abre el panel "Registrar intervención".
4. En "Registrar intervención", complete el tipo y la descripción y presione "Registrar intervención". Este paso es opcional: puede presionar "Cancelar" y la alerta igualmente queda confirmada.

![](img/62-alerta-celo-acciones.png)

*Imagen 59: Opciones para confirmar o descartar una alerta de celo.*

![](img/97-registrar-intervencion.png)

*Imagen 60: Registro de la intervención al confirmar un celo.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Tipo de intervención" | Qué se hizo. | Sí | "Tratamiento", "Vacunación", "Revisión veterinaria", "Inseminación" u "Otro". |
| "Estado de salud resultante" | Solo para "Revisión veterinaria". | No | "Sin cambio de estado", "Sano", "En observación" o "Enfermo". |
| "Descripción" | Detalle de la intervención. | Sí | Texto de hasta 500 caracteres. |
| "Fecha y hora de intervención" | Momento de la intervención. | No | Fecha y hora. Si no se indica, se usa la actual. |

**Resultado esperado:** la alerta deja de estar pendiente. Si el tipo es "Inseminación", el estado reproductivo del animal pasa a "Inseminada". Si es una "Revisión veterinaria" con estado de salud, ese estado se agrega al historial de salud.

**Errores posibles:** el botón "Registrar intervención" se habilita recién cuando completa el tipo y la descripción ("Completá el tipo y la descripción para registrar la intervención"). Cada alerta admite una sola intervención: si ya tiene una, VigIA muestra el error.

### 4.10.4. Confirmar o descartar las demás alertas

**Quién puede hacerlo:** productor y veterinario, en todas las alertas; el campero, en todas menos las sanitarias.

1. Abra el detalle de la alerta.
2. Opcionalmente, escriba una nota en "Notas". Al descartar, la nota queda como motivo.
3. Presione el botón de confirmación ("Confirmar alerta", "Confirmar salida", "Confirmar aislamiento" o "Confirmar anestro") o "Descartar alerta".

**Resultado esperado:** la alerta deja de estar pendiente. En el detalle queda registrado quién la procesó y cuándo.

**Errores posibles:** "Alerta ya procesada — Esta alerta ya fue procesada por otro usuario.": otra persona la resolvió antes; el detalle se actualiza con el estado real. "Esta alerta ya fue archivada porque venció la ventana de inseminación.": la ventana del celo terminó (ver Anexo A). "No se pudo actualizar la alerta. Intentá de nuevo.": reintente.

### 4.10.5. Registrar un diagnóstico de una alerta sanitaria

**Quién puede hacerlo:** el veterinario.

1. Abra el detalle de la alerta sanitaria.
2. Presione "Confirmar alerta". El detalle queda abierto.
3. En "Cargar diagnóstico", complete "Diagnóstico" e "Indicación o tratamiento".
4. Presione "Registrar diagnóstico".

![](img/98-registrar-diagnostico.png)

*Imagen 61: Carga del diagnóstico de una alerta sanitaria.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Diagnóstico" | Diagnóstico del veterinario. | Sí | Texto de hasta 200 caracteres. |
| "Indicación o tratamiento" | Tratamiento indicado. | No | Texto de hasta 500 caracteres. |

**Resultado esperado:** el diagnóstico aparece en "Diagnósticos de esta alerta", en el historial clínico del animal y en la sección "Diagnósticos". Un diagnóstico no se puede editar.

**Errores posibles:** "Ingresá el diagnóstico.".

### 4.10.6. Avisos por correo electrónico

Cada vez que VigIA genera una alerta nueva, envía un correo "Alerta … en VigIA: …" a todos los usuarios del establecimiento, aunque no tengan la aplicación abierta. El correo incluye un enlace a la sección "Alertas". Los cambios de estado de una alerta no generan correos.

## 4.11. Mapa

### 4.11.1. Visualización del mapa del rodeo

**Quién puede hacerlo:** productor, veterinario y campero.

1. Presione "Mapa". Cada animal aparece con un pin en su última posición. Los pines se mueven solos cuando llegan datos nuevos.
2. El color del pin indica el estado del animal: normal, con alerta de severidad media o baja, crítico (alerta de severidad alta) o sin datos recientes (no transmite hace más de 15 minutos).
3. Arriba a la derecha, el resumen indica cuántas alertas activas hay de tipo "Reproductivo", "Sanitario" y "Geoespacial".
4. Haga clic en un pin para abrir el panel del animal: "Raza", "Lote/Rodeo", "Nacimiento", "Estado Reproductivo", "Última posición" y sus alertas recientes. "Ver ficha completa" abre la ficha del animal.

![](img/20-mapa-rodeo.png)

*Imagen 62: Mapa del rodeo.*

![](img/22-mapa-panel-animal.png)

*Imagen 63: Panel del animal en el mapa.*

### 4.11.2. Panel de alertas del mapa

1. Presione el botón "Alertas" del mapa.
2. Filtre por tipo con "Celo", "Salud" o "Geoespacial", y por severidad con "Filtrar por Severidad (Todas)".
3. Las tarjetas muestran la acción recomendada o el tiempo fuera del perímetro. Si varios animales salieron del perímetro en horarios cercanos, se agrupan.
4. Haga clic en una tarjeta para abrir el detalle de la alerta (ver 4.10.2).

![](img/21-mapa-panel-alertas.png)

*Imagen 64: Panel de alertas del mapa.*

### 4.11.3. Mapa de calor

1. Presione el botón "Mapa de calor" y active el interruptor del panel "Mapa de Calor".
2. En "ALCANCE", elija "Todo el rodeo", "Rodeo" (y luego el rodeo) o "Animal" (puede buscar por caravana).
3. Si eligió un rodeo, puede activar "Perímetro del rodeo" para ver sus perímetros.
4. En "PERÍODO", elija "7 días", "30 días", "Este mes" o un rango en el calendario. "Restablecer" vuelve a los valores iniciales.

![](img/25-mapa-de-calor.png)

*Imagen 65: Mapa de calor del rodeo.*

Si no hay datos para el filtro elegido, el panel indica "Sin datos para el filtro elegido.".

### 4.11.4. Perímetros

**Quién puede hacerlo:** el productor. El veterinario y el campero pueden ver los perímetros, pero no crearlos, modificarlos, activarlos ni eliminarlos.

**Visualización**

1. Presione el botón "Perímetros" del mapa. El panel lista cada perímetro con sus rodeos asignados y su tolerancia ("Zona estricta" o "Tolerancia … m").

![](img/23-mapa-perimetros.png)

*Imagen 66: Panel de perímetros.*

**Alta**

1. En el panel "Perímetros", presione "Nuevo".
2. Haga clic sobre el mapa para marcar cada vértice del perímetro (mínimo 3). Puede arrastrar un vértice para moverlo y quitarlo con clic derecho o con "Eliminar punto".
3. Complete el formulario "Nuevo Perímetro".
4. Presione "Terminado".

![](img/24-perimetro-nuevo-edicion.png)

*Imagen 67: Dibujo de un perímetro nuevo.*

| Campo | Qué es | Obligatorio | Valores aceptados |
|---|---|---|---|
| "Nombre del Perímetro" | Nombre para identificarlo. | Sí | Texto ("El nombre del perímetro no puede estar vacío"). |
| "Descripción" | Comentario opcional. | No | Texto de hasta 500 caracteres. |
| "Rodeos asignados" | Rodeos que deben permanecer dentro. | No | Uno o más rodeos activos. |
| "Zona estricta (sin tolerancia)" | Alerta apenas el animal sale. | No | Marcado o no. |
| "Distancia de tolerancia (metros)" | Margen antes de alertar. | Sí, si no es zona estricta | Número mayor o igual a 0. |
| Vértices | Puntos dibujados en el mapa. | Sí | Al menos 3 ("El perímetro debe tener al menos 3 puntos"). |

**Modificación:** presione el lápiz ("Editar perímetro"), modifique los datos o mueva los vértices y presione "Terminado". "Cancelar" descarta los cambios.

**Activación y desactivación:** use la casilla de cada perímetro ("Activar perímetro" / "Desactivar perímetro"). Un perímetro desactivado no genera alertas.

**Baja:** presione la papelera ("Eliminar perímetro") y confirme con "Eliminar". Esta acción no se puede deshacer.

**Errores posibles:** "La distancia de tolerancia debe ser un número mayor o igual a 0"; "Error guardando perímetro"; "No se pudo eliminar el perímetro.".

## 4.12. Dashboard

**Quién puede hacerlo:** productor, veterinario y campero.

Presione el logo de VigIA o ingrese a la plataforma. El Dashboard tiene tres pestañas. Arriba a la derecha puede filtrar por lote ("Lote: todos"), elegir el período ("Últimos … días", en las pestañas que lo usan) y presionar "Refrescar".

### 4.12.1. Centro de mando ("Requiere atención hoy")

- Indicadores: alertas pendientes, "Ventanas abiertas", "Bajo observación" y "Cobertura de caravanas".
- "Cola de atención": las alertas pendientes ordenadas por urgencia, con el animal, la acción recomendada, el tipo y la antigüedad. "Atender" abre el animal; "Ver todas" lleva a "Alertas".
- "Ventanas de inseminación abiertas": tiempo restante de cada celo, destacando las que vencen pronto.
- "Estado del monitoreo": porcentaje del rodeo vigilado, animales en calibración, "Sin señal > 6h", "Batería ≤ 20%" y "Sin caravana".
- "Mapa en vivo" ("Expandir" abre el mapa) y "Embudo reproductivo".

(Ver Imagen 12.)

### 4.12.2. Salud y reproducción

Indicadores del rodeo ("Días abiertos (promedio)", ventanas perdidas, "Salud sin atender", "Celos sin atender", "En calibración"), "Embudo reproductivo" con las "Excepciones que necesitan decisión", "Animales a revisar", "Partos esperados" ("Próximas 12 semanas" o "Próximo año"), "Desempeño reproductivo", "Reincidencia", "Última intervención" y "Ganancia de peso".

Al presionar un número o una barra, VigIA abre el listado de "Animales" filtrado con esos animales.

![](img/11-dashboard-salud-reproduccion.png)

*Imagen 68: Dashboard, pestaña Salud y reproducción.*

### 4.12.3. Resumen ejecutivo

"Información por rodeo" (animales, cobertura, alertas pendientes, alertas cada 100 animales, ventanas perdidas), "Tendencia de alertas · 8 semanas", "¿Está actuando el equipo?" ("En cola", "Atendidas", "Descartadas" y tiempos de atención), "Actividad del rodeo", "Dónde ocurren" y "Carga animal".

![](img/12-dashboard-resumen-ejecutivo.png)

*Imagen 69: Dashboard, pestaña Resumen ejecutivo.*

# Anexo A. Funcionamiento automático

**Calibración de los primeros días.** Cada vaca se compara contra sí misma. Durante los primeros 7 días desde que se le vincula una caravana (o desde su alta, la fecha más reciente), VigIA aprende cómo se mueve normalmente ese animal. Mientras tanto:

- la ficha muestra "Período de calibración en proceso" y cuántos días faltan;
- no se generan alertas sanitarias para ese animal;
- el "Índice de cohesión" muestra "Disponible al finalizar la calibración.";
- el Dashboard lo cuenta en "En calibración".

Las alertas de celo, de salida del perímetro y de aislamiento funcionan desde el primer día. El perfil de cada animal se vuelve a calcular cada 30 días.

![](img/59-animal-en-calibracion.png)

*Imagen 70: Aviso de período de calibración en la ficha del animal.*

**Generación de alertas.** VigIA analiza la información de las caravanas en forma periódica y genera:

| Alerta | Cuándo se genera | Severidad |
|---|---|---|
| Celo | Al detectar un aumento brusco y sostenido de la actividad del animal. El estado reproductivo pasa a "Posible celo". También se genera al registrar a mano el estado "Celo". | Alta |
| Sanitaria | Al detectar un desvío del comportamiento respecto del perfil del animal, evaluado en períodos de 24 horas. | Alta, media o baja, según el desvío |
| Salida del perímetro | En el momento en que el animal queda fuera de todos los perímetros activos de su rodeo, considerando la tolerancia. Mientras sigue afuera, la alerta actualiza su posición. | Alta |
| Aislamiento social | Cuando un animal (o un grupo) permanece alejado del rodeo más que la distancia configurada durante un tiempo mínimo. | Alta si se alejó más del doble de la distancia configurada; si no, media |
| Anestro | Cuando una vaca supera los días sin celo, inseminación ni parto configurados (por defecto, 45). | Según configuración (por defecto, media) |

**Ventana de inseminación.** Va de 6 a 24 horas desde la detección del celo. Cuando la ventana vence sin que nadie procese la alerta, VigIA la archiva sola y la registra como oportunidad no aprovechada.

**Anestro.** Si se registra un ciclo (celo, inseminación o parto), la alerta de anestro pendiente se archiva sola. Si usted la descarta, VigIA no vuelve a alertar anestro para esa vaca durante 21 días.

**Dato desactualizado.** Si la caravana de un animal no transmite hace más de 2 horas, sus alertas de celo muestran "Dato posiblemente desactualizado". En el mapa, el pin pasa a "sin datos recientes" a los 15 minutos sin posición. Revise la caravana (batería, colocación) y verifique el animal en el campo.

**Tiempo real.** Las alertas nuevas, los cambios de estado de las alertas y las posiciones del mapa se actualizan sin recargar la página. Si dos personas procesan la misma alerta, solo vale la primera.

**Sesión.** La sesión se cierra sola tras 60 minutos sin uso. Cinco contraseñas incorrectas seguidas bloquean la cuenta durante 30 minutos.

# Anexo B. Preguntas frecuentes y solución de problemas

**Di de alta un animal hace unos días y no tiene alertas sanitarias. ¿Funciona?**
Sí. Durante los primeros 7 días el animal está en calibración y no genera alertas sanitarias (ver Anexo A). La ficha indica cuántos días faltan.

**Un animal aparece gris en el mapa o con "Sin señal".**
Su caravana no transmite. Revise que la caravana esté bien colocada y con batería (el listado de "Animales" muestra la batería y la última señal). Si el problema sigue, avise al administrador.

**La alerta de celo dice "Dato posiblemente desactualizado". ¿La confirmo?**
Antes de inseminar, verifique al animal en el campo: VigIA no recibe datos de su caravana hace más de 2 horas.

**El archivo de carga masiva fue rechazado.**
Lea el "Detalle de errores": indica la fila y el motivo. Los casos más comunes son fechas en otro formato (use AAAA-MM-DD), lotes que no existen (créelos antes con ⋮ → "Rodeo") e identificadores repetidos. Si una sola fila falla, no se guarda ninguna: corrija y vuelva a subir el archivo completo. El archivo debe ser .csv, .xlsx o .xls y pesar menos de 5 MB.

**No encuentro un animal en el listado.**
Verifique el establecimiento elegido en la barra superior, borre el texto del buscador y vuelva a la vista "General". Los animales dados de baja no se muestran.

**No me deja confirmar una alerta sanitaria.**
Si usted es campero, las alertas sanitarias las procesan el productor o el veterinario.

**Quiero cambiar con qué sensibilidad se detecta el aislamiento o el anestro.**
Desde el engranaje → "Configuración del establecimiento" → "Aislamiento" o "Anestro" (ver 4.3).

**Mi cuenta está bloqueada.**
Espere 30 minutos o contacte al administrador.

**La invitación expiró.**
Las invitaciones duran 7 días. Pida a quien lo invitó que elimine la anterior y genere una nueva.

**No recibí el correo de invitación.**
Revise la carpeta de correo no deseado y confirme con quien lo invitó que el correo esté bien escrito.

# Anexo C. Glosario

| Término | Significado |
|---|---|
| Alerta | Aviso de VigIA sobre un animal que requiere atención: celo, salud, perímetro, aislamiento o anestro. |
| Anestro | Período en que una vaca no muestra ciclos reproductivos (no entra en celo). |
| Calibración | Primeros 7 días de un animal con caravana, en los que VigIA aprende su comportamiento normal. |
| Caravana | Dispositivo electrónico que se coloca en la oreja del animal, con sensor de movimiento y GPS. |
| Celo | Período en que la vaca es receptiva y puede ser inseminada. |
| Cohesión (índice de) | Medida de cuán cerca del rodeo se mueve un animal. |
| Dato desactualizado | Situación en que la caravana no transmite hace más de 2 horas. |
| Dev EUI | Identificador electrónico único de una caravana. |
| Establecimiento | Campo o tambo registrado en VigIA. |
| Intervención | Acción realizada sobre un animal: tratamiento, vacunación, revisión veterinaria, inseminación u otra. |
| Lote / Rodeo | Grupo de animales dentro del establecimiento. En VigIA se usan como sinónimos. |
| Mapa de calor | Mapa que colorea las zonas donde más tiempo pasaron los animales. |
| Perímetro | Zona del campo dibujada en el mapa; VigIA avisa si un animal sale de ella. |
| Perfil de referencia | Comportamiento normal de un animal, calculado durante la calibración. |
| Severidad | Nivel de urgencia de una alerta: alta, media o baja. |
| Tolerancia | Distancia en metros que un animal puede alejarse del perímetro antes de que se genere la alerta. |
| Ventana de inseminación | Período de 6 a 24 horas desde la detección del celo en que conviene inseminar. |
| Zona estricta | Perímetro sin tolerancia: alerta apenas el animal sale. |
