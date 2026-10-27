# Domínio de pedidos

Estado: **aprovada, implementada e validada**. Item 02 do [roadmap](../../docs/implementation-roadmap.md). A dependência 01 foi concluída; evidências em [tasks.md](tasks.md).

## Objetivo

Definir o modelo de pedido e seus itens, a cópia histórica dos dados de produto, os cálculos monetários e as transições do primeiro fluxo de reserva, com critérios verificáveis por testes unitários.

## Contexto e dependência

- A arquitetura propõe um pedido com um ou mais itens, identificadores UUID, moeda única, nome e preço históricos do produto, quantidade positiva, preço não negativo e cálculo de subtotais e total. O fluxo inicial parte de `AWAITING_STOCK` e termina em `AWAITING_PAYMENT` ou `REJECTED`.
- Não há classes de domínio de pedidos em `src/main/java` no momento da análise. Produtos, Estoque e Kafka ainda não estão disponíveis; não há contratos externos aprovados nesta etapa.
- O item 02 depende da conclusão do item 01. A [base de persistência e integração](../base-persistencia-integracao/tasks.md) foi validada com build, testes em PostgreSQL descartável e inicialização no banco local. A presente especificação foi aprovada explicitamente pelo usuário.

## Regras de negócio propostas

### Pedido e itens

1. Um pedido tem um identificador UUID, uma moeda e pelo menos um item. A coleção e seus itens não podem ser nulos; a lista de itens exposta não pode permitir alteração externa do pedido.
2. Cada item contém `productId` UUID, nome histórico não nulo e não composto apenas por espaços, preço unitário histórico e quantidade inteira maior que zero. O preço é não nulo e maior ou igual a zero. O identificador de produto não pode ser nulo.
3. Cada pedido utiliza uma única moeda. Proposta: aceitar código ISO 4217 reconhecido, sem conversão, e rejeitar itens cuja moeda difira da moeda do pedido. A moeda do item é validada na formação do pedido; a representação interna pode evitá-la em duplicidade depois da validação.
4. Proposta: rejeitar um pedido com o mesmo `productId` em mais de um item. Não somar quantidades nem escolher entre snapshots conflitantes implicitamente.
5. O domínio recebe nome, preço e moeda como snapshot já obtido pelo chamador. Após criação, alterações em objetos ou coleções fornecidos ao domínio não podem mudar os dados históricos do pedido. A obtenção do snapshot no catálogo e a proibição de receber preço do cliente na API serão tratadas no item 03.

### Valores monetários

6. Proposta: representar preço, subtotal e total em decimal exato, sem `float` ou `double`; padronizar escala de **duas casas decimais**. Aceitar valores representáveis exatamente nessa escala, incluindo `1`, `1.0` e `1.000`; rejeitar `1.001`, sem arredondamento silencioso. Preço zero é válido.
7. O subtotal é preço unitário × quantidade; o total é a soma exata dos subtotais. Ambos têm duas casas decimais. Não há impostos, frete, descontos, conversão de moeda ou arredondamento intermediário nesta etapa.
8. A escala uniforme de duas casas é uma restrição aprovada para esta versão, inclusive para moedas cuja unidade fracionária convencional seja diferente.

### Estados e transições

9. Um pedido válido é criado em `AWAITING_STOCK`.
10. Proposta para as únicas transições permitidas neste recorte:

| Estado atual | Operação de domínio | Estado resultante |
| --- | --- | --- |
| `AWAITING_STOCK` | registrar reserva de estoque aceita | `AWAITING_PAYMENT` |
| `AWAITING_STOCK` | registrar reserva de estoque recusada | `REJECTED` |

11. Qualquer outra aplicação dessas operações, incluindo repetição, falha como operação inválida e mantém o estado, itens e valores inalterados. `AWAITING_PAYMENT` e `REJECTED` são terminais **apenas para as duas operações de reserva deste recorte**; não se define aqui o ciclo de vida posterior.
12. A validação de estado pertence ao domínio. A deduplicação de mensagens e o tratamento de eventos repetidos ou atrasados na integração serão definidos no item 05; não presumir que uma operação de domínio repetida seja idempotente.

## Requisitos funcionais

| ID | Requisito |
| --- | --- |
| RF01 | Modelar pedido e itens com os dados, invariantes, isolamento e snapshot das regras 1–5. Construção inválida não produz pedido válido. |
| RF02 | Validar moedas e preços e calcular subtotal e total conforme regras 6–8, sem perda de precisão ou conversão implícita. |
| RF03 | Criar pedido em `AWAITING_STOCK` e aplicar exclusivamente as transições da tabela; rejeitar operações inválidas sem alterar o pedido, conforme regras 9–12. |
| RF04 | Expor ao chamador falha identificável para dados inválidos e operações inválidas, sem definir resposta HTTP ou contrato de mensagem. |

