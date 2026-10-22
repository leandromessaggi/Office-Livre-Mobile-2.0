# Office Livre Mobile 2.2 — Zoom e índice de PDFs

Aplicativo Android nativo (Java 17, Android API 26+), com editor básico de DOCX/TXT, planilhas XLSX/CSV e leitura/anotação de PDFs. O código desta versão acrescenta zoom nos documentos e PDFs, além de leitura de marcadores (bookmarks) de PDFs.

> **Estado:** o código passou pelos testes Java do núcleo e por verificação sintática dos novos arquivos Android. **O APK 2.1 ainda precisa ser compilado pelo GitHub Actions e testado no telefone**. Não confundir com o APK 2.0 já produzido anteriormente.

## Novidades 2.1

### Documentos Word / DOCX

- Botões de visualização `−`, `+` e `100%`.
- Gesto de pinça com dois dedos dentro do editor.
- Zoom de **60% a 240%**, sem inserir essa ampliação nas propriedades do DOCX exportado.
- Títulos e tamanhos de fonte importados são representados proporcionalmente ao tamanho-base para acompanhar o zoom.
- `A+` e `A−` continuam sendo **tamanho-base da fonte do documento**, não o zoom. O tamanho-base pode influenciar os tamanhos exportados.
- Limitação: o editor ainda usa texto refluído; **não reproduz com fidelidade a paginação/imagens/layout avançado do Word**.

### PDF

- Botões `−`, `+`, `Ajustar` e ampliação de **75% a 350%**.
- Gesto de pinça sobre a página e rolagem vertical e horizontal para inspecionar a página ampliada.
- Controle `Índice / sumário` que lê os **bookmarks (outline) existentes no PDF**, incluindo subníveis, e abre a página vinculada.
- Controle `Ir para…` para informar o número da página — útil também em PDFs sem bookmarks.
- O zoom preserva as posições proporcionais das anotações. Em zoom por pinça, um traço iniciado pelo primeiro dedo é cancelado se o segundo dedo entrar.
- Os bookmarks são lidos em uma thread separada para não travar a interface.

**Importante:** índice navegável significa *bookmarks internos*, não a leitura automática de sumários impressos no conteúdo. Alguns PDFs têm um sumário desenhado nas páginas, mas nenhum bookmark. Nesse caso, a lista de índice ficará vazia e ainda será possível navegar por número de página. PDFs criptografados/complexos podem não permitir extração de marcadores.

A exportação atual de anotações **rasteriza todo o PDF**. Ela **não preserva bookmarks, links, assinaturas, formulários, texto selecionável** ou garantias de redação segura. Mantenha o original.

### Biblioteca acrescentada

Leitura dos bookmarks por [PdfBox-Android 2.0.27.0](https://github.com/TomRoush/PdfBox-Android), licença **Apache 2.0**. O Android `PdfRenderer` continua responsável pela renderização de páginas. O aplicativo funciona offline, mas a **compilação** exige que o Gradle baixe a dependência do Maven Central. A biblioteca é inicializada via `PDFBoxResourceLoader.init()`.

## Testes locais sem Android

Na raiz do projeto, em ambiente com JDK 17+:

```sh
bash test-core.sh
```

Testa o núcleo de documentos/planilhas e o parse sintático dos arquivos `MainActivity.java`, `PdfPageView.java`, `DocZoomEditText.java` e `PdfOutlineReader.java`. **Isso não substitui compilar com Android SDK e abrir o aplicativo no dispositivo.**

## Atualizar o repositório GitHub usando Termux

1. Baixe `OfficeLivreMobile-v2.1-projeto.zip` para a pasta **Downloads** no Android.
2. Se ainda não autorizou arquivos no Termux, rode `termux-setup-storage`.
3. Execute os comandos:

```sh
cd ~/Office-Livre-Mobile-2.0
git pull --ff-only origin main
mkdir -p ~/office-v21-temp
unzip -o ~/storage/downloads/OfficeLivreMobile-v2.1-projeto.zip -d ~/office-v21-temp
cp -a ~/office-v21-temp/OfficeLivreMobile-v2.1/. .
git add -A
git commit -m "Office Livre 2.1: zoom e índice PDF"
git push origin main
```

Se o GitHub não reconhecer os comandos, confira `gh auth status`. **Não execute um `git push --force`**. O fluxo `.github/workflows/android-apk.yml` já está ajustado para `android-actions/setup-android@v4` com `packages: 'platform-tools'`, corrigindo a falha anterior causada pelo pacote Android `tools` obsoleto.

Depois do push, abra [Actions do repositório](https://github.com/leandromessaggi/Office-Livre-Mobile-2.0/actions), aguarde a nova execução **Gerar APK Android** com sucesso e baixe o ZIP `OfficeLivre-Android-APK` em **Artifacts**. Extraia `app-debug.apk`.

Se o Android rejeitar a atualização por assinatura diferente, é provável que o GitHub Actions tenha gerado uma nova chave **debug**. Antes de desinstalar a versão anterior, exporte/salve todos os documentos importantes; desinstalar um app apaga seus dados internos. A edição release assinada com chave estável não foi configurada.

## Verificações recomendadas no celular

1. Criar texto, aplicar título e verificar zoom de 100% → 160% → 100%; salvar em DOCX e reabrir (zoom não deve mudar os tamanhos gravados).
2. Abrir PDF com marcadores e navegar por `Índice / sumário` (inclusive subcapítulos).
3. Abrir PDF *sem* bookmarks e confirmar a mensagem e a opção `Ir para…`.
4. Abrir PDF, marcar com caneta, ampliar, reduzir e verificar se as anotações seguem a posição certa.
5. Testar zoom com dois dedos; trocar de página e retornar.
6. Testar importação/exportação de XLSX, que não sofreu mudanças funcionais na 2.1.

## Compatibilidade / limitações conhecidas

- Word: formatação e tabelas simples; sem paginação WYSIWYG, imagens ou tabelas complexas preservadas.
- Excel: até 32 abas, fórmulas básicas, CSV e XLSX; sem gráficos, macros ou preservação de estilos avançados.
- PDF: navegadores de bookmarks e página, anotações rasterizadas; sem OCR, edição do texto subjacente nem navegação automática pelo índice impresso na página.
- O pacote 2.1 **não inclui APK pré-compilado**.


## Correções 2.2: zoom e links
- No PDF, o botão Índice fica visível e mostra quantos marcadores foram encontrados.
- Os links internos da primeira página (sumário) e o link “Voltar ao índice”
  navegam diretamente no PDF original, sem abrir o diálogo de anotação.
- Use pinça para ampliar de 75% a 350%, +/− para controlar, e arraste
  a página livremente (horizontal e vertical).
- Word tem botões permanentes de zoom 60%-240% e pinça com dois dedos.
- Para testar: abra um PDF com links/marcadores e confirme se a faixa “Índice”
  mostra a quantidade e se os links respondem.
