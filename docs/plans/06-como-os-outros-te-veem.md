# 06 · Como os outros te veem

**Status:** Aprovado · **Esforço:** baixo

## Objetivo

Um espelho mostra você invertido. Quem te olha te vê sem inverter, e muita gente
estranha o próprio rosto "do jeito certo". O ajuste mostra a imagem como os
outros a veem.

## Como

- Ajuste **Como os outros te veem** na tela de ajustes (desligado por padrão).
- Caminho padrão: a escala horizontal da view fica negativa. A matemática do
  zoom e do arraste não muda, porque a translação é aplicada depois da
  inversão.
- Caminho da GPU: a coordenada horizontal da tela é invertida antes do resto do
  mapeamento no shader.
- A medição de exposição (plano 03) usa a coordenada já corrigida, então mede o
  lugar certo nos dois modos.

## Pronto quando

- Com o ajuste ligado, levantar a mão direita mostra a mão do lado direito da
  tela; zoom, arraste e toque duplo continuam iguais.
