# 08 · APK menor

**Status:** Aprovado · **Esforço:** baixo

## Objetivo

O APK da v1.1.0 tem 71 MB porque traz as bibliotecas nativas de quatro
arquiteturas, duas delas (x86 e x86_64) só usadas por emuladores.

## Como

- O workflow do APK passa a compilar só `arm64-v8a` e `armeabi-v7a`
  (`-PreactNativeArchitectures=arm64-v8a,armeabi-v7a`). Celulares Android reais
  usam uma dessas duas.
- Só arm64 deu 28 MB no build local; com as duas o esperado é algo entre 40 e
  45 MB.

## Fica para depois

- R8 / encolher recursos (`enableMinifyInReleaseBuilds`,
  `enableShrinkResourcesInReleaseBuilds`) reduziria mais, mas pode quebrar
  módulos que usam reflexão. Só vale com teste no aparelho.
- Publicar dois APKs (um por arquitetura) deixaria cada um perto de 28 MB, ao
  custo de escolher o arquivo certo na hora de baixar.

## Pronto quando

- O APK da próxima release sai sem x86/x86_64 e menor que 50 MB.
