# 20 · Círculo de atalhos

**Status:** Backlog · **Esforço:** médio

## Objetivo

Hoje o arraste da borda direita para dentro mostra só a engrenagem. Com as
próximas funções (verificar a barba, tipo de nitidez), faltam botões. A ideia é
um círculo de atalhos, como uma roda de habilidades de jogo: o mesmo gesto abre
vários botões de uma vez, e a tela continua sem nada no resto do tempo.

## Como

- O mesmo gesto de hoje: arrastar da borda direita para dentro, à altura do
  círculo do zoom. Em vez de um botão, aparece um arco de botões em pixel art,
  centrado no ponto onde o dedo entrou, abrindo para dentro da tela (um
  semicírculo de raio ≈ 96dp).
- Botões, de cima para baixo:
  1. **Verificar a barba** (plano 21);
  2. **Tipo de nitidez** (plano 19), que passa para o próximo tipo a cada toque;
  3. **Congelar** (o mesmo do toque longo, para quem não conhece o gesto);
  4. **Ajustes** (a engrenagem de hoje).
- Cada botão é um círculo de 56dp com ícone de 16×16 em pixel art, na paleta
  do app, igual à engrenagem. Um rótulo curto aparece enquanto o dedo está
  sobre o botão.
- Dá para usar de dois jeitos:
  - **Tocar:** solta o arraste, o arco fica na tela e um toque escolhe o botão.
  - **Arrastar e soltar:** sem tirar o dedo, ir até o botão e soltar em cima
    dele, que é o jeito rápido de uma roda de habilidades.
- O arco some sozinho depois de 4 s sem uso, ou com um toque fora dele.
- A área de exclusão do gesto de voltar do Android (200dp na borda direita)
  continua a mesma de hoje.
- Botões que não valem no momento aparecem apagados. Por exemplo, o tipo de
  nitidez sem a GPU ligada.
- Para leitores de tela, os botões têm nome e também ficam na tela de ajustes.

## Riscos

- Mais de 4 ou 5 botões num semicírculo pequeno ficam difíceis de acertar com o
  polegar. Se crescer, vira um segundo anel ou uma página dentro do círculo.
- O "arrastar e soltar" não pode disparar sem querer. Soltar a menos de 24dp do
  ponto de entrada só deixa o arco aberto.

## Pronto quando

- O arraste da borda mostra o arco com os botões, e tanto tocar quanto
  arrastar e soltar acionam o botão certo.
- O arco some sozinho e não aparece em nenhum outro gesto.
- A engrenagem continua levando aos ajustes como hoje.
