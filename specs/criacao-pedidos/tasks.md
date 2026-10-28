# Tarefas — Criação de pedidos com catálogo e Outbox

Estado: **aprovada, implementada e validada em 28/10/2026**. Item 04 do [roadmap](../../docs/implementation-roadmap.md), conforme [spec.md](spec.md). Aprovação explícita recebida com “implemente”, após apresentação dos contratos e decisões.

## Análise e aprovação

- [x] T01 — Conferir dependências concluídas 01–03, entidades/factories Lombok, V1/V2, configurações e testes existentes; identificar limites de integração real. Evidência: leitura documental e do código nesta preparação. Referências: contexto; RNF01–RNF03.
- [x] T02 — Elaborar contratos propostos de API, catálogo, envelope e erros, regras preservadas, critérios e tarefas desta funcionalidade. Referências: RF01–RF08.
- [x] T03 — Apresentar proposta e dúvidas ao usuário; obter aprovação explícita de contratos, repetição sem idempotência, autenticação fora do recorte, timeouts e ajuste aditivo da factory. Se houver mudança, revisar a spec antes da implementação. Referência: RNF06.

## Implementação — somente após T03

- [x] T04 — Criar DTOs de criação/resposta e endpoint `POST /orders`, aplicando validação estrita de estrutura/tipos/campos e duplicatas antes do catálogo. Não serializar entidades JPA. Referências: RF01, RF06; CA01–CA02; RNF03.
- [x] T05 — Implementar cliente HTTP concreto de catálogo usando a stack existente, URL externa e timeouts positivos configuráveis; consultas sequenciais, sem redirects/retries/cache/fallback. Validar todos os campos/tipos/identidade e mapear falhas do catálogo. Referências: RF02–RF03, RF06; CA03, CA08; RNF01, RNF04.
- [x] T06 — Coordenar obtenção de snapshots fora da transação, formação por `Order.create`, moeda única e cálculos existentes. Gerar UUIDs no backend; não introduzir preço do request ou estoque presumido. Referências: RF03; CA01, CA03, CA06; RNF02–RNF04.
- [x] T07 — Adicionar sobrecarga mínima validada da factory de Outbox com UUID explícito, preservando a factory atual. Modelar/serializar o envelope aprovado, com `messageId = outbox.id`, metadados, produtos e quantidades. Não alterar schema ou tornar payload mutável. Referências: RF05; CA04; RNF02–RNF03.
- [x] T08 — Implementar persistência de pedido/itens e uma Outbox em uma única transação local, com resposta somente após commit. Usar mecanismo Spring de transação efetivo, sem autoinvocação que ignore o proxy, transação separada de Outbox ou escrita após commit. Criar somente os meios de persistência necessários. Referências: RF04; CA01, CA05–CA06; RNF03–RNF04.
- [x] T09 — Aplicar respostas/erros da spec sem vazar stack trace, SQL ou resposta bruta do catálogo; manter falhas da dependência distintas de input inválido. Preservar semântica de repetição sem idempotência, sem retries da criação. Referências: RF06–RF07; CA02–CA03, CA06–CA07.
- [x] T10 — Fornecer configuração explícita de catálogo aos contextos/testes existentes, sem criar fallback de produção ou iniciar Kafka. Validar ausência/invalidade da URL e timeouts. Referências: RF02, RF08; CA08; RNF01–RNF02.

## Testes e validação — após implementação correspondente

