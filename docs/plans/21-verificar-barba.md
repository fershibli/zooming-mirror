# 21 · Verificar a barba com IA no aparelho

**Status:** Backlog · **Esforço:** alto

## Objetivo

Depois de fazer a barba, apontar onde ainda ficaram fios: no pescoço, embaixo
do queixo, no canto da boca. Tudo no celular, sem enviar a imagem para lugar
nenhum.

## Como

### Onde fica o botão

Um botão **Verificar a barba** no círculo de atalhos (plano 20). Ele fica no
círculo, e não só na imagem congelada, porque a análise fica bem melhor com
vários quadros (abaixo). O resultado, porém, aparece **congelado**, e dá para
analisar com calma, com zoom e arraste, como se tivesse pausado. Se a imagem já
estiver congelada, o botão analisa os quadros guardados no momento em que ela
congelou.

### Por que vários quadros, e não só a imagem parada

- Em zoom alto, o ruído do sensor parece pontinhos escuros, como barba por
  fazer. A média de 6 a 8 quadros seguidos (≈ 0,3 s) corta esse ruído, e os
  fios reais, que estão em todos os quadros, continuam lá.
- Dá para descartar quadros tremidos (pouca nitidez) em vez de analisar
  justamente o pior.
- Na prática: ao tocar no botão, aparece "Fique parado" por meio segundo, o
  app junta os quadros, congela e mostra o resultado.
- Para caber na memória, cada quadro guarda só o recorte do rosto, em
  resolução cheia, e não o quadro inteiro de 4K.

### Como encontra os fios

1. **Onde é a barba:** detecção de rosto com contornos no aparelho (ML Kit Face
   Detection, modelo embutido). O contorno do rosto, os lábios e as bochechas
   formam a região da barba: metade de baixo do rosto, menos os lábios, mais
   uma faixa abaixo da linha do queixo para o pescoço. Os quadros são alinhados
   por esses pontos antes da média.
2. **O que é fio e o que é pele:** em cada quadro médio, um filtro morfológico
   de "chapéu preto" na luminância, na escala da espessura de um fio, realça
   pontos e traços curtos mais escuros que a pele ao redor. A referência de
   pele vem da própria pessoa, de uma área sem barba (maçã do rosto), o que
   adapta a análise ao tom de pele e à luz do momento.
3. **Resultado:** a região da barba é dividida em quadradinhos (≈ 3 mm). Os que
   têm densidade de fios acima do limite ganham um contorno translúcido sobre a
   imagem congelada, e um número no círculo do zoom diz quantas áreas ficaram.
   Zoom e arraste continuam funcionando sobre o resultado.
4. **Segunda fase, se a heurística não bastar:** um modelo pequeno de
   segmentação (LiteRT) treinado para barba por fazer. Isso exige montar um
   conjunto de fotos anotadas, e por isso fica para depois de medir a primeira
   versão.

### A luz conta

A análise depende de luz estável e frontal. Ela usa a luz segurada (sem a
exposição mudando no meio dos quadros) e combina com a luz de preenchimento
(plano 12) e com o tipo de nitidez **Detalhe** (plano 19).

## Riscos

- **Falsos positivos:** pintas, poros abertos, sardas e sombras de pele
  parecem pontos escuros. A referência de pele da própria pessoa ajuda, mas não
  resolve tudo; o resultado é uma dica, não uma certeza.
- **Pele escura e barba clara** reduzem o contraste entre fio e pele. A
  heurística pode falhar justamente aí, e é o que mais justifica um modelo
  treinado.
- **Tamanho do APK:** o modelo de rosto do ML Kit embutido adiciona alguns MB.
  A versão que vem do Google Play Services não pesa no APK, mas exige o Play
  Services.
- **Privacidade:** a imagem nunca sai do aparelho e nada é gravado em disco.
  Isso deve aparecer escrito nos ajustes.

## Pronto quando

- Com barba de um dia de um lado do rosto e o outro lado feito, o resultado
  marca o lado não feito e deixa o lado feito limpo, em pelo menos 4 de 5
  tentativas, com luz de banheiro comum.
- Pintas e sardas conhecidas não são marcadas na maioria das vezes.
- A análise leva menos de 2 s do toque até o resultado num celular médio.
