# Conventional Commits — Padrão Organizacional

Aplicável a todos os repositórios da organização. Cada projeto pode documentar scopes e convenções específicas em seu próprio `CONTRIBUTING.md`.

---

## Estrutura

```text
<tipo>[(<scope>)]: <descrição em PT-BR>

[corpo opcional]

[footer opcional: BREAKING CHANGE, Refs, Co-authored-by]
```

| Parte | Idioma | Obrigatoriedade |
|---|---|---|
| Tipo | Inglês | Obrigatório |
| Scope | Inglês | Opcional |
| Descrição | PT-BR | Obrigatório |
| Corpo | PT-BR | Opcional |
| Footer | Conforme a chave utilizada | Opcional |

---

## Tipos permitidos

| Tipo | Quando usar |
|---|---|
| `feat` | Nova funcionalidade ou capacidade |
| `fix` | Correção de bug |
| `perf` | Melhoria de performance sem alterar o comportamento esperado |
| `refactor` | Mudança interna sem alterar o comportamento esperado |
| `docs` | Apenas documentação |
| `test` | Adição ou correção de testes |
| `build` | Build, dependências, imagens ou empacotamento |
| `ci` | Pipelines e automações de CI/CD |
| `chore` | Manutenção que não se encaixa nos tipos acima |
| `revert` | Reversão de commit anterior |

Breaking changes usam `!` após o tipo ou scope e, quando necessário, o footer `BREAKING CHANGE:`.

Exemplos:

```text
feat(auth)!: altera formato do token de autenticação
refactor!: reorganiza API pública do pacote
```

---

## Regras de formatação

- **Descrição**: curta, em minúsculas, sem ponto final
- **Idioma**: tipo e scope em inglês; descrição e corpo em PT-BR
- **Scope**: use quando ajudar a identificar claramente a área afetada
- **Header**: mantenha conciso; prefira até 72 caracteres quando possível
- **Corpo**: opcional; use para contexto, motivação ou decisões que não cabem no header
- **Breaking change**: declare com `!` e/ou `BREAKING CHANGE:`
- **Commits**: mantenha cada commit coerente e compreensível isoladamente

---

## Scopes

Scopes identificam a área afetada e são opcionais. Projetos podem documentar scopes recorrentes no `CONTRIBUTING.md`, sem necessidade de manter uma lista organizacional fechada.

Exemplos:

```text
auth
user
company
cart
product
order
payment
config
infra
cache
logging
security
notification
```

Evite criar scopes excessivamente específicos ou diferentes para o mesmo conceito.

---

## Exemplos

```text
feat(user): adiciona endpoint de atualização de perfil
fix(payment): corrige cálculo de desconto quando cupom é nulo
refactor(order): extrai lógica de validação
test(product): adiciona testes de integração para busca por categoria
perf(cart): elimina consulta N+1
build: atualiza dependências do projeto
ci: adiciona verificação de tipos ao pipeline
docs: documenta variáveis de ambiente
chore: remove configuração não utilizada
```

Breaking change com referência ao ClickUp:

```text
feat(auth)!: migra autenticação para OAuth2

BREAKING CHANGE: clientes devem usar o novo formato de autenticação.
Refs: QUI-86e33y130
```

---

## Evite

| Evite | Exemplo |
|---|---|
| Commit sem tipo | `arruma bug do login` |
| Tipo fora do vocabulário | `update:`, `wip:`, `hotfix:` |
| Descrição genérica | `fix: ajustes` |
| Descrição em inglês | `feat(user): add user endpoint` |
| Misturar mudanças sem relação no mesmo commit | Separe quando isso melhorar a revisão ou reversão |
| Breaking change sem indicação explícita | Use `!` e/ou `BREAKING CHANGE:` |

---

## Referência rápida

```text
feat(user): adiciona autenticação via OAuth2
─────┬─── ──┬─ ───────────────────┬────────────────
     │      │                     └ descrição em PT-BR
     │      └ scope opcional em inglês
     └ tipo em inglês
```
