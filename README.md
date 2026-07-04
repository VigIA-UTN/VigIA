# VigIA

## Link al Drive

https://drive.google.com/drive/folders/1OaNcqx1iyuzeKOyNrNP1Tt-S7YMCXce3

## Paso a Paso para Levantar el Entorno

### 1. Levantar la Base de Datos
Siempre que vayas a trabajar en el backend, necesitás la base de datos encendida:
1. Abrí una terminal en la carpeta `/infra`.
2. Ejecutá el siguiente comando (el `-d` es para que corra en segundo plano):
   ```bash
   docker-compose up -d db
   ```

### 2. Levantar el Backend (Spring Boot)
Para aprovechar el autocompletado y el live-reload de tu entorno:
- **Opción A (Recomendada):** Abrí la carpeta `/backend-core` en tu IDE (IntelliJ, VSCode, etc.) y dale 'Run' a la clase principal.
- **Opción B (Terminal):** Abrí una terminal en `/backend-core` y ejecutá:
   ```bash
   mvn spring-boot:run
   ```
*(Tu backend estará disponible en `http://localhost:8080`).*

### 3. Levantar el Frontend
Si vas a trabajar en la interfaz visual:
1. Abrí una terminal en la carpeta `/frontend`.
2. Si es la primera vez que lo abrís, instalá las dependencias:
   ```bash
   npm install
   ```
3. Iniciá el servidor de desarrollo:
   ```bash
   npm run dev
   ```
*(La app estará disponible en `http://localhost:5173`).*

### 4. Apagar el Entorno
Cuando termines de trabajar, así es como bajás todo para liberar recursos en tu PC:
- **Frontend / Backend en Terminal**: Hacé clic en la terminal que lo está corriendo y presioná `Ctrl + C` (si te pregunta "Terminar el trabajo por lotes", apretá la tecla `S` o `Y`).
- **Backend en IDE**: Apretá el botón rojo de "Stop" (con forma de cuadrado) en tu editor.
- **Base de Datos (Docker)**: Abrí una terminal en `/infra` y ejecutá el siguiente comando para apagar los contenedores:
   ```bash
   docker-compose down
   ```

---

### 🚀 Extras: Microservicios de Machine Learning
Si necesitás que el backend se comunique con los módulos de Python, podés levantar todo el ecosistema de dependencias rápidamente. Desde la carpeta `/infra` ejecutá:
```bash
docker-compose up -d db ml-celo ml-salud ml-geo
```
