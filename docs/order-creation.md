# Criação de pedidos

Contrato aprovado do item 04: [spec](../specs/criacao-pedidos/spec.md) e [tarefas/evidências](../specs/criacao-pedidos/tasks.md).

`POST /orders` recebe JSON com produtos e quantidades:

```json
{"items":[{"productId":"11111111-1111-4111-8111-111111111111","quantity":2}]}
```

Campos desconhecidos, produtos repetidos, UUIDs inválidos e quantidades nulas, não positivas, fracionárias, em string ou fora do intervalo de `Integer` são rejeitados antes de consultar o catálogo. Nome, preço e moeda não são recebidos do cliente.

O sucesso retorna `201` com `id`, `status: AWAITING_STOCK`, `total`, `currency` e `createdAt`. Isso confirma o commit do pedido e do comando pendente; não confirma reserva de estoque. Não há autenticação, identificação do comprador, consulta de pedidos ou idempotência nesta etapa. Repetir uma requisição válida cria outro pedido; perder a resposta após o commit pode causar duplicação se o cliente repetir.

## Catálogo e configuração

Defina `catalog.base-url` (variável `CATALOG_BASE_URL`) para a URL HTTP(S) do catálogo. Não há URL/fallback de produção: ausência ou configuração inválida impede a inicialização. O perfil local continua usando a conexão PostgreSQL documentada em [persistence.md](persistence.md).

| Propriedade | Variável de ambiente | Padrão |
| --- | --- | --- |
| `catalog.base-url` | `CATALOG_BASE_URL` | Obrigatória |
| `catalog.connect-timeout` | `CATALOG_CONNECT_TIMEOUT` | `1s` |
| `catalog.read-timeout` | `CATALOG_READ_TIMEOUT` | `2s` |

Os timeouts devem ser positivos e valem por consulta. O cliente faz `GET /products/{productId}`, sequencialmente, sem redirects, cache, fallback ou retries da aplicação. Espera `application/json` com `productId`, `name`, `price`, `currency` e `active`. Campos adicionais do catálogo são tolerados; campos obrigatórios ausentes, tipos incompatíveis, identidade divergente e valores monetários inválidos são falhas da dependência.

Todos os produtos devem estar ativos e usar uma única moeda ISO 4217 reconhecida pelo domínio. Preços são decimais exatos, não negativos e representáveis em duas casas, sem arredondamento.

## Transação e comando

As consultas HTTP e a serialização precedem a transação de escrita. `TransactionTemplate` confirma pedido, itens e Outbox juntos antes de o serviço devolver a resposta. Uma falha na Outbox reverte também pedido e itens.

A Outbox armazena `ReserveStock` versão 1 em `inventory.commands`, com chave textual `orderId`, `published_at` nulo e envelope com `messageId`, `type`, `version`, `orderId`, `correlationId`, `causationId`, `occurredAt` e `payload.items`. `messageId` coincide com o ID da Outbox, `correlationId` com o pedido e `causationId` é nulo. O payload contém somente produtos e quantidades. A factory anterior de Outbox permanece disponível; uma sobrecarga permite fornecer o UUID previamente gerado para serialização.

Esta etapa apenas grava o comando. Não conecta ao Kafka nem marca publicação. Publicação e consumo permanecem nos itens 05/06.

## Erros

As respostas de erro contêm apenas `code` e `message`, sem stack trace, SQL ou corpo bruto do catálogo.

| HTTP | Código | Situação |
| --- | --- | --- |
| 400 | `INVALID_ORDER_REQUEST` | Entrada inválida ou produto repetido |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Tipo de conteúdo incompatível |
| 422 | `PRODUCT_NOT_FOUND` | Catálogo respondeu 404 |
| 422 | `PRODUCT_INACTIVE` | Produto inativo |
| 422 | `MIXED_CURRENCIES` | Moedas diferentes |
| 503 | `CATALOG_UNAVAILABLE` | Falha de conexão, timeout, 429 ou 5xx |
| 502 | `INVALID_CATALOG_RESPONSE` | Outro status inesperado ou resposta inválida |
| 500 | `ORDER_CREATION_FAILED` | Falha interna, serialização ou persistência |

## Limites de validação

Os contratos foram definidos localmente para Pedidos. Os testes usam um servidor HTTP simulado e PostgreSQL Testcontainers, sem Produtos, Estoque, Kafka ou banco local. A compatibilidade com os serviços reais permanece pendente; não há garantia de disponibilidade de estoque ou snapshot simultâneo entre consultas de produtos.
