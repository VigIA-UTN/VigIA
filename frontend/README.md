# VigIA Frontend

Aplicación web para la plataforma VigIA, que permite a los productores ganaderos visualizar y gestionar las alertas tempranas generadas por los modelos de Machine Learning (celo, problemas de salud, geofencing).

## Tecnologías Utilizadas
- **React 18**
- **TypeScript**
- **Vite**
- **React Router** para navegación
- **TailwindCSS** / **CSS** para estilos (según aplique)

## Requisitos Previos
- Node.js >= 20.x

## Instalación y Ejecución

1. Instalar dependencias:
   ```bash
   npm install
   ```

2. Levantar servidor de desarrollo:
   ```bash
   npm run dev
   ```
   El entorno de desarrollo estará disponible en `http://localhost:5173`.

## Construcción para Producción

```bash
npm run build
```
Los archivos estáticos listos para desplegar se generarán en la carpeta `dist/`.

## Linter

Para verificar la consistencia y calidad del código (este paso se ejecuta en CI):
```bash
npm run lint
```
