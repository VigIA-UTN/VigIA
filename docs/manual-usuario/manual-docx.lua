-- Filtro para generar el .docx del Manual de Usuario:
--  * la portada ("Manual de Usuario") y "Historial de Revisión" no entran en el índice;
--  * el índice (campo TOC de Word) va después del Historial de Revisión;
--  * cada capítulo (título de nivel 1) empieza en una página nueva;
--  * las imágenes ocupan el ancho de la página.

local salto = pandoc.RawBlock('openxml', '<w:p><w:r><w:br w:type="page"/></w:r></w:p>')
local indice = pandoc.RawBlock('openxml', [[
<w:p><w:pPr><w:pStyle w:val="TOCHeading"/></w:pPr><w:r><w:t>Índice</w:t></w:r></w:p>
<w:p><w:r><w:fldChar w:fldCharType="begin" w:dirty="true"/></w:r><w:r><w:instrText xml:space="preserve"> TOC \o "1-3" \h \z \u </w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>Haga clic derecho y elija "Actualizar campos" para generar el índice.</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r></w:p>]])

function Pandoc(doc)
  local out = {}
  local primero = true
  local enHistorial = false
  for _, b in ipairs(doc.blocks) do
    if b.t == 'Header' and pandoc.utils.stringify(b) == 'Manual de Usuario' then
      table.insert(out, pandoc.Div({ pandoc.Para(b.content) }, { ['custom-style'] = 'Title' }))
    elseif b.t == 'Header' and pandoc.utils.stringify(b) == 'Historial de Revisión' then
      table.insert(out, salto)
      table.insert(out, pandoc.Div({ pandoc.Para(b.content) }, { ['custom-style'] = 'Subtitle' }))
      enHistorial = true
    elseif enHistorial and b.t == 'Table' then
      table.insert(out, b)
      table.insert(out, salto)
      table.insert(out, indice)
      enHistorial = false
    elseif b.t == 'Header' and b.level == 1 then
      table.insert(out, salto)
      table.insert(out, b)
    else
      table.insert(out, b)
    end
  end
  doc.blocks = out
  return doc
end

function Image(img)
  img.attributes.width = '16cm'
  return img
end
