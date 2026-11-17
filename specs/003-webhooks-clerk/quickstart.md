# Quickstart: testar os webhooks da Clerk (LLT-164)

## Automático

```powershell
.\mvnw.cmd clean verify
```

## Manual (instância de desenvolvimento)

1. Siga o quickstart da LLT-163 (variáveis da Clerk, infra local, Tenant de teste no Mongo).
2. Instale uma ferramenta de túnel HTTPS (ex.: ngrok ou Cloudflare Tunnel) e exponha a porta 8080 do Core. Anote a URL pública que ela mostrar.
3. No Dashboard da Clerk > **Webhooks** > **Add Endpoint**: URL `<url do túnel>/webhooks/clerk`; eventos `user.created`, `user.updated`, `user.deleted`, `organizationMembership.created`, `organizationMembership.updated`, `organizationMembership.deleted`.
4. Copie o **Signing Secret** do endpoint e exporte antes de subir o Core:
   ```powershell
   $env:CLERK_WEBHOOK_SIGNING_SECRET="<signing secret do endpoint>"
   make run-local
   ```
5. Na aba **Testing** do endpoint, envie um `organizationMembership.created` de exemplo (ou convide alguém para a organização do Tenant de teste no lhamalog-web) e confira a coleção:
   ```javascript
   db.member.find({ tenantId: "<tenantId do Tenant de teste>" })
   ```
6. Reenvie a mesma mensagem pela aba **Logs/Attempts** (mesmo `svix-id`): a resposta é 200 e nada muda.
7. A URL do túnel muda a cada execução nas versões gratuitas: atualize o endpoint no Dashboard quando isso acontecer.
