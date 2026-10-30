# 19 · Tipo de nitidez

**Status:** Feito · **Esforço:** médio

## Objetivo

Escolher como a imagem ampliada fica, conforme o uso. Para fazer a barba, cada
fio deve se destacar. Para olhar a pele, às vezes é melhor uma imagem mais lisa.
Hoje o caminho da GPU (plano 02) aplica só um tipo de realce.

## Como

O ajuste **Nitidez (GPU)** deixa de ser só liga/desliga e ganha um tipo, que o
segundo passo do shader aplica:

| Tipo        | Para quê                   | Como                                                                                                                                                     |
| ----------- | -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Lisa**    | olhar a pele com calma     | suavização que preserva bordas (bilateral 5×5 só na luminância): some a textura fina e o ruído, e os contornos do rosto ficam                            |
| **Natural** | o dia a dia                | o realce adaptativo de hoje (CAS)                                                                                                                        |
| **Detalhe** | fazer a barba, sobrancelha | CAS mais forte mais um "clarity": unsharp mask de raio maior só na luminância, que aumenta o contraste local de fios curtos e escuros sem estourar a cor |

- Os três tipos usam o mesmo passo e mudam só os uniforms e um `#define`. Trocar
  de tipo não reinicia a câmera.
- O tipo fica nos ajustes, como três opções lado a lado. Também é um atalho
  do círculo de atalhos (plano 20): um toque passa para o próximo tipo, e o
  rótulo do botão mostra o tipo escolhido.
- Cada tipo é um programa de GPU próprio, compilado uma vez, então a troca não
  reinicia a câmera e vale também para a imagem congelada.
- O realce continua dependendo do zoom, como no plano 02. Em 1× quase não
  muda nada, e o efeito cresce conforme amplia.

## Riscos

- Depende do caminho da GPU, que ainda não foi testado num aparelho. Antes,
  vale confirmar no celular que **Nitidez (GPU)** liga e fica estável.
- **Detalhe** realça também o ruído, principalmente com pouca luz. Combina com a
  luz segurada (sem a exposição oscilando) e com a luz de preenchimento
  (plano 12).
- **Lisa** "embeleza": num espelho usado para examinar a pele (uma pinta, uma
  irritação), isso pode esconder o que se quer ver. Por isso o padrão continua
  **Natural**.
- O bilateral custa cerca de 25 leituras de textura por pixel. Na resolução da
  tela, isso cabe na GPU de um celular médio, mas precisa ser medido.

## Pronto quando

- Os três tipos são escolhidos nos ajustes e trocam na hora, sem piscar.
- Em 4×, **Detalhe** mostra fios de barba de um dia que **Natural** mal mostra,
  e **Lisa** deixa a pele visivelmente mais uniforme sem borrar olhos e boca.
- Sem a GPU, a escolha fica apagada, como os outros ajustes indisponíveis.
