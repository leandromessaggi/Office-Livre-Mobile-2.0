# Office Livre Mobile 2.0 — Android offline

Aplicativo de escritório Android, desenvolvido em **Java 17 + Android SDK nativo**, sem serviços pagos, contas, internet, anúncios ou permissões irrestritas de arquivos. Inclui editor básico de documentos, editor de planilhas e anotações sobre PDFs.

**Estado da entrega:** código-fonte + testes + modelos DOCX/XLSX + automação para gerar um `app-debug.apk` pelo GitHub Actions. **O APK não está incluído e a interface Android não foi compilada ou executada neste ambiente**, porque não há Android SDK/Gradle instalados nem acesso de rede para baixá-los. Não é um substituto completo do Microsoft Office/Acrobat.

## Evolução 2.0

| Área | Recursos desta versão | Limitações relevantes |
|---|---|---|
| Word / DOCX | Escrever e formatar negrito, itálico e sublinhado; **títulos com tamanho destacado**; **inserir e editar tabelas simples**; **localizar e substituir**; TXT; exportar PDF básico | O conteúdo das tabelas é mostrado no editor Android como linhas com tabulações, mas salvo em DOCX como tabelas reais `<w:tbl>`. Não preserva imagens, cabeçalhos, rodapés, complexidade de tabelas mescladas, estilos avançados, layouts nem comentários. Exportação para PDF só usa texto sem estilos avançados. |
| Excel / XLSX | **Múltiplas abas (até 32)**, criar, renomear, selecionar e excluir abas; células, fórmulas, importação e exportação XLSX; exportação CSV da aba ativa; importação CSV separada por vírgula ou ponto e vírgula | Estilos, gráficos, macros, imagens, proteção, validações, filtros, células mescladas, tabelas dinâmicas e comentários **não são preservados**. O parser suporta só subconjunto de fórmulas e não referências entre abas. Fórmulas incompatíveis mostram `#ERRO`. Nunca sobrescreva um XLSX complexo. |
| Fórmulas | `SOMA/SUM`, `MÉDIA/MEDIA/AVG/AVERAGE`, `MIN`, `MAX`, `CONTAR/COUNT`, **`SE/IF`, `ARRED/ROUND`, `POTENCIA/POWER`, `RAIZ/SQRT`, `ABS`, `E/AND`, `OU/OR`**, operadores aritméticos, `^`, `%`, `=`, `<`, `>`, `>=`, `<=`, `<>`, referências A1 e intervalos | Argumentos separados por `;` ou `,`; números decimais dentro das fórmulas usam ponto (`2.5`); valores simples digitados em células aceitam vírgula (`12,5`) e são convertidos em números XLSX. Intervalos limitados a 10.000 células. Fórmulas diferentes das implementadas ou referências de outras abas não são avaliadas. |
| PDF | Abrir e navegar páginas, **inserir texto, desenhar com caneta, marcar com marca-texto**, desfazer últimos textos ou traços da página, salvar cópia PDF | **O documento é rasterizado ao exportar.** Isso pode remover pesquisa/seleção de texto, links, formulários, camadas, assinaturas e acessibilidade; não altera texto original. Não serve para tarjas/redação segura. PDF com senha não suportado. |

### O que mudou comparado à versão 1.0

- Excel não descarta todas as abas após a primeira: suporta importação/exportação de vários `xl/worksheets/sheetN.xml` e relacionamentos `workbook.xml.rels`.
- Formatação de títulos DOCX utiliza `w:sz`; tabelas simples utilizam `w:tbl`, `w:tr` e `w:tc`.
- Fórmulas condicionais possuem **avaliação preguiçosa dos ramos** (`=SE(0;A1;42)` não avalia o ramo falso). Exportação OOXML troca nomes portugueses comuns por equivalentes internacionais.
- Anotações de caneta e marca-texto usam pontos normalizados e são aplicadas à cópia exportada de todas as páginas.
- Exemplos em `samples/` permitem testar a edição e os formatos sem criar arquivos do zero.

## Gerar APK usando GitHub (inclusive pelo celular)

