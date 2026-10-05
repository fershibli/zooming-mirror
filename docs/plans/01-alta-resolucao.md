# 01 · Alta resolução na câmera

**Status:** Aprovado · **Esforço:** baixo

## Objetivo

Mais detalhe real quando o zoom aproxima. Hoje a câmera entrega cerca de 1080p
e o zoom amplia essa imagem: em 10× sobram uns 108×192 pixels esticados na tela
inteira. Com um stream de 4K, o mesmo 10× tem o dobro de pixels de verdade em
cada direção.

## Como

- Por padrão o CameraX limita o preview ao tamanho "PREVIEW" (a tela ou 1080p,
  o que for menor). Esse limite cai quando o app passa a própria
  `ResolutionStrategy`: o `Preview` pede até 3840×2160 em 16:9, com fallback
  para a resolução menor mais próxima.
- Fica a preferência padrão por taxa de quadros: só entram tamanhos que a
  câmera entrega a 30 fps, sem as resoluções "lentas" de foto.
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
