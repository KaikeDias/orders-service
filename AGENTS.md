# Orquestração SDD

Este repositório usa Spec-Driven Development (SDD). O agente principal coordena os subagentes `spec_architect`, `software_engineer`, `code_reviewer` e `mr_writer` definidos em `.codex/config.toml`.

## Estrutura

Cada funcionalidade tem sua própria pasta `specs/<nome-kebab-case>/` com `spec.md` e `tasks.md`. Nunca criar uma especificação ou tasks globais para múltiplas funcionalidades. Documentos de Merge Request ficam em `docs/merge_requests/<nome-kebab-case>.md`.

## Fluxos

### Nova funcionalidade ou mudança de comportamento
1. Delegar análise e elaboração da especificação ao `spec_architect`.
2. Exigir `spec.md` com objetivo, contexto, regras de negócio, requisitos funcionais e não funcionais, critérios de aceitação, casos de erro e fora de escopo.
3. Exigir `tasks.md` com tarefas técnicas relacionadas aos critérios de aceitação, implementação, testes, validação e checklist.
4. Apresentar resumo e dúvidas ao usuário; aguardar aprovação explícita antes de implementar.
5. Após aprovação, delegar implementação ao `software_engineer`.
6. Delegar revisão ao `code_reviewer`. Se houver problemas, retornar ao engenheiro e repetir revisão.
7. Relatar arquivos alterados, testes efetivamente executados, cobertura verificada, riscos e pendências.

### Correção de bug
Identificar primeiro se o bug exige mudança do comportamento especificado. Se exigir, passar pelo arquiteto e pela aprovação da especificação. Caso contrário, delegar correção ao engenheiro e revisão ao revisor, utilizando a especificação existente quando houver. Criar teste de regressão.

### Refatoração
Delegar ao engenheiro e depois ao revisor, preservando comportamento e contratos existentes. Não alterar requisitos implicitamente.

### Documentação de Merge Request
Invocar `mr_writer` apenas mediante solicitação do usuário. Antes de criar o documento, solicitar o contexto geral e a motivação da MR, mesmo que o repositório forneça detalhes técnicos.

## Regras gerais
- Especificações são fonte da verdade para comportamentos esperados.
- Não inventar requisitos, resultados de testes, métricas ou justificativas.
- Não fazer commits, pushes nem abrir Merge Requests automaticamente.
- Não marcar tarefas como concluídas sem evidência.
- Não permitir edições simultâneas dos mesmos arquivos por agentes diferentes.
- Se a delegação a subagentes não estiver disponível, informar e seguir as mesmas etapas sem fingir que houve delegação.
- Para tarefas dependentes, aguardar a conclusão da etapa anterior.
