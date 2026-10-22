## 2.2.0 - Leitor PDF corrigido

- PDF: zoom real no próprio Canvas, pinça, + e -, e arrasto vertical/horizontal.
- PDF: índice visível com contador de marcadores, destinos de página e indicador de falhas.
- PDF: links internos clicáveis (inclusive índice na primeira página e voltar ao índice).
- Word: controles de zoom acessíveis sem rolar barra; pinça com prioridade ao editor.
- PDFs anotados ainda são exportados como imagem, sem manter hiperlinks.
- Testes do núcleo e compilação Android pelo GitHub Actions.

# Histórico — Office Livre Mobile

## 2.1.0
- Zoom visual de 60 a 240% no editor Word, com botões e pinça de dois dedos.
- Mudança interna de tamanho de títulos e importações DOCX para `RelativeSizeSpan`, evitando que o zoom visual altere os tamanhos exportados.
- Zoom de 75 a 350% para PDF com botões e pinça; possibilidade de rolagem horizontal quando ampliado e melhora de resolução de renderização.
- Lista de índice hierárquico de PDF baseada em bookmarks existentes com suporte a títulos sem página; navegação direta por número da página.
- `PdfOutlineReader.java` adiciona leitura offline por PDFBox-Android.
- Workflow Android atualizado de `setup-android@v3` para `v4` para corrigir o pacote descontinuado `tools`.
- Preservadas funcionalidades 2.0 de Word, Excel e anotações PDF.

## 2.0.0
- Títulos, tabelas e localizar/substituir em documentos.
- Excel de múltiplas abas, novas fórmulas e CSV.
- PDFs com texto, caneta e marca-texto.