- [x] T11 — Testar unidade de coordenação/validação e contrato HTTP da API: resposta, total `20.80`, origem dos dados e todos os inputs inválidos definidos. Confirmar que input inválido não chama catálogo nem persistência. Referências: CA01–CA02; RF01, RF03, RF06.
- [x] T12 — Testar cliente com servidor HTTP simulado, verificando rota, dados, status, timeout, conexão recusada, parsing e falhas do contrato; comprovar ausência de retries/fallback. Testar valores monetários aceitos/rejeitados sem perda de precisão. Referência: CA03; RF02–RF03, RF06; RNF01, RNF04.
- [x] T13 — Testar sobrecarga da factory (incluindo UUID nulo), regressão da factory existente e contrato do envelope. Na integração, desserializar a linha efetivamente gravada e confrontar IDs, metadados e todos os itens com o pedido. Referência: CA04; RF05; RNF02.
- [x] T14 — Testar criação real com HTTP simulado e PostgreSQL Testcontainers, migrations V1/V2 e consulta posterior em novo contexto para comprovar commit de pedido/itens/Outbox e snapshots. Não usar transação externa de teste que masque o commit. Referência: CA01; RF04; RNF01–RNF02.
- [x] T15 — Forçar falha real de inserção da Outbox no caminho transacional de produção após flush do pedido/itens; comprovar rollback de toda a tentativa e preservação de dados anteriores via nova conexão. Simular falha de serialização e verificar que não há sucesso ou escrita parcial. Referências: CA05–CA06; RF04, RF06; RNF01–RNF02.
- [x] T16 — Verificar consultas HTTP fora da transação de escrita e resposta após commit, sem depender de lazy no controller. Testar repetição do mesmo request com dois pedidos/comandos independentes. Referências: CA06–CA07; RF04, RF07; RNF04.
- [x] T17 — Testar configuração inválida/ausente, adaptar contextos anteriores e executar regressões sem Kafka ou serviços reais. Referência: CA08; RF08; RNF01–RNF02.
- [x] T18 — Executar `bash ./mvnw clean verify`; conferir relatórios Surefire, JaCoCo e artefato, registrar comandos, contagens e cobertura das novas unidades efetivamente medidos. Falta de Docker/HTTP simulado disponível é bloqueio de validação, sem trocar por banco local ou declarar sucesso. Referência: CA09; RNF05.

## Documentação e encerramento

- [x] T19 — Documentar contratos aprovados, configuração de catálogo e exemplos, erros, limitação de idempotência e risco de repetição após resposta perdida. Atualizar estado/roadmap somente com evidências de conclusão; distinguir HTTP simulado/PostgreSQL de integração real. Referências: RF08; CA07–CA09.
- [x] T20 — Relatar arquivos, testes executados, cobertura, riscos e pendências; concluir somente com critérios demonstrados. Não iniciar item 05, commitar ou invocar revisão automaticamente. Revisão independente depende de solicitação do usuário conforme AGENTS.md. Referência: RNF05–RNF06.

## Checklist

- [x] Spec e tarefas próprias de `criacao-pedidos` elaboradas.
- [x] Aprovação explícita registrada e decisões pendentes resolvidas.
- [x] API e erros conforme contrato aprovado; dados históricos vêm do catálogo.
- [x] Contrato HTTP de catálogo testado com simulação, incluindo falhas/timeouts.
- [x] Pedido/itens/Outbox confirmados na mesma transação PostgreSQL.
- [x] Falha real de Outbox demonstra rollback de toda a criação.
- [x] Envelope persistido e identidade compartilhada validados; factory anterior preservada.
- [x] Repetição e limitação de idempotência testadas/documentadas.
- [x] Regressões, build e cobertura reais registrados.
- [x] Documentação e roadmap coerentes com evidências e limites externos.

## Evidências desta preparação

Somente análise do repositório e criação desta spec/tarefas, em 28/10/2026. Não foram executados build, testes, SQL, migrations ou integração; não há nova cobertura medida. Tarefas futuras permanecem abertas até aprovação e execução demonstrada.

## Evidências da implementação — 28/10/2026

