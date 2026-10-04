# 01 · Alta resolução na câmera

**Status:** Aprovado · **Esforço:** baixo

## Objetivo

Mais detalhe real quando o zoom aproxima. Hoje a câmera entrega cerca de 1080p
e o zoom amplia essa imagem: em 10× sobram uns 108×192 pixels esticados na tela
inteira. Com um stream de 4K, o mesmo 10× tem o dobro de pixels de verdade em
cada direção.

## Como

- No `Preview` do CameraX, um `ResolutionSelector` que pede até 3840×2160 em
  16:9 (`ResolutionStrategy` com fallback para a resolução menor mais próxima)
  e `PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE`.
- A `TextureView` amostra a textura da câmera direto na composição, com a
  transformação final aplicada, então a ampliação aproveita a resolução extra
  sem mexer no resto do caminho.
- Um ajuste **Alta resolução** (ligado por padrão) volta para o comportamento
  de hoje, caso o aparelho perca fluidez.
- A resolução que a câmera realmente entregou aparece na tela de ajustes, para
  conferir no aparelho.

## Riscos

- Cada aparelho decide o que entrega: alguns não passam de 1080p no preview.
- Mais banda de memória e bateria; em aparelhos fracos pode cair o fps.

## Pronto quando

- Com o ajuste ligado, a tela de ajustes mostra uma resolução acima de 1080p
  num aparelho que suporte, e o zoom alto fica visivelmente mais nítido.
- Com o ajuste desligado, o app se comporta como na v1.1.0.
