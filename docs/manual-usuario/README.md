# Manual de Usuario

| Archivo | Qué es |
|---|---|
| `manual.md` | Fuente del Manual de Usuario (Sección V de la carpeta final). |
| `inventario.md` | Relevamiento de funcionalidades, roles, Historias de Usuario y tests e2e que sirvió de base al manual. |
| `manual-docx.lua` | Filtro de pandoc que arma el .docx: portada e historial fuera del índice, índice después del historial, un salto de página por capítulo e imágenes al ancho de la página. |

Las capturas (`img/`) y el `.docx` generado no se versionan (ver `.gitignore`): el manual completo va a la carpeta final del proyecto. Por eso, en GitHub, `manual.md` se ve sin imágenes.

## Generar el .docx

Con las capturas en `img/` y [pandoc](https://pandoc.org) 3 o posterior, desde esta carpeta:

```bash
pandoc manual.md -f markdown-implicit_figures -t docx -o manual.docx --resource-path=. -M lang=es-AR --lua-filter=manual-docx.lua
```

El índice queda como un campo de Word: al abrir el archivo, hacé clic derecho sobre él y elegí "Actualizar campos".