- Aprovação explícita do usuário: “implemente”, após a entrega desta spec e tarefas. Implementação restrita ao item 04, preservando V1/V2, domínio e factory anterior da Outbox; a sobrecarga de UUID é aditiva.
- `POST /orders` implementado com DTOs, validação estrutural e de tipos estrita, duplicatas antes do catálogo e respostas sanitizadas. Erros HTTP do framework mantêm status e headers (incluindo `Allow` em 405).
- `CatalogClient` consulta HTTP real contra servidor simulado nos testes. Casos exercitados: snapshots exatos, dados adicionais, produto ausente/inativo, moedas distintas, respostas/campos/tipos inválidos, preços negativos ou que exigem arredondamento, status 3xx/401/403/429/5xx, conexão recusada, timeout antes dos headers e durante o corpo. Sem retries ou redirects de aplicação; configuração obrigatória/defaults/valores inválidos testados.
- `OrderService` consulta catálogo e serializa antes da transação, usa `TransactionTemplate`/`EntityManager` para pedido/itens/Outbox e retorna após commit. Testes unitários verificam ordenação, serialização antes da escrita e falha de commit; integração verifica ausência de transação durante consulta de catálogo e dados visíveis após retorno com Open Session in View desativado.
- O teste de rollback adiciona trigger e sequência somente ao PostgreSQL descartável. A trigger verifica pedido/itens já flushados, avança a sequência e força a falha de Outbox. O teste exige `is_called=true` depois do rollback, ausência das novas linhas e preservação de pedido anterior; remove esses objetos em `finally`. Não há serviço alternativo ou transação externa de teste mascarando o caminho de produção.
- Integração desserializa o JSON realmente persistido, verifica IDs compartilhados, tipo/versão, correlação/causalidade, instante, produtos/quantidades, tópico, chave e publicação nula. Repetir request cria pedidos e comandos distintos. Falhas do catálogo e de serialização não deixam criação parcial.
- Comando isolado final: `bash ./mvnw -q -Dtest=CatalogClientTest,CatalogPropertiesTest,OrderServiceTest,OrderControllerTest,MessageTest -Djacoco.destFile=target/jacoco-step4-unit-headers.exec test jacoco:report -Djacoco.dataFile=target/jacoco-step4-unit-headers.exec`. **20 testes passaram**, sem PostgreSQL; inclui testes unitários, cliente com HTTP simulado e slice MVC. XML preservado em `target/site/jacoco-unit/jacoco.xml`, dados em `target/jacoco-unit.exec`, antes/depois do clean da suíte completa. O relatório foi gerado em `target/site/jacoco/` pelo goal e copiado para conservar a medição isolada.
- Comando completo final: `bash ./mvnw clean verify`. **48 testes, zero falhas, erros ou skips**, JDK Amazon Corretto 25.0.4.1, Maven 3.10.0, PostgreSQL Testcontainers `postgres:16.9-alpine`. São os 28 testes anteriores, uma regressão adicional da factory, 15 novos testes unitários/slice e 4 de integração de criação. Relatórios Surefire em `target/surefire-reports/`; JaCoCo agregado em `target/site/jacoco/`.
- Cobertura por unidade (linhas) na medição isolada e agregada: `CatalogClient` 36/36, `CatalogProperties` 8/8, `OrderService` 34/34, `OrderController` 1/1, `OrderExceptionHandler` 7/7, `OrderConfiguration` 4/4, `OrderCreationException` 4/4, `OrderResponse` 2/2; records de request/item/snapshot/envelope/payload/erro 1/1 cada (100%). Branches: `CatalogClient` e `CatalogProperties` 19/20 cada (95%); `OrderService` 12/12 e handler 2/2 (100%). `OutboxMessage`, classe preexistente modificada, fica 10/12 linhas (83,3%) isoladamente e 12/12 (100%) agregadas, pois as duas linhas de seu callback JPA são exercitadas na integração; branches 8/8 em ambas. Não foi criado teste reflexivo de callback somente para aumentar percentual.
- JAR inspecionado: V1/V2 de produção presentes, sem migrations inválidas ou helpers/trigger de teste. V1/V2 conferidas byte a byte contra o index, que já incluía V2 da etapa anterior. `git diff --check` passou.
- Documentação: `docs/order-creation.md`, estado, roadmap e persistência atualizados, preservando alterações preexistentes. Nenhum teste usou banco local, Produtos/Estoque reais ou Kafka. Não houve revisão independente, commit ou início do item 05.

Limites: contratos externos validados somente por simulação; repetição após resposta perdida pode duplicar pedido; não há autenticação/propriedade do comprador neste recorte aprovado. A validação integrada real e a publicação/consumo permanecem pendentes.
