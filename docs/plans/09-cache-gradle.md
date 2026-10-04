# 09 · Cache do Gradle no build do APK

**Status:** Aprovado · **Esforço:** baixo

## Objetivo

O build do APK baixa e recompila tudo do zero a cada release (~8 min).

## Como

- `gradle/actions/setup-gradle` no workflow do APK, antes da compilação: guarda
  as dependências baixadas, o wrapper e o cache de build do Gradle entre
  execuções.
- O projeto Android é gerado na hora (`expo prebuild --clean`), então o cache
  que mais rende é o de dependências; o de saída de tarefas ajuda menos.

## Pronto quando

- Do segundo build em diante, o passo de compilação fica mais rápido que o
  primeiro e o log mostra o cache restaurado.
