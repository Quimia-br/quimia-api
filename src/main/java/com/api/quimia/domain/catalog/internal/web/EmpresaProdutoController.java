package com.api.quimia.domain.catalog.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.dto.AlteracaoProdutoForm;
import com.api.quimia.domain.catalog.internal.dto.CadastroProdutoForm;
import com.api.quimia.domain.catalog.internal.dto.CadastroProdutoResponse;
import com.api.quimia.domain.catalog.internal.usecase.AlterarProdutoEmpresaUseCase;
import com.api.quimia.domain.catalog.internal.usecase.CadastrarProdutoEmpresaUseCase;
import com.api.quimia.domain.catalog.internal.usecase.ListarProdutosEmpresaUseCase;
import com.api.quimia.domain.catalog.internal.usecase.ListarProdutosUseCase;
import com.api.quimia.domain.catalog.internal.usecase.ListarTiposDeProdutoUseCase;
import com.api.quimia.domain.catalog.internal.usecase.RemoverProdutoEmpresaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** "Produtos cadastrados" do portal (titular empresa). Cadastro e alteração em multipart/form-data. */
@Tag(name = "Empresa: produtos")
@RestController
@RequestMapping("/api/v1/empresas/me/produtos")
public class EmpresaProdutoController {
    private final ListarProdutosEmpresaUseCase listar;
    private final CadastrarProdutoEmpresaUseCase cadastrar;
    private final AlterarProdutoEmpresaUseCase alterar;
    private final RemoverProdutoEmpresaUseCase remover;
    private final ListarTiposDeProdutoUseCase listarTipos;

    public EmpresaProdutoController(
            ListarProdutosEmpresaUseCase listar,
            CadastrarProdutoEmpresaUseCase cadastrar,
            AlterarProdutoEmpresaUseCase alterar,
            RemoverProdutoEmpresaUseCase remover,
            ListarTiposDeProdutoUseCase listarTipos) {
        this.listar = listar;
        this.cadastrar = cadastrar;
        this.alterar = alterar;
        this.remover = remover;
        this.listarTipos = listarTipos;
    }

