# Zooming Mirror

[English](../../README.md) · **Português (Brasil)**

Um espelho para Android: a câmera frontal ocupa a tela inteira e você faz
pinça para dar zoom. Não há mais nada na tela — nenhum botão, nem barra de
status ou de navegação — além de um valor enquanto ele muda (o zoom ou a luz) e
dos ajustes quando você os chama. Feito com [Expo](https://expo.dev), React
Native, TypeScript e uma pequena view em Kotlin sobre o CameraX.

## Uso

- Abra o app e permita o acesso à câmera. A câmera frontal aparece em tela
  cheia.
- **Afaste os dedos** para aproximar e **junte os dedos** para afastar, até 10×.
  O ponto entre os dedos fica parado, e mover os dois dedos desloca a imagem.
- **Arraste com um dedo** para se mover pela imagem ampliada.
- **Toque duplo** vai para 1×, e outro toque duplo volta para o zoom em que você
  estava.
- **Toque longo** congela a imagem; zoom e arraste continuam funcionando nela.
  Outro toque longo volta ao vivo.
- **Deslize para cima ou para baixo na borda direita** para clarear ou escurecer
  a imagem.
- **Arraste da borda direita para dentro**, à altura do círculo do zoom, para
  mostrar uma engrenagem; toque nela para abrir os ajustes.
- Enquanto o zoom ou a luz mudam, um círculo translúcido na parte de baixo da
  tela mostra o valor e some assim que você solta. Arrastar com um dedo nunca o
  mostra.
- A tela não apaga enquanto o app está aberto.
- Deslize a partir da borda de cima ou de baixo para mostrar as barras do
  sistema por um instante.

### Ajustes

| Ajuste                 | Padrão    | O que faz                                                          |
| ---------------------- | --------- | ------------------------------------------------------------------ |
| Luz automática         | ligado    | Com zoom, exposição e balanço de branco seguem o que está na tela. |
| Prioridade de rosto    | ligado    | A medição com prioridade de rosto da própria câmera, se houver.    |
| Alta resolução         | ligado    | Pede até 4K à câmera, em vez do padrão dela (até 1080p).           |
| Nitidez (GPU)          | desligado | Experimental: ampliação bicúbica e realce de bordas na GPU.        |
| Como os outros te veem | desligado | Mostra a imagem sem espelhar.                                      |

O que o aparelho não suporta aparece apagado. O pé da tela mostra o tamanho que
a câmera realmente entrega e a versão do app.

Se a permissão da câmera foi negada, a tela fica preta: toque nela para o
pedido aparecer de novo, ou para abrir as configurações do app quando o Android
parar de perguntar.

## Instalação

Baixe o APK da
[última release](https://github.com/fershibli/zooming-mirror/releases/latest)
e abra no celular (permita instalar dessa origem quando for perguntado).

## Desenvolvimento

```bash
npm install
npm run android   # expo run:android — precisa do Android SDK local
```

### Como o zoom funciona

A maioria das câmeras frontais não tem zoom próprio, então o app não pede zoom à
câmera. Ela continua transmitindo em 1× e o zoom amplia a imagem — igual em
qualquer celular. O stream é pedido em 16:9, para a tela em retrato cortar o
mínimo possível, e em até 4K com **Alta resolução** ligada: o CameraX limita o
preview a 1080p a menos que o app passe a própria estratégia de resolução.

A imagem chega à tela de um de dois jeitos:

- **Padrão:** a `PreviewView` do CameraX no modo `COMPATIBLE` (uma
  `TextureView`; a `SurfaceView` não acompanha transformações de view), escalada
  e movida como uma view inteira. A GPU compõe a textura da câmera direto na
  tela, então um stream grande ainda acrescenta detalhe no zoom.
- **GPU (experimental):** um passo próprio em OpenGL ES. Cada pixel da tela é
  levado pelo zoom até o quadro da câmera e amostrado com Catmull-Rom (bicúbico)
  quando amplia; um segundo passo aplica realce de bordas adaptativo ao
  contraste, mais forte quanto maior o zoom. Se o caminho da GPU não iniciar, o
  app volta ao padrão e apaga o ajuste.

Com **Luz automática**, a câmera mede exposição e balanço de branco na área
visível depois de cada gesto. Câmeras que não aceitam área de medição usam um
laço lento: ele lê o brilho do que está na tela e ajusta a compensação de
exposição. O deslizar manual na borda direita soma a própria compensação.

A view da câmera é um módulo nativo local (`modules/mirror-view`), então não
existe no Expo Go. Use o APK de uma release ou um development build.

| Comando             | O que faz                                          |
| ------------------- | -------------------------------------------------- |
| `npm run lint`      | ESLint                                             |
| `npm run typecheck` | TypeScript                                         |
| `npm run format`    | Prettier                                           |
| `npm run release`   | Script de versionamento (`-- --dry-run` p/ ensaio) |

As melhorias planejadas e possíveis ficam em [`docs/plans`](../plans), um
arquivo por plano.

### Arquitetura

- `App.tsx` — a raiz; esconde as barras do sistema e mostra o espelho.
- `src/Mirror.tsx` — pede a permissão da câmera e mostra o espelho, a
  engrenagem e os ajustes.
- `src/Settings.tsx`, `src/GearButton.tsx` — a tela de ajustes e a engrenagem
  escondida que a abre; `src/store/settings.ts` guarda os ajustes (Zustand +
  AsyncStorage).
- `modules/mirror-view/` — módulo nativo local (Kotlin):
  - `MirrorView` liga a câmera e amarra tudo;
  - `MirrorGestures` transforma toques em zoom, arraste, toque duplo,
    congelar, o deslizar da exposição e o arraste da engrenagem;
  - `PinchZoom` é o zoom em números; `PreviewRenderer` e `GlRenderer` (com
    `GlShaders`) o transformam em pixels;
  - `Exposure` mede a luz, e `ValueIndicator` é o círculo translúcido.
- `plugins/` — config plugin que injeta a assinatura de release no projeto
  Gradle gerado.

### Branches

| Branch | Papel                                          | O que dispara                                                             |
| ------ | ---------------------------------------------- | ------------------------------------------------------------------------- |
| `main` | A linha publicada. Abra os pull requests aqui. | **CI** + **Release**, que versiona, cria a tag e dispara **Android APK**. |

Pull requests rodam só o **CI** (lint, typecheck, bundle, prebuild).

### Versionamento e deploy

O versionamento roda num script próprio (`scripts/release.js`), sem
dependências externas: a cada push na `main` — ou seja, a cada pull request
mergeado — o workflow **Release** lê os conventional commits desde a última
tag, sobe o semver, grava o `versionCode` do Android
(`major * 1000000 + minor * 1000 + patch`), adiciona a seção no topo do
`CHANGELOG.md` e cria a tag anotada `v*`. Em seguida dispara o **Android APK**
via `workflow_dispatch`, que roda `expo prebuild --clean` mais o Gradle no
runner (sem EAS) e anexa o APK assinado à GitHub Release.

Só commits `feat`, `fix`, `perf`, `refactor` e `revert` geram versão; um `!`
depois do tipo ou um `BREAKING CHANGE` no corpo gera uma major. Em squash merge,
dê ao pull request um título no padrão conventional commits, porque é esse o
commit que o script lê. A primeira release usa a versão que já está no
`package.json` (`1.0.0`).

### Assinatura

`signing/temp-release.keystore` é uma **chave descartável**, versionada de
propósito para o APK sair assinado sem ninguém configurar secrets (senha do
store e da chave `android`, alias `zooming-mirror`). Serve para instalar e
atualizar o app no celular e **não serve para a Play Store** — qualquer pessoa
com acesso ao repositório consegue assinar com ela.

Para usar uma chave de verdade, configure os secrets `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`; o
workflow passa a usá-los e ignora a descartável. A assinatura muda junto, então
uma instalação feita com a chave antiga precisa ser removida antes.

## Licença

Uso pessoal.
