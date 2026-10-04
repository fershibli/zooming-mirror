# 13 · Inércia no arraste

**Status:** Backlog · **Esforço:** baixo

## Objetivo

A imagem continua deslizando um pouco ao soltar o dedo depois de um arraste
rápido, como no Google Fotos.

## Como

- `VelocityTracker` no arraste de um dedo e `OverScroller.fling` ao soltar,
  com a mesma trava que mantém a imagem cobrindo a tela.
- Qualquer toque novo para o deslizamento.

## Pronto quando

- Um arraste rápido continua o movimento e desacelera até parar na borda da
  imagem.
