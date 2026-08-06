# test/.../persistence/

Testes de repositorio e queries.

Use `@DataJpaTest` para slice ou `@SpringBootTest` + Testcontainers
quando precisar do banco real. Convencao: `<Repository>Test` (slice)
ou `<Repository>IT` (com banco).
