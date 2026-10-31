package com.api.quimia.domain.kpi.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.kpi.internal.dto.ConsultasDiariasView;
import com.api.quimia.domain.kpi.internal.dto.DauDiarioView;
import com.api.quimia.domain.kpi.internal.dto.PerfilUsuariosView;
import com.api.quimia.domain.kpi.internal.dto.TopProdutoView;
import com.api.quimia.domain.kpi.internal.usecase.ConsultarKpisUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Empresa: KPIs")
@RestController
@RequestMapping("/api/v1/empresas/me/kpis")
public class KpiEmpresaController {
    private final ConsultarKpisUseCase kpis;

    public KpiEmpresaController(ConsultarKpisUseCase kpis) {
        this.kpis = kpis;
    }

    @Operation(
            summary = "Usuários ativos por dia (gráfico de linha)",
            description = "Usuários distintos que consultaram produtos da empresa em cada dia, com variação, médias, "
                    + "desvio padrão e z-score. A empresa vem do token. Lista vazia quando não há consultas.")
    @ApiResponse(responseCode = "200", description = "Série diária, do dia mais antigo ao mais recente.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @GetMapping("/dau")
    public List<DauDiarioView> dau(@AuthenticationPrincipal AccountPrincipal principal) {
        return kpis.dau(principal.empresaId());
    }

    @Operation(
            summary = "Consultas por dia (gráfico de linha)",
            description = "Consultas aos produtos da empresa e usuários distintos por dia. A empresa vem do token. "
                    + "Lista vazia quando não há consultas.")
    @ApiResponse(responseCode = "200", description = "Série diária, do dia mais antigo ao mais recente.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @GetMapping("/recomendacoes")
    public List<ConsultasDiariasView> recomendacoes(@AuthenticationPrincipal AccountPrincipal principal) {
        return kpis.recomendacoes(principal.empresaId());
    }

    @Operation(
            summary = "Produtos mais consultados (gráfico de barras)",
            description = "Ranking dos produtos da empresa por consultas. Empates dividem a posição. A empresa vem do "
                    + "token.")
    @Parameter(name = "limite", description = "Quantos produtos devolver, de 1 a 50.", example = "10")
    @ApiResponse(responseCode = "200", description = "Produtos do mais ao menos consultado.")
    @ApiResponse(responseCode = "400", description = "`limite` fora de 1 a 50: `limite_invalido`.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @GetMapping("/top-produtos")
    public List<TopProdutoView> topProdutos(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "limite", defaultValue = "" + ConsultarKpisUseCase.LIMITE_PADRAO) int limite) {
        return kpis.topProdutos(principal.empresaId(), limite);
    }

    @Operation(
            summary = "Perfil de quem consulta (gráfico de barras por estado)",
            description = "Usuários distintos que consultaram produtos da empresa, por nível de acesso e estado. Sem "
                    + "nome nem e-mail. A empresa vem do token.")
    @ApiResponse(responseCode = "200", description = "Contagens, da maior para a menor.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @GetMapping("/perfil-usuarios")
    public List<PerfilUsuariosView> perfilUsuarios(@AuthenticationPrincipal AccountPrincipal principal) {
        return kpis.perfilUsuarios(principal.empresaId());
    }
}
