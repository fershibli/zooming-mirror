# 17 · APK em todo PR

**Status:** Backlog · **Esforço:** baixo

## Objetivo

Testar cada PR no celular antes do merge, sem depender de compilar fora do
GitHub.

## Como

- Um job no workflow de PR que compila o APK de release (assinado com a chave
  temporária) e publica como artefato do workflow, com o número do PR no nome.
- Junto com o cache do Gradle (plano 09), para não pesar demais em cada push.

## Riscos

- Cada push em PR passa a levar alguns minutos a mais de CI.

## Pronto quando

- Todo PR tem um APK para baixar na aba de artefatos da execução do CI.
