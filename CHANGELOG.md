## 2.1.0
- Zoom por pinça e botões nos editores de Word e PDF.
- Navegação via bookmarks internos do PDF e comando Ir para página.
- PDFBox para leitura estrutural de índices.

# Histórico de versões

## 2.0.0 — evolução do projeto

- Word: inserção de títulos, tabelas DOCX simples, localizar e substituir.
- Excel: livros com até 32 abas, alternar/criar/renomear/excluir; abertura e salvamento de múltiplas abas.
- Fórmulas: SE/IF, ARRED/ROUND, ABS, RAIZ/SQRT, POTENCIA/POWER, E/AND, OU/OR, comparações, potência e percentual.
- XLSX: conversão de alguns nomes de funções para formato internacional e valores brasileiros com vírgula decimal para números OOXML.
- CSV: leitura com separador vírgula ou ponto e vírgula.
- PDF: anotações de texto, desenho com caneta e marca-texto (exportação rasterizada).
- Testes: 20 verificações V2, testes de núcleo anteriores, verificação sintática Android e interoperabilidade com openpyxl/python-docx.
- CI: workflow GitHub Actions para testar e compilar APK debug.

**Não validado:** compilação Android e testes em smartphone real; a plataforma SDK Android não estava disponível no ambiente de desenvolvimento.

## 1.0.0 — MVP

- DOCX/TXT, XLSX/CSV e visualizador/anotador básico de PDF.
