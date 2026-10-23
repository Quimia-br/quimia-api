package com.api.quimia.domain.insights.internal.usecase;

import com.api.quimia.domain.insights.internal.dto.CardView;
import com.api.quimia.domain.insights.internal.dto.EstatisticasView;
import com.api.quimia.domain.insights.internal.dto.ResumoEmpresaView;
import com.api.quimia.domain.insights.internal.dto.SerieItem;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dois cards fixos (D7). `q` filtra pelo título, sem acento e sem diferenciar caixa; `%` e `_` são texto. */
@Service
public class ConsultarEstatisticasUseCase {
    private static final int TERMO_MAXIMO = 100;
    private static final Pattern MARCAS_DE_ACENTO = Pattern.compile("\\p{M}");

    private final ResumoDaEmpresa resumo;

    public ConsultarEstatisticasUseCase(ResumoDaEmpresa resumo) {
        this.resumo = resumo;
    }

    @Transactional(readOnly = true)
    public EstatisticasView execute(Integer empresaId, String termo) {
        if (termo != null && termo.length() > TERMO_MAXIMO) {
            throw new InsightsException("termo_muito_longo", 400);
        }
        ResumoEmpresaView dados = resumo.carregar(empresaId);
        List<CardView> cards = List.of(
                new CardView("produtos_em_estantes", "Produtos em estantes", "numero", dados.produtosEmEstantes(), List.of()),
                new CardView(
                        "taxa_sucesso_misturas",
                        "Taxa de sucesso das misturas",
                        "rosca",
                        null,
                        List.of(
                                new SerieItem("Bem sucedida", dados.misturas().bemSucedidas()),
                                new SerieItem("Mal sucedida", dados.misturas().malSucedidas()))));
        String procurado = normalizar(termo);
        return new EstatisticasView(cards.stream()
                .filter(card -> procurado.isEmpty() || normalizar(card.titulo()).contains(procurado))
                .toList());
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String decomposto = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD);
        return MARCAS_DE_ACENTO.matcher(decomposto).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
