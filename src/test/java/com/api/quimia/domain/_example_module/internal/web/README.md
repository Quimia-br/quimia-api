# test/.../web/

Testes do adaptador HTTP do modulo.

- `<Controller>Test` para slice tests com `@WebMvcTest`.
- `<Controller>IT` para integracao end-to-end do controller com
  `@SpringBootTest` (rodam em fase Maven separada via Failsafe se
  configurado).