## Requisitos não funcionais

- **RNF01 — Isolamento:** as regras e seus testes unitários não dependem de banco, Docker, HTTP, catálogo, Estoque ou Kafka.
- **RNF02 — Precisão:** usar aritmética decimal exata; validar escala antes de calcular, sem valores monetários binários ou arredondamento implícito.
- **RNF03 — Integridade:** preservar invariantes e snapshots mesmo se o chamador modificar a coleção ou os objetos usados na construção; não expor mutações que contornem a validação.
- **RNF04 — Evidência:** executar testes unitários ligados aos critérios, build e medição de cobertura conforme o projeto permitir; relatar resultados e limitações reais sem apresentar testes não executados como aprovados.
- **RNF05 — Dependência:** não iniciar implementação enquanto o item 01 estiver pendente; uma aprovação desta spec não conclui nem substitui as evidências exigidas pela spec anterior.

## Critérios de aceitação

| ID | Cenário verificável | Referências |
| --- | --- | --- |
| CA01 | Criar pedido válido com dois produtos distintos, UUIDs, nomes, preços, quantidades e uma moeda; o estado inicial é `AWAITING_STOCK`. Alterações posteriores nas entradas e na lista exposta não alteram o pedido nem seus snapshots. | RF01; RNF01, RNF03 |
| CA02 | Rejeitar pedido sem itens, lista/item nulo, identificador de pedido ou produto nulo, nome nulo/vazio/em branco, preço nulo/negativo e quantidade zero ou negativa; nenhum pedido válido é produzido. | RF01, RF04 |
| CA03 | Rejeitar moeda nula, inválida ou incompatível entre pedido e item, e produto repetido, inclusive quando snapshots ou quantidades divergirem. Não converter moedas nem consolidar itens. | RF01, RF02, RF04 |
| CA04 | Com itens de `10.25 × 2` e `0.10 × 3`, subtotais são `20.50` e `0.30` e total `20.80`. Preço zero e valores `1`, `1.0`, `1.000` são aceitos exatamente em escala 2; `1.001` é rejeitado. Testar soma e multiplicação com valores decimais sem perda de precisão. | RF02; RNF02 |
| CA05 | De `AWAITING_STOCK`, registrar aceitação produz `AWAITING_PAYMENT` e registrar recusa produz `REJECTED`, cada um em pedido independente; snapshots e totais permanecem inalterados. | RF03 |
| CA06 | Em `AWAITING_PAYMENT` e em `REJECTED`, testar ambas as operações de reserva: todas falham com erro identificável e estado, itens e total permanecem inalterados; isso inclui repetição da mesma operação e operação contrária. | RF03, RF04 |
| CA07 | Testes de domínio executam isolados de serviços externos e banco. Registrar comando, resultado e cobertura efetivamente medidos; não declarar sucesso se build/testes não puderem executar. | RNF01, RNF04 |
| CA08 | Antes de implementar, registrar aprovação explícita desta spec e evidência de conclusão do item 01, preservando pendências até sua efetiva validação. | RNF05 |

## Casos de erro

| Caso | Resultado proposto |
| --- | --- |
| Dados obrigatórios ausentes, quantidade inválida, preço inválido ou escala não representável | Rejeitar construção com erro identificável, sem arredondar ou criar pedido parcial utilizável. |
| Moeda desconhecida ou diferente, ou produto repetido | Rejeitar construção com erro identificável, sem conversão ou consolidação implícita. |
| Operação de reserva incompatível com o estado | Rejeitar operação com erro identificável e preservar estado e dados. |
| Dependência 01 não concluída, aprovação ausente ou build indisponível | Manter implementação ou validação dependente pendente, com motivo e sem presumir sucesso. |

## Fora de escopo

API e DTOs, consulta ao catálogo, validação de produto ativo ou ausente, persistência e esquema físico de pedidos, migrations de negócio, repositórios, Outbox, processamento e deduplicação de eventos, contratos HTTP/Kafka, integração real com outros serviços, cancelamento, pagamento, notificações, impostos, descontos, frete e conversão de moeda. Políticas de concorrência otimista e reidratação persistida serão definidas nas funcionalidades que as exigirem.

## Decisões aprovadas

O usuário aprovou esta especificação, incluindo escala uniforme de duas casas e rejeição sem arredondamento; rejeição de produtos repetidos; rejeição de moedas diferentes e aceitação de códigos ISO 4217 reconhecidos; validação de snapshot e invariantes acima; e rejeição de transições repetidas no domínio. A escala uniforme limita o uso de moedas com outra quantidade convencional de casas decimais. Não há contrato de catálogo ou evento proposto neste documento.
