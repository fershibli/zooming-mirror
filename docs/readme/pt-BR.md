# Zooming Mirror

[English](../../README.md) · **Português (Brasil)**

Um espelho para Android: a câmera frontal ocupa a tela inteira e você faz
pinça para dar zoom. Não há mais nada na tela — nenhum botão, nenhuma
sobreposição, nem barra de status ou de navegação. Feito com
[Expo](https://expo.dev), React Native, TypeScript e uma pequena view em Kotlin
sobre o CameraX.

## Uso

- Abra o app e permita o acesso à câmera. A câmera frontal aparece em tela
  cheia.
- **Afaste os dedos** para aproximar e **junte os dedos** para afastar, até 10×.
  O ponto entre os dedos fica parado, e mover os dois dedos desloca a imagem.
- A tela não apaga enquanto o app está aberto.
- Deslize a partir da borda para mostrar as barras do sistema por um instante.

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
câmera. Ela continua transmitindo em 1× para uma `TextureView`, e a pinça escala
e desloca essa view — igual em qualquer celular. A `SurfaceView`, o padrão mais
rápido, não acompanha transformações de view, por isso o preview roda no modo
`COMPATIBLE` do CameraX. O stream é pedido em 16:9 para a tela em retrato cortar
o mínimo possível da imagem.

A view da câmera é um módulo nativo local (`modules/mirror-view`), então não
existe no Expo Go. Use o APK de uma release ou um development build.

| Comando             | O que faz                                          |
| ------------------- | -------------------------------------------------- |
| `npm run lint`      | ESLint                                             |
| `npm run typecheck` | TypeScript                                         |
| `npm run format`    | Prettier                                           |
| `npm run release`   | Script de versionamento (`-- --dry-run` p/ ensaio) |

### Arquitetura

- `App.tsx` — a raiz; esconde as barras do sistema e mostra o espelho.
- `src/Mirror.tsx` — pede a permissão da câmera e mostra o espelho.
- `modules/mirror-view/` — módulo nativo local (Kotlin): `MirrorView` liga a
  câmera frontal pelo CameraX e `PinchZoom` escala e desloca o preview.
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
