# 02 · Nitidez por GPU

**Status:** Aprovado (experimental, desligado por padrão) · **Esforço:** alto

## Objetivo

Que a imagem ampliada _pareça_ mais nítida. Hoje quem amplia é a composição do
Android, com filtro bilinear, que é o que deixa tudo borrado em zoom alto.

Um filtro não cria detalhe que a câmera não captou: ele melhora a percepção
(bordas mais definidas). O ganho de detalhe real vem do plano 01.

## Como

Um caminho de renderização próprio em OpenGL ES, ligado pelo ajuste
**Nitidez (GPU)**:

1. A câmera escreve numa `SurfaceTexture` (textura OES). A matriz da
   `SurfaceTexture` já traz a rotação e o espelhamento da câmera frontal.
2. **Passo 1 — ampliação:** para cada pixel da tela, o shader calcula de onde
   ele vem na imagem da câmera (zoom, arraste, corte para preencher a tela,
   espelhamento) e amostra com Catmull-Rom (bicúbico) em vez de bilinear.
   Quando não há ampliação (zoom baixo com stream grande), usa bilinear.
3. **Passo 2 — nitidez:** _contrast-adaptive sharpening_ (a ideia do CAS da
   AMD) na resolução da tela, mais forte quanto maior o zoom. Ele realça menos
   onde já há muito contraste, o que evita halos.
4. O resultado vai para uma `TextureView` do tamanho da tela; o zoom deixa de
   ser uma transformação da view e passa a ser uniforme do shader.

O estado do zoom (`PinchZoom`) continua o mesmo nos dois caminhos: no caminho
padrão ele mexe na view; no da GPU ele vira uniforme.

## Riscos

- Código de GPU sem teste em aparelho real até o primeiro APK: por isso fica
  desligado por padrão e o caminho de hoje continua intacto.
- Realce de bordas também realça ruído, principalmente com pouca luz.
- Um pouco mais de bateria (dois passos por quadro, em torno de 14 leituras de
  textura por pixel).

## Pronto quando

- Com o ajuste ligado, a imagem aparece em pé, espelhada e preenchendo a tela,
  com zoom, arraste, toque duplo e congelar funcionando igual ao caminho padrão.
- Em 4× ou mais, as bordas ficam visivelmente mais definidas que no caminho
  padrão, sem halos fortes.
- Desligar o ajuste volta ao caminho padrão na hora.
