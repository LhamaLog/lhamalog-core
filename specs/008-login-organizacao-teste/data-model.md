# Data Model: Login e organização de teste para desenvolvimento

Nenhuma coleção nova. A feature usa a coleção `tenant` da LLT-163 e uma configuração local.

## Organização de teste (configuração)

| Campo | Origem | Regra |
|---|---|---|
| `clerkOrgId` | item da lista `DEV_TENANT_CLERK_ORG_ID` | casa com `^org_[A-Za-z0-9]+$`; espaços removidos; repetidos ignorados |

- A lista pode ter zero, um ou vários itens, separados por vírgula.
- Só é lida no perfil `local`.

## Tenant de desenvolvimento (coleção `tenant`)

Mesmo documento do Tenant real (ADR_121, `TenantDocument`).

| Campo | Valor na criação | Depois |
|---|---|---|
| `_id` | UUID novo | nunca muda |
| `clerkOrgId` | o item da lista | nunca muda |
| `name` | `Tenant de desenvolvimento` | nunca muda pelo provisionamento |
| `status` | `ACTIVE` | nunca muda pelo provisionamento (um Tenant suspenso à mão continua suspenso) |

### Transições

```text
(não existe) --subida local com o org_id na lista--> ACTIVE   [event=dev_tenant.seed.created]
ACTIVE       --nova subida-->                         ACTIVE   [event=dev_tenant.seed.exists]
SUSPENDED/…  --nova subida-->                         igual    [event=dev_tenant.seed.inactive, WARN]
```

## Resultado do roteiro (fora do repositório)

| Campo | Regra |
|---|---|
| `item` | V1 a V10 do [quickstart.md](quickstart.md) |
| `data` | data da execução |
| `resultado` | passou / falhou / não se aplica |
| `observação` | texto livre, sem token, senha, chave ou e-mail real |

Registrado como comentário no PR #6.
