# Contrato: configuração das organizações de teste (perfil local)

## Entrada

| Variável | Propriedade | Perfil | Formato | Padrão |
|---|---|---|---|---|
| `DEV_TENANT_CLERK_ORG_ID` | `lhamalog.dev-tenant.clerk-org-id` | só `local` | lista separada por vírgula de `org_...` | vazio (nada é provisionado) |

Exemplos (valores fictícios):

```text
DEV_TENANT_CLERK_ORG_ID=org_exemploA1
DEV_TENANT_CLERK_ORG_ID=org_exemploA1,org_exemploB2
DEV_TENANT_CLERK_ORG_ID= org_exemploA1 , org_exemploA1,,org_exemploB2
```

O terceiro exemplo é equivalente ao segundo: espaços, item vazio e repetido são ignorados.

## Comportamento na subida

1. Fora do perfil `local`: nenhuma leitura, nenhum Tenant criado, nenhum log deste contrato.
2. No perfil `local`, para cada item válido, na ordem da lista:
   - sem Tenant para o `clerkOrgId`: cria `ACTIVE` e registra `dev_tenant.seed.created`;
   - com Tenant `ACTIVE`: não altera e registra `dev_tenant.seed.exists`;
   - com Tenant em outro status: não altera e registra `dev_tenant.seed.inactive` (WARN).
3. Item inválido: ignorado, com `dev_tenant.seed.invalid_org_id` (WARN).
4. Nenhum caso impede a subida da aplicação.

## Logs

Formato da constituição VI, em inglês:

```text
event=dev_tenant.seed.created | Development tenant created | clerkOrgId=<id> tenantId=<uuid>
event=dev_tenant.seed.exists | Development tenant already exists | clerkOrgId=<id> status=ACTIVE
event=dev_tenant.seed.inactive | Development tenant exists but is not active; access stays denied | clerkOrgId=<id> status=<status>
event=dev_tenant.seed.invalid_org_id | Ignored value that is not a Clerk organization id | value=<valor>
```