    @Operation(
            summary = "Produtos cadastrados pela empresa",
            description = "Produtos com vínculo ativo da empresa. `fields` escolhe em que campos `q` procura (os "
                    + "chips Nome, Tipo e Código de barras do portal); sem `fields`, procura em nome, "
                    + "descrição e marca. Ordena por `sort` e `direcao`.")
    @Parameter(name = "q", description = "Termo de busca (até 100 caracteres).", example = "desinfetante")
    @Parameter(
            name = "fields",
            description = "Campos da busca, separados por vírgula: nome, tipo, codBarras.",
            example = "nome,tipo")
    @Parameter(name = "tipo", description = "Filtra pelo tipo do produto.", example = "desinfetante")
    @Parameter(
            name = "codBarras",
            description = "Filtra pelo código de barras (UPC-A ou EAN-13).",
            example = "7891234567895")
    @Parameter(
            name = "sort",
            description = "Coluna da ordenação: nome (padrão), tipo, descricao ou codBarras.",
            example = "nome")
    @Parameter(name = "direcao", description = "asc (padrão) ou desc.", example = "asc")
    @Parameter(name = "page", description = "Página, a partir de 0.", example = "0")
    @Parameter(name = "size", description = "Itens por página, de 1 a 50.", example = "20")
    @ApiResponse(responseCode = "200", description = "Página de produtos.")
    @ApiResponse(
            responseCode = "400",
            description = "Paginação fora das regras (página negativa, tamanho fora de 1 a 50 ou página × tamanho "
                    + "acima do maior int): `paginacao_invalida`. Termo de busca acima de 100 caracteres: "
                    + "`termo_muito_longo`. Campo de busca, tipo ou ordenação fora da lista: "
                    + "`campo_busca_invalido`, `tipo_invalido`, `ordenacao_invalida`.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @GetMapping
    public Pagina<ProdutoView> listar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "q", required = false) String termo,
            @RequestParam(required = false) List<String> fields,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String codBarras,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direcao,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarProdutosUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(principal.empresaId(), termo, fields, tipo, codBarras, sort, direcao, pagina, tamanho);
    }

    /**
     * Opções do campo "Tipo" do formulário de cadastro: array de códigos (`limpeza_geral`, `desinfetante`, ...), todos,
     * sem paginação. O portal traduz os códigos para o texto exibido.
     */
    @Operation(
            summary = "Tipos de produto",
            description = "Opções do campo \"Tipo\" do formulário de cadastro: array com todos os códigos, sem "
                    + "paginação (`limpeza_geral`, `desinfetante`, `desincrustante`, `desengraxante`, `alvejante`, "
                    + "`aromatizante`, `outro`). São os mesmos valores que o cadastro aceita.")
    @ApiResponse(responseCode = "200", description = "Códigos dos tipos de produto.")
    @GetMapping("/tipos")
    public List<String> tipos() {
        return listarTipos.execute();
    }

    /** `201` quando o produto foi criado; `200` quando a empresa foi vinculada ou reativada. */
    @Operation(
            summary = "Cadastrar produto",
            description = "Multipart com os dados do produto, a `imagem` opcional (JPEG, PNG ou WebP até 5 MB) e "
                    + "a `fds` em PDF (até 10 MB). Se já existir produto com o mesmo código de barras, a "
                    + "empresa só é vinculada a ele (sem imagem nem FDS). A FDS vai para o armazenamento e "
                    + "para o extrator, que devolve a composição; com `QUIMIA_FDS_OBRIGATORIA=true`, produto "
                    + "novo exige a FDS. Em vez do PDF, `analiseFds` (o `analiseId` de `POST "
                    + "/produtos/fds/analises`) cadastra a FDS já revisada, com as `correcoesFds` da empresa.")
    @ApiResponse(responseCode = "201", description = "Produto criado.")
    @ApiResponse(responseCode = "200", description = "Empresa vinculada (ou reativada) a um produto que já existia.")
    @ApiResponse(
            responseCode = "400",
            description = "Nome só com espaços: `nome_invalido`. Tipo fora da lista: `tipo_invalido`. `fds` e "
                    + "`analiseFds` juntos: `fds_e_analise_juntas`. `correcoesFds` sem `analiseFds`: "
                    + "`correcoes_sem_analise`.")
    @ApiResponse(responseCode = "409", description = "Empresa já tem esse produto: `produto_ja_cadastrado`.")
    @ApiResponse(responseCode = "410", description = "A análise da FDS passou da validade: `analise_expirada`.")
    @ApiResponse(
            responseCode = "422",
            description = "Código de barras inválido: `codigo_barras_invalido`. Produto novo sem FDS com a FDS "
                    + "obrigatória: `fds_obrigatoria`. PDF não reconhecido como FDS: `fds_invalida`. O "
                    + "extrator não reconheceu o PDF como FDS ou não achou texto legível: `fds_invalida`. "
                    + "`analiseFds` de outra empresa ou adulterado: `analise_invalida`. Correção que não cabe na "
                    + "FDS: `correcao_invalida`.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @ApiResponse(
            responseCode = "413",
            description = "Imagem acima de 5 MB: `imagem_muito_grande`. PDF da FDS acima de 10 MB: "
                    + "`pdf_muito_grande`.")
    @ApiResponse(
            responseCode = "415",
            description = "Arquivo que não é JPEG, PNG ou WebP pelo conteúdo: `imagem_invalida`. Arquivo `fds` "
                    + "que não começa com `%PDF-`: `pdf_invalido`.")
    @ApiResponse(
            responseCode = "502",
            description = "O armazenamento de imagens recusou o envio: `armazenamento_imagem_falhou`. Extrator ou "
                    + "armazenamento do PDF responderam com erro: `extrator_falhou`, "
                    + "`armazenamento_fds_falhou`.")
    @ApiResponse(
            responseCode = "503",
            description = "Armazenamento de imagens desligado (sem `CLOUDINARY_*`) ou fora do ar: "
                    + "`armazenamento_imagem_indisponivel`. Extrator ou armazenamento do PDF desligados ou "
                    + "fora do ar: `extrator_indisponivel`, `armazenamento_fds_indisponivel`.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CadastroProdutoResponse> cadastrar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @ModelAttribute CadastroProdutoForm form) {
        CadastroProdutoResponse resposta = cadastrar.execute(principal.empresaId(), form);
        HttpStatus status = CadastrarProdutoEmpresaUseCase.CRIADO.equals(resposta.situacao())
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(resposta);
    }

    @Operation(
            summary = "Alterar produto",
            description = "Multipart; altera só os campos enviados. `imagem` troca a foto; `fds` grava uma FDS "
                    + "nova e desativa a anterior; `analiseFds` e `correcoesFds` fazem o mesmo com uma análise já "
                    + "revisada, no lugar do PDF. Produto vinculado a outra empresa ativa não pode ser "
                    + "alterado.")
    @Parameter(name = "id", description = "Id do produto.", example = "42")
    @ApiResponse(responseCode = "200", description = "Produto alterado.")
    @ApiResponse(
            responseCode = "400",
            description = "Nome só com espaços: `nome_invalido`. Tipo fora da lista: `tipo_invalido`. `fds` e "
                    + "`analiseFds` juntos: `fds_e_analise_juntas`. `correcoesFds` sem `analiseFds`: "
                    + "`correcoes_sem_analise`.")
    @ApiResponse(responseCode = "410", description = "A análise da FDS passou da validade: `analise_expirada`.")
    @ApiResponse(
            responseCode = "404",
            description = "Produto sem vínculo ativo com a empresa: `produto_nao_encontrado`. Empresa do token "
                    + "não existe mais: `empresa_nao_encontrada`.")
    @ApiResponse(
            responseCode = "409",
            description = "Código de barras de outro produto ou produto compartilhado com outra empresa: "
                    + "`cod_barras_em_uso`, `produto_compartilhado`.")
    @ApiResponse(
            responseCode = "422",
            description = "Código de barras inválido: `codigo_barras_invalido`. PDF não reconhecido como FDS: "
                    + "`fds_invalida`. O extrator não reconheceu o PDF como FDS ou não achou texto legível: "
                    + "`fds_invalida`. `analiseFds` de outra empresa ou adulterado: `analise_invalida`. Correção "
                    + "que não cabe na FDS: `correcao_invalida`.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(
            responseCode = "413",
            description = "Imagem acima de 5 MB: `imagem_muito_grande`. PDF da FDS acima de 10 MB: "
                    + "`pdf_muito_grande`.")
    @ApiResponse(
            responseCode = "415",
            description = "Arquivo que não é JPEG, PNG ou WebP pelo conteúdo: `imagem_invalida`. Arquivo `fds` "
                    + "que não começa com `%PDF-`: `pdf_invalido`.")
    @ApiResponse(
            responseCode = "502",
            description = "O armazenamento de imagens recusou o envio: `armazenamento_imagem_falhou`. Extrator ou "
                    + "armazenamento do PDF responderam com erro: `extrator_falhou`, "
                    + "`armazenamento_fds_falhou`.")
    @ApiResponse(
            responseCode = "503",
            description = "Armazenamento de imagens desligado (sem `CLOUDINARY_*`) ou fora do ar: "
                    + "`armazenamento_imagem_indisponivel`. Extrator ou armazenamento do PDF desligados ou "
                    + "fora do ar: `extrator_indisponivel`, `armazenamento_fds_indisponivel`.")
    @PatchMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProdutoView alterar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable Integer id,
            @Valid @ModelAttribute AlteracaoProdutoForm form) {
        return alterar.execute(principal.empresaId(), id, form);
    }

    @Operation(
            summary = "Remover produto do portal",
            description = "Desativa o vínculo da empresa com o produto. O produto sai do app quando nenhuma "
                    + "empresa ativa o tiver.")
    @Parameter(name = "id", description = "Id do produto.", example = "42")
    @ApiResponse(responseCode = "204", description = "Vínculo desativado.")
    @ApiResponse(
            responseCode = "404",
            description = "Produto sem vínculo ativo com a empresa: `produto_nao_encontrado`. Empresa do token "
                    + "não existe mais: `empresa_nao_encontrada`.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer id) {
        remover.execute(principal.empresaId(), id);
    }
}
