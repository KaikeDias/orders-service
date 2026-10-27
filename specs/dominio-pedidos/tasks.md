# Tarefas — Domínio de pedidos

Estado: **implementação, validação e revisão concluídas**. Evidências registradas ao final deste documento.

## Aprovação e dependência

- [x] T01 — Apresentar ao usuário a [spec](spec.md), suas decisões de moeda/escala, produtos repetidos, invariantes e transições; registrar aprovação explícita. Evidência: o usuário respondeu “aprovo” à apresentação da spec. Referência: CA08.
- [x] T02 — Comprovar conclusão do item 01 segundo seus critérios e [tarefas](../base-persistencia-integracao/tasks.md). Evidência: build com três testes aprovados, migration verificada em container e inicialização no PostgreSQL local; detalhes em [persistence.md](../../docs/persistence.md). Referência: CA08; RNF05.

## Implementação após T01 e T02

- [x] T03 — Implementar pedido, item e validação dos dados, moeda, duplicatas e preservação do snapshot, conforme decisões aprovadas. Referências: CA01–CA03; RF01, RF04.
- [x] T04 — Implementar subtotal e total com decimal exato e política de escala aprovada, sem conversão ou arredondamento implícitos. Referência: CA04; RF02.
- [x] T05 — Implementar estado inicial e as duas operações de transição com rejeição das combinações inválidas sem mutação. Referências: CA05–CA06; RF03, RF04.

## Testes e validação

- [x] T06 — Testes unitários de construção, isolamento, snapshots, campos inválidos, moedas e duplicatas. Referências: CA01–CA03, CA07.
- [x] T07 — Testes de exemplos monetários, zero, escalas equivalentes, excesso de precisão e cálculo exato. Referências: CA04, CA07.
- [x] T08 — Testes das transições permitidas e inválidas, verificando ausência de mutação. Referências: CA05–CA07.
- [x] T09 — Testes unitários e build executados; resultados e cobertura abaixo. Referência: CA07.

## Revisão e encerramento

- [x] T10 — Revisão independente realizada; lacuna em testes de moeda vazia corrigida e reavaliada sem novos achados. Referências: CA01–CA07.
- [x] T11 — Arquivos, testes, cobertura e limites registrados abaixo e no estado do projeto. Referências: CA01–CA08.

## Checklist de evidências

- [x] Spec aprovada e dúvidas de negócio resolvidas.
- [x] Item 01 concluído com evidências; dependências da stack disponíveis.
- [x] Testes de invariantes, snapshot, cálculo e transições executados com resultado registrado.
- [x] Build e cobertura registrados, com limites explícitos.
- [x] Revisão independente concluída e critérios de aceitação conferidos.

## Evidências de execução

- Implementação: `Order`, `OrderItem` e `OrderStatus` em `src/main/java/com/dias/orders_service/domain/`; testes em `src/test/java/com/dias/orders_service/domain/OrderTest.java`.
- `mvnw.cmd -Dtest=OrderTest test`: oito testes aprovados. `mvnw.cmd clean verify`: 11 testes aprovados (oito de domínio e três da base), sem falhas, erros ou skips.
- JaCoCo: `Order` 167/167 instruções, 45/45 linhas e 20/20 branches; `OrderItem` 97/97 instruções, 21/21 linhas e 16/16 branches; `OrderStatus` 21/21 instruções e 4/4 linhas, sem branches. Essa medição não representa validação de integrações futuras ou cobertura global integral.
- Relatórios: `target/site/jacoco/index.html` e `target/site/jacoco/jacoco.xml`. A revisão independente confirmou a correção dos casos de moeda em branco.
