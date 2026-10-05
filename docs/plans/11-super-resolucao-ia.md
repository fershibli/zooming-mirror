# 11 · Super-resolução com IA

**Status:** Backlog · **Esforço:** alto

## Objetivo

Reconstruir detalhe na imagem ampliada com um modelo de super-resolução.

## Como

- Modelo leve (família ESRGAN/SRCNN) em LiteRT com delegate de GPU/NPU,
  aplicado só à área visível — em 4× ela tem 1/16 do quadro.
- Mais realista como modo "congelar e ampliar" (plano 07) do que ao vivo.

## Riscos

- Ao vivo: atraso, aquecimento e bateria, num app que fica aberto como espelho.
- O modelo **inventa** detalhe plausível. Num espelho usado para olhar a
  própria pele, isso pode enganar (uma pinta, uma ruga).
- APK bem maior por causa do modelo.

## Pronto quando

- A decidir. Só vale depois dos planos 01 e 02 e com teste em aparelho.
