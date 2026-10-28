# Orquestração SDD

Este repositório usa Spec-Driven Development (SDD). O agente principal coordena os subagentes `spec_architect`, `software_engineer`, `code_reviewer`, `mr_writer` e `commit_agent` definidos em `.codex/config.toml`.

## Estrutura

Cada funcionalidade tem sua própria pasta `specs/<nome-kebab-case>/` com `spec.md` e `tasks.md`. Nunca criar uma especificação ou tasks globais para múltiplas funcionalidades.

Documentos de Merge Request ficam em `docs/merge_requests/<nome-kebab-case>.md`.

## Fluxos

### Nova funcionalidade ou mudança de comportamento

1. Delegar análise e elaboração da especificação ao `spec_architect`.
2. Exigir `spec.md` com objetivo, contexto, regras de negócio, requisitos funcionais e não funcionais, critérios de aceitação, casos de erro e fora de escopo.
3. Exigir `tasks.md` com tarefas técnicas relacionadas aos critérios de aceitação, implementação, testes, validação e checklist.
4. Apresentar resumo e dúvidas ao usuário; aguardar aprovação explícita antes de implementar.
5. Após aprovação, delegar implementação ao `software_engineer`.
6. Relatar arquivos alterados, testes efetivamente executados, cobertura verificada, riscos e pendências.

### Correção de bug

Identificar primeiro se o bug exige mudança do comportamento especificado. Se exigir, passar pelo arquiteto e pela aprovação da especificação.

Caso contrário, delegar correção ao engenheiro e revisão ao revisor, utilizando a especificação existente quando houver.

Criar teste de regressão.

### Refatoração

Delegar ao engenheiro, preservando comportamento e contratos existentes.

Não alterar requisitos implicitamente.

### Revisao
Invocar `code_reviewer` apenas mediante solicitação do usuário.

### Documentação de Merge Request

Invocar `mr_writer` apenas mediante solicitação do usuário.

Antes de criar o documento, solicitar o contexto geral e a motivação da MR, mesmo que o repositório forneça detalhes técnicos.

### Criação de Commits

Invocar `commit_agent` apenas mediante solicitação explícita do usuário.

O agente deve analisar as alterações existentes no repositório e criar commits seguindo o padrão **Conventional Commits**, com mensagens obrigatoriamente em inglês.

#### Fluxo obrigatório

1. Identificar a branch atual utilizando `git branch --show-current`.
2. Verificar o estado do repositório utilizando `git status`.
3. Analisar as alterações staged, unstaged e os arquivos não rastreados.
4. Identificar alterações relacionadas e determinar como organizá-las em commits.
5. Utilizar Conventional Commits para definir as mensagens.
6. Separar alterações independentes em commits distintos quando apropriado.
7. Adicionar ao staging apenas os arquivos ou trechos relacionados a cada commit.
8. Executar os commits exclusivamente na branch atual.
9. Verificar os commits criados e o estado final do repositório.
10. Informar os hashes, as mensagens e os arquivos incluídos em cada commit.

#### Padrão das mensagens

Utilizar o formato:

`<type>(<scope>): <description>`

O escopo é opcional quando não houver um componente claramente identificável.

Tipos permitidos:

- `feat`: nova funcionalidade.
- `fix`: correção de bug.
- `refactor`: refatoração sem mudança de comportamento.
- `docs`: alterações de documentação.
- `test`: adição ou alteração de testes.
- `chore`: manutenção geral.
- `build`: alterações no sistema de build ou dependências.
- `ci`: alterações em pipelines de CI/CD.
- `perf`: melhorias de desempenho.
- `style`: alterações de formatação sem impacto funcional.
- `revert`: reversão de commits.

Exemplos:

- `feat(appointments): add appointment cancellation`
- `fix(auth): handle expired access tokens`
- `refactor(donors): simplify validation logic`
- `test(appointments): add cancellation unit tests`
- `docs(context): document notification module`
- `ci(pipeline): improve Maven build caching`

#### Regras obrigatórias

- Todas as mensagens de commit devem ser escritas em inglês.
- Utilizar verbos no imperativo nas descrições.
- Manter mensagens objetivas e descritivas.
- Utilizar letras minúsculas no tipo, escopo e descrição, salvo nomes próprios ou identificadores que exijam outra capitalização.
- Não utilizar mensagens genéricas como `update files`, `fix stuff` ou `changes`.
- Não criar ou trocar de branch.
- Não executar `git push`.
- Não executar merges ou rebases.
- Não modificar código apenas para criar commits.
- Não incluir arquivos que não estejam relacionados às alterações solicitadas.
- Não sobrescrever commits existentes sem autorização explícita.
- Não utilizar `git commit --amend` sem autorização.
- Não utilizar `git reset --hard` ou outros comandos destrutivos.
- Não utilizar `git add .` indiscriminadamente.
- Respeitar alterações preexistentes e arquivos pertencentes a outros trabalhos.
- Não incluir credenciais, tokens, segredos ou arquivos sensíveis.
- Não realizar commits automaticamente ao concluir funcionalidades, correções ou refatorações.
- Se houver alterações ambíguas, solicitar esclarecimento antes de incluí-las.
- Se não houver alterações elegíveis, informar ao usuário sem criar commits vazios.

## Regras gerais

- Especificações são fonte da verdade para comportamentos esperados.
- Não inventar requisitos, resultados de testes, métricas ou justificativas.
- Não fazer commits, pushes nem abrir Merge Requests automaticamente.
- Commits somente podem ser executados mediante solicitação explícita do usuário e delegação ao `commit_agent`.
- Não marcar tarefas como concluídas sem evidência.
- Não permitir edições simultâneas dos mesmos arquivos por agentes diferentes.
- Se a delegação a subagentes não estiver disponível, informar e seguir as mesmas etapas sem fingir que houve delegação.
- Para tarefas dependentes, aguardar a conclusão da etapa anterior.
- Não executar operações destrutivas no Git sem autorização explícita.
- Preservar o estado e as alterações existentes no repositório que não façam parte da tarefa solicitada.