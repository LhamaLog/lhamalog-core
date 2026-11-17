# Research: Webhooks da Clerk (LLT-164)

Data: 2026-11-06. Decisões tomadas pela IA por delegação do desenvolvedor.

Fontes: ADR_022, ADR_033, ADR_121, ADR_122, ADR_123, ADR_159; https://docs.svix.com/receiving/verifying-payloads/how-manual e https://clerk.com/docs/guides/development/webhooks/overview (consultadas em 2026-11-06).

## R1. Verificação da assinatura sem dependência

- **Decisão**: `SvixSignatureVerifier` com `javax.crypto.Mac` (HmacSHA256). Conteúdo assinado: `${svix-id}.${svix-timestamp}.${corpo bruto}`. Chave: segredo sem o prefixo `whsec_`, decodificado de Base64. Header `svix-signature`: lista separada por espaço de `v1,<base64>`; basta uma igual, comparada com `MessageDigest.isEqual` (tempo constante). Tolerância de timestamp: ±5 min (`clerk.webhook.tolerance`).
- **Por quê**: o algoritmo é curto e documentado; `com.svix:svix` está [PENDENTE] no doc de dependências e traria cliente HTTP e modelos que não usamos.
- **Corpo bruto**: o controller recebe `@RequestBody byte[]` (conversor de bytes, sem passar por JSON) e só faz o parse depois da verificação.

## R2. Tenant no webhook (ADR_033 x ADR_121)

- **Decisão**: o webhook não cria Tenant. A ADR_121 (11/10) substitui a parte de tenant por usuário da ADR_030 e define que o Tenant nasce na compra. A ADR_033 (02/10) é anterior e foi escrita para o modelo antigo (tenant por empresa + usuário + produto). Evento de vínculo de organização sem Tenant: 2xx, ignorado, log `INFO` com o `clerkOrgId`.
- **Risco aceito**: um vínculo criado antes de o Tenant existir não é reprocessado depois. O fluxo de compra cria o Membro DONO diretamente; convites posteriores chegam com o Tenant já existente.

## R3. Onde fica o usuário

- **Decisão**: no Membro (ADR_159). Nome e e-mail principal são gravados no Membro (necessários para notificações, ADR_159), sem outros dados pessoais. `user.created` sem Membro não grava nada; o vínculo traz `public_user_data` (nome e identificador).
- **Usuário apagado**: coleção `deleted_clerk_user` (clerkUserId, deletedAt, tenantId nulo) como marcador permanente, para cobrir `user.deleted` que chega antes de qualquer vínculo.

## R4. Idempotência e ordem

- **Deduplicação**: coleção `clerk_webhook_event` com `_id = svix-id` (único por natureza), `type`, `tenantId` (do vínculo, ou nulo), `processedAt` com índice TTL de 7 dias. O registro é gravado **depois** do processamento com sucesso; falha não registra, e o reenvio é processado. Duas entregas simultâneas do mesmo id podem processar as duas vezes, o que é seguro porque as escritas são idempotentes; a segunda inserção do registro é ignorada (chave duplicada).
- **Versões**: o Membro guarda `membershipVersion` e `userVersion` (ms). Vínculo criado/atualizado usa `data.updated_at`; vínculo removido e `user.deleted` usam o `timestamp` do envelope (o dado não muda o `updated_at` na remoção). Só se aplica versão estritamente maior.
- **Escrita condicional**: leitura, decisão no domínio e escrita com condição nas versões lidas (concorrência otimista). Se outra escrita ganhou a corrida, a operação falha e responde 5xx; o reenvio do Svix reaplica. Sem transações, porque o Mongo standalone não as suporta.
- **Remoção antes da criação**: `organizationMembership.deleted` para vínculo inexistente grava o Membro já removido, com a versão da remoção; o `created` atrasado é mais antigo e é ignorado.

## R5. Papéis (ADR_123)

- `org:admin` → ADMIN; `org:member` → MEMBRO; outro valor → MEMBRO com `WARN` (menor privilégio).
- DONO nunca é alterado por sincronização (só o fluxo de compra atribui DONO). A remoção do vínculo de um DONO é aplicada (não é rebaixamento) e gera `WARN`.

## R6. Respostas HTTP

| Situação | Resposta |
|---|---|
| Processado, duplicado, ignorado ou desconhecido | 200 `{"status":"ok"}` |
| Assinatura/headers/timestamp inválidos, segredo não configurado, JSON inválido | 400 `{"error":"invalid_webhook"}` |
| Falha de processamento | 500 `{"error":"processing_failed"}` |

## R7. Configuração

| Propriedade | Variável | Padrão |
|---|---|---|
| `clerk.webhook.signing-secret` | `CLERK_WEBHOOK_SIGNING_SECRET` | local: vazio (rejeita tudo com aviso); prod: obrigatório |
| `clerk.webhook.tolerance` | `CLERK_WEBHOOK_TOLERANCE` | `5m` |

Record próprio (`ClerkWebhookProperties`), separado do `ClerkProperties` da LLT-163.

## R8. Testes

- `SvixSignatureVerifierTests` (unidade): assinatura assinada no teste com implementação própria do teste.
- `ClerkWebhookTests` (MockMvc + Testcontainers): cenários das histórias. A falha de processamento é provocada de verdade com um *validator* de coleção do MongoDB (`collMod`) que recusa escritas em `member`, sem mock.
- Segredo de teste fictício gerado no teste e registrado por `DynamicPropertyRegistrar` (o perfil local, que tem precedência sobre o `config/application.properties` de teste, define o segredo vazio).

## R10. Ajustes feitos na implementação (2026-11-06)

- O parser usa um `JsonMapper` próprio (Jackson 3, já no classpath) em vez do bean do Spring, para não depender da configuração global de JSON.
- `user.created` e `user.updated` têm o mesmo tratamento (atualizar perfil dos Membros existentes): `user.created` com Membro já existente acontece quando o vínculo chega primeiro.
- Depois de aplicado um `user.*`, eventos de vínculo deixam de sobrescrever nome e e-mail (o dado do usuário é a fonte mais precisa; o do vínculo é `public_user_data`).
- O `identifier` do vínculo só é usado como e-mail se contiver `@` (pode ser username ou telefone).
- O `svix-id` vem do chamador; só entra em log se parecer um id (`[A-Za-z0-9_-]{1,64}`), para evitar injeção em log.
- **Convergência (2026-11-13, T019):** o Spring Data omite campos nulos ao gravar, então `clerk_webhook_event` de eventos sem tenant ficava sem o campo `tenantId`, contra a FR-011 ("nulo de forma explícita"). O campo passou a usar `@Field(write = ALWAYS)`, e o teste lê o documento bruto para conferir que ele existe com valor nulo.

## R9. Túnel em desenvolvimento

Documentado no README sem URLs reais: subir o Core, expor a porta 8080 com um túnel HTTPS (ex.: ngrok ou Cloudflare Tunnel, instalado pelo desenvolvedor), cadastrar `<url do túnel>/webhooks/clerk` em Dashboard > Webhooks com os eventos da FR-005, copiar o Signing Secret para `CLERK_WEBHOOK_SIGNING_SECRET` e usar a aba Testing para enviar exemplos.
