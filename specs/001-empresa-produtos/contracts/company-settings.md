# Contrato `settings` da Empresa

O PUT continua aceitando `version`, `data`, `context` e `address`. Campos legados omitidos permanecem gravados (`registrationDate`, `mission`, `targetAudience`, `differentiators` e os demais já existentes). `id`, `clerkOrgId` e `organizationId` enviados pelo cliente são ignorados: a empresa é a organização do JWT (`o.id`, com fallback `org_id`).

O Web confirma o salvamento só quando a resposta devolve `settings` com os mesmos valores enviados. O Core grava esse objeto e o ecoa sem reescrever rótulos. `version` e `organizationId` dentro de `settings` são ignorados na comparação; a versão vigente é a de primeiro nível.

## PUT /api/company

```json
{
  "version": 0,
  "data": {
    "legalName": "LhamaLog Tecnologia LTDA",
    "tradeName": "LhamaLog",
    "document": "11.444.777/0001-61",
    "registrationDate": "2020-01-02",
    "size": "11 - 50 colaboradores"
  },
  "context": {
    "sector": "Tecnologia / SaaS",
    "language": "Português (Brasil)",
    "mission": "Monitoramento",
    "targetAudience": "Times de operação",
    "differentiators": "Vários serviços"
  },
  "address": {
    "street": "Avenida Brasil",
    "number": "1234",
    "complement": "Sala 502",
    "district": "Centro",
    "city": "Maringá",
    "state": "Paraná",
    "country": "Brasil",
    "postalCode": "87000-000"
  },
  "settings": {
    "data": {
      "legalName": "LhamaLog Tecnologia LTDA",
      "tradeName": "LhamaLog",
      "document": "11.444.777/0001-61",
      "email": "contato@example.com",
      "stateRegistration": "123",
      "sector": "Tecnologia / SaaS",
      "description": "Monitoramento",
      "website": "https://example.com",
      "size": "11 - 50 colaboradores",
      "timeZone": "(UTC-03:00) Brasília",
      "language": "Português (Brasil)",
      "logo": {
        "fileName": "logo.png",
        "mediaType": "image/png",
        "dataBase64": "<base64>"
      }
    },
    "context": {
      "environmentTypes": ["Produção", "Homologação"],
      "monitoredSystems": ["API", "Backend"],
      "criticalWindows": "Horário comercial e deploys",
      "description": "Vários serviços"
    },
    "address": {
      "postalCode": "87000-000",
      "country": "Brasil",
      "street": "Avenida Brasil",
      "number": "1234",
      "complement": "Sala 502",
      "district": "Centro",
      "city": "Maringá",
      "state": "Paraná",
      "reference": "Praça",
      "fiscalAddress": true
    },
    "preferences": {
      "currency": "Real (BRL)",
      "dateFormat": "dd/mm/aaaa",
      "timeFormat": "24 horas",
      "dailySummary": true,
      "autoSuggestions": false,
      "telemetry": {
        "interval": "Últimas 24 horas",
        "initialEnvironment": "Todos os ambientes",
        "logOrder": "Mais recentes primeiro",
        "activeProductsOnly": true
      },
      "ai": {
        "detailLevel": "Alto",
        "tone": "Objetivo e técnico",
        "priority": "Criticidade",
        "probableCause": true,
        "correctiveAction": true,
        "relateSimilar": false
      },
      "notifications": { "criticalImmediate": true, "recurringFailures": false },
      "security": { "requireMfa": true, "approvedIntegrations": false, "maskSensitive": true }
    }
  }
}
```

`logo: null` remove o arquivo. PNG, JPG ou SVG até 2 MB, reconhecidos pelo conteúdo. SVG com script, evento ou URL externa é recusado; o conteúdo aceito volta no mesmo Base64.

Sistemas monitorados aceitos, no texto enviado: `API`, `Backend`, `Frontend`, `Banco de Dados`, `Workers`, `Mobile`, `Infraestrutura`.

Cadastro incompleto pode ser gravado. CNPJ, e-mail, site e CEP são validados quando preenchidos. A primeira gravação usa `version: null`. Conflito de versão: 409.

## GET /api/company

Devolve os campos legados e `settings` no mesmo formato. Sem `settings` gravado, o Core monta o objeto a partir dos campos antigos: setor e idioma vêm de `context`, a descrição da empresa vem de `mission`, a descrição do contexto vem de `differentiators`, e as preferências ficam vazias. `organizationId` e `clerkOrgId` são o id da organização. `summary.metrics`, `summary.ingestion` e verificação da empresa não são inventados (`verified: false`, `verification: "unavailable"`).

## O que não é aplicado

Guardar `settings.preferences.security` não liga MFA da Clerk, não abre integração e não mascara relatório. O mesmo vale para resumo diário, sugestões de IA e padrões de telemetria. A resposta traz `limitations[].enforced: false`.

Não há geocodificação, consulta de CEP, PDF no servidor nem sincronização. Estes caminhos respondem 501, sem sucesso fictício:

- `GET /api/company/postal-code/{cep}`
- `POST /api/company/actions/export-pdf`
- `POST /api/company/actions/sync`
