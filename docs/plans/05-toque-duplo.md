# 05 · Toque duplo vai e volta

**Status:** Aprovado · **Esforço:** baixo

## Objetivo

Sair do zoom e voltar para ele sem refazer a pinça.

## Como

- Com zoom (acima de 1×): o toque duplo guarda zoom e posição e anima para 1×.
- Em 1×: volta para o zoom e a posição guardados. Se não houver nada guardado,
  aproxima para 2,5× no ponto tocado.
- Animação de ~250 ms. O círculo do zoom aparece durante a animação, como na
  pinça, e some no fim.
- Detecção pelo `GestureDetector` do Android, junto da pinça e do arraste.

## Pronto quando

- Toque duplo com zoom vai para 1×; outro toque duplo volta exatamente para o
  zoom e a posição anteriores.
