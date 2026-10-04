# 18 · Testes da matemática do zoom

**Status:** Backlog · **Esforço:** baixo

## Objetivo

Garantir, a cada mudança, que o ponto entre os dedos fica parado, que a imagem
sempre cobre a tela e que os limites de 1× e 10× valem.

## Como

- Separar o cálculo do `PinchZoom` (zoom, translação e trava) da `View`, para
  testar em JVM pura com JUnit, sem emulador.
- Rodar os testes no CI de PR (`./gradlew :mirror-view:testReleaseUnitTest`
  depois do prebuild).

## Pronto quando

- O CI de PR roda os testes e falha se a matemática do zoom quebrar.