1. Crie um **repositório privado** no GitHub.
2. Descompacte o projeto; publique o **conteúdo da pasta** no repositório, mantendo `.github/workflows/android-apk.yml` (pasta oculta).
3. Abra **Actions → Gerar APK Android → Run workflow**. Também é possível disparar por push na branch `main` ou `master`.
4. O fluxo instala Java 17, executa os testes de núcleo, instala Android SDK 35 e Gradle 8.11.1, compila a aplicação e publica o artefato `OfficeLivre-Android-APK`.
5. Após o fluxo concluir com sucesso, baixe o artefato e extraia `app-debug.apk`. Transfira ao Android e autorize instalação por aquela origem se necessário.

**Observação:** o fluxo de CI está configurado, mas não foi executado nesta entrega. APK debug é para testes; não publique em loja sem assinatura release própria, política de privacidade e testes em aparelhos reais.

### Android Studio

Abra esta pasta no Android Studio com SDK Platform 35 e JDK 17. Sincronize Gradle e selecione **Build > Build APK(s)**. O resultado, se compilado com sucesso, ficará em `app/build/outputs/apk/debug/app-debug.apk`.

Requisitos da aplicação: `minSdk 26` (Android 8), `targetSdk 35`, Java 17, Android Gradle Plugin 8.9.2, Gradle 8.11.1. O projeto não depende de bibliotecas externas em tempo de execução.

## Testar sem Android

```bash
./test-core.sh
```

Usa `javac` e `java` JDK 17+; realiza os testes de núcleo antigos e **20 verificações novas** cobrindo fórmulas, três abas XLSX, renomeação/remoção de aba, rejeição de nomes inválidos, tabelas DOCX e títulos; gera modelos e valida **sintaxe** dos arquivos Java Android (isso **não** substitui a compilação real Android).

Na preparação da versão 2, os arquivos produzidos foram adicionalmente abertos com `openpyxl` e `python-docx`, e arquivos criados por ambas as bibliotecas foram lidos pelo parser Java. **Validação em dispositivo ainda pendente**.

## Estrutura

```text
app/src/main/java/com/officelivre/mobile/
  MainActivity.java              UI Android, seleção de arquivos, documentos/planilhas e PDF
  PdfPageView.java               Páginas PDF e desenho de anotações
  core/SheetData.java            Grade de células e CSV
  core/WorkbookData.java         Livro com múltiplas abas
  core/FormulaEngine.java        Expressões e funções de planilha
  core/OpenXml.java              Importação/exportação DOCX/XLSX
samples/
  Exemplo_Documento_Tabela.docx
  Exemplo_Multiplas_Abas.xlsx
tests/CoreTests.java            Testes da versão original
tests/V2Tests.java              Testes adicionais da versão 2
.github/workflows/android-apk.yml
```

## Privacidade e segurança

Utiliza Storage Access Framework (`ACTION_OPEN_DOCUMENT` e `ACTION_CREATE_DOCUMENT`) para escolher arquivos, sem permissão de acesso a todo o armazenamento nem à internet. Nenhum arquivo é transmitido pelo aplicativo. A importação restringe o tamanho descompactado de ZIP e bloqueia DTD/entidades XML. **Salve uma nova cópia dos arquivos importantes:** a conversão de formatos complexos é destrutiva. Para PDFs contendo informações sigilosas, não use a camada de anotação para censurar informações.

## Documentação técnica

- [Microsoft Open XML — WordprocessingML](https://learn.microsoft.com/en-us/office/open-xml/word/how-to-create-a-word-processing-document-by-providing-a-file-name)
- [Microsoft Open XML — SpreadsheetML](https://learn.microsoft.com/en-us/office/open-xml/spreadsheet/structure-of-a-spreadsheetml-document)
- [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
- [Android PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)
- [Android PdfDocument](https://developer.android.com/reference/android/graphics/pdf/PdfDocument)

A implementação foi escrita para este projeto usando essas especificações públicas; não contém código copiado de bibliotecas comerciais nem distribui dependências licenciadas de terceiros.
