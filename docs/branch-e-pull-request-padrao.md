# Branches e Pull Requests — Padrão Organizacional

Aplicável a todos os repositórios da organização.

---

## Estratégia de branch

O modelo adotado é **GitHub Flow** com branches de curta duração.

```text
main (protegida, sempre estável)
  ├── QUI-86e33y130
  ├── QUI-86e33y131
  └── QUI-86e33y132
```

### Regras gerais

- Toda branch de trabalho nasce de `main`
- A branch é removida após o merge do PR
- Evite branches de longa duração; prefira PRs pequenos e frequentes
- Push direto em `main` é proibido
- Force push em `main` é proibido
- Em branches com PR aberto, evite reescrever o histórico depois que a revisão começar

---

## Nomenclatura de branch

Cada branch corresponde a uma tarefa do ClickUp.

```text
QUI-<id-clickup>
```

Exemplo para a tarefa de ID `86e33y130`:

```text
QUI-86e33y130
```

### Regras de nomenclatura

- O prefixo `QUI-` é obrigatório
- O ID deve ser exatamente o ID da tarefa no ClickUp
- Não adicione tipo, descrição ou nome do desenvolvedor à branch

O contexto da mudança é descrito pelo título do PR e pelos commits, evitando duplicação no nome da branch.

---

## Estratégia de merge

O padrão adotado é **merge commit**.

- Preserva os commits da branch
- O merge do PR fica identificável em `main`
- O título do merge commit deve refletir o título do PR

Rebase ou squash podem ser adotados por um repositório quando houver uma justificativa específica, mas não devem ser misturados sem uma convenção definida.

---

## Proteção de `main`

Configuração base recomendada:

```text
Require pull request before merging
  └── Required approvals: 1
  └── Dismiss stale approvals on new commits: true
  └── Require review from Code Owners: true (quando CODEOWNERS existir e for aplicável)

Require status checks to pass before merging
  └── Checks obrigatórios de CI devem passar

Require conversation resolution before merging: true
```

Repositórios críticos podem exigir controles adicionais, como duas aprovações, merge queue ou restrição de bypass.

---

## Pull Request

### Título

O título do PR segue Conventional Commits:

```text
<tipo>[(<scope>)]: <descrição em PT-BR>
```

Exemplos:

```text
feat(user): adiciona autenticação via OAuth2
fix(cart): corrige total após remoção de item
refactor(order): extrai lógica de validação
```

O título deve resumir a mudança de forma suficiente para que o merge seja compreensível no histórico.

### Vínculo com o ClickUp

A tarefa correspondente é identificada pela branch:

```text
QUI-86e33y130
```

Quando for útil explicitar a referência também na descrição do PR ou em um commit:

```text
Refs: QUI-86e33y130
```

Não use `Closes:` para tarefas do ClickUp como se fossem GitHub Issues.

---

## Template de PR

Salvar em `.github/pull_request_template.md`.

```markdown
## O que muda

<!-- Resuma o que mudou e por quê. -->

## Como validar

<!-- Informe os passos relevantes para validar a mudança.
     Para alterações triviais, "ver CI" é suficiente. -->

## Checklist

- [ ] Título segue Conventional Commits
- [ ] CI está passando
- [ ] Não há credenciais, secrets ou chaves expostas

## Observações

<!-- Riscos, impactos, evidências, screenshots ou qualquer contexto adicional.
     Remova esta seção se não houver nada relevante. -->
```

O template deve servir como apoio à revisão, não como formulário burocrático. Adicione seções específicas apenas quando o tipo de projeto realmente precisar delas.

---

## Fluxo resumido

```text
1. Criar branch a partir de main
   └── QUI-86e33y130

2. Desenvolver com commits seguindo Conventional Commits
   └── feat(user): adiciona endpoint de cadastro
   └── test(user): adiciona testes de integração
   └── fix(user): corrige validação de e-mail duplicado

3. Abrir PR para main
   └── Título: feat(user): adiciona cadastro de usuário
   └── Branch identifica a tarefa do ClickUp

4. Review
   └── Aprovação obrigatória
   └── CI passando
   └── Conversas resolvidas

5. Merge em main
   └── Branch deletada após o merge
```
