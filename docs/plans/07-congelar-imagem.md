# 07 · Congelar a imagem

**Status:** Aprovado · **Esforço:** médio

## Objetivo

Parar a imagem para olhar com calma (o perfil, um detalhe da pele), com zoom e
arraste, sem precisar ficar parado na frente do celular.

## Como

- **Toque longo** congela; outro toque longo descongela. Uma vibração curta
  confirma, e o círculo mostra ❚❚ ou ▶ por um instante.
- Caminho padrão: copia o quadro da `TextureView` interna da `PreviewView` na
  resolução do stream (não da tela), e mostra a cópia exatamente por cima, com a
  mesma transformação. Zoom e arraste continuam funcionando sobre ela.
- Caminho da GPU: copia o quadro para uma textura própria e passa a desenhar a
  partir dela. A câmera continua consumindo quadros por baixo (parar de
  consumir pode travar a câmera em alguns aparelhos).
- A câmera segue ligada durante o congelamento, então descongelar é imediato.

## Riscos

- Em zoom alto o congelado fica tão nítido quanto o vivo só se a cópia for na
  resolução do stream — por isso a cópia não usa `PreviewView.getBitmap()`, que
  vem no tamanho da tela.

## Pronto quando

- Toque longo para a imagem; dá para dar zoom e arrastar a imagem parada; outro
  toque longo volta ao vivo.
