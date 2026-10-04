# 03 · Exposição que acompanha o zoom

**Status:** Aprovado · **Esforço:** médio

## Objetivo

Hoje a câmera mede a luz do quadro inteiro. Com zoom num rosto e uma janela
clara atrás, o rosto fica escuro. A exposição deve seguir o que está na tela, e
dá para ajustar à mão quando o automático não acerta.

## Como

### Automático: medir só a área visível (ajuste "Luz automática")

- Ao fim de cada pinça, arraste ou toque duplo, o app pede à câmera para medir
  exposição e balanço de branco só na região visível (`FocusMeteringAction` com
  `FLAG_AE | FLAG_AWB`, sem cancelamento automático). O tamanho da região é
  proporcional ao zoom (em 4×, um quarto do quadro).
- De volta a 1×, a medição volta ao quadro inteiro.
- No caminho padrão o ponto vem da `meteringPointFactory` da `PreviewView`; no
  da GPU, de uma `DisplayOrientedMeteringPointFactory` com as coordenadas que o
  shader já calcula.

### Plano B, quando a câmera não aceita região de medição

- Algumas câmeras frontais não aceitam. Nelas, o app lê o brilho médio da área
  visível numa cópia pequena da imagem (≈64×112) a cada 0,4 s e corrige pela
  compensação de exposição, com uma faixa morta para não ficar oscilando.

### Prioridade de rosto (ajuste "Prioridade de rosto")

- Liga o `CONTROL_SCENE_MODE_FACE_PRIORITY` do Camera2 quando a câmera tem: o
  próprio processador de imagem prioriza rostos na exposição. Pode ser ligado e
  desligado sem reiniciar a câmera.

### Manual: deslizar na borda direita

- Deslizar na vertical começando na borda direita (faixa de 24dp) clareia
  (para cima) ou escurece (para baixo), pela compensação de exposição da
  câmera. Meia altura da tela percorre a faixa inteira que a câmera aceita.
- Enquanto desliza, o mesmo círculo do zoom mostra o valor (`+0,7 EV`); um
  toque leve marca a passagem pelo zero.
- O valor manual soma com o ajuste automático do plano B e vale até fechar o
  app.

## Riscos

- O que cada câmera frontal aceita varia (região de medição, prioridade de
  rosto, faixa de compensação); a tela de ajustes mostra o que o aparelho
  suporta e desativa o resto.
- No Android com navegação por gestos, a borda direita também é o gesto de
  voltar. Deslizar na _vertical_ não dispara o voltar; o gesto horizontal da
  engrenagem (plano 04) usa uma área de exclusão de gesto.

## Pronto quando

- Com zoom num rosto contra uma janela clara, o rosto fica bem exposto alguns
  instantes depois de soltar a pinça.
- Deslizar na borda direita clareia e escurece, com o valor no círculo.
- Desligar "Luz automática" volta à medição do quadro inteiro.
