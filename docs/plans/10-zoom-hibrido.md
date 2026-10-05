# 10 · Zoom híbrido

**Status:** Backlog · **Esforço:** alto

## Objetivo

Detalhe real no zoom até o limite do sensor. Muitas câmeras frontais têm zoom
digital no próprio processador de imagem (ISP), que recorta o sensor em
resolução cheia antes de reduzir para o tamanho do stream. Um sensor de 12 MP
(~4000 px de largura) aguenta uns 3× praticamente sem perda.

## Como

- Usar o zoom da câmera (`setZoomRatio`) até o máximo dela e só depois ampliar
  a superfície, como hoje. O zoom total continua sendo câmera × superfície.
- O zoom da câmera é sempre centralizado: o arraste precisa de recorte fora do
  centro (`SCALER_CROP_REGION` via Camera2 interop, onde o aparelho aceitar) ou
  de uma combinação recorte + deslocamento da superfície.
- A câmera aplica o zoom alguns quadros depois do pedido: a superfície precisa
  compensar nesse meio-tempo para a troca não "pular".

## Riscos

- Comportamento muito dependente do aparelho; precisa de teste em vários.
- Combinação com o plano 01: com stream em 4K o ganho deste plano diminui.

## Pronto quando

- Até o zoom máximo da câmera, a imagem ampliada tem detalhe visivelmente maior
  que a ampliação da superfície, sem saltos ao passar desse limite.
