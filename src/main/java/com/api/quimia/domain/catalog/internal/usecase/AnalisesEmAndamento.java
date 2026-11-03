package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.internal.dto.AnaliseDeFdsResponse;
import com.api.quimia.domain.catalog.internal.dto.AnaliseEmAndamentoResponse;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Análises de FDS em segundo plano. O estado fica em memória neste processo (a API roda em uma instância): um reinício
 * perde as análises em andamento, e o portal refaz o envio. Cada análise pertence à empresa que a iniciou, e uma
 * consulta de outra empresa não a encontra.
 */
@Component
class AnalisesEmAndamento {
    static final int LIMITE_EM_ANDAMENTO = 6;
    static final Duration VALIDADE = Duration.ofMinutes(15);
    private static final Logger log = LoggerFactory.getLogger(AnalisesEmAndamento.class);
    private static final int BYTES_DO_ID = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ERRO_INESPERADO = "analise_falhou";

    private static final class Registro {
        final String id;
        final Integer empresaId;
        volatile String status = AnaliseEmAndamentoResponse.PROCESSANDO;
        volatile AnaliseDeFdsResponse analise;
        volatile Integer codigo;
        volatile String detail;
        volatile Instant finalizadaEm;

        Registro(String id, Integer empresaId) {
            this.id = id;
            this.empresaId = empresaId;
        }

        AnaliseEmAndamentoResponse resposta() {
            return new AnaliseEmAndamentoResponse(id, status, analise, codigo, detail);
        }
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Clock relogio;

    AnalisesEmAndamento() {
        this(Clock.systemUTC());
    }

    AnalisesEmAndamento(Clock relogio) {
        this.relogio = relogio;
    }

    /** Agenda o trabalho e devolve o estado inicial; acima do limite, `429 analises_demais`. */
    synchronized AnaliseEmAndamentoResponse iniciar(Integer empresaId, Supplier<AnaliseDeFdsResponse> trabalho) {
        descartarVencidas();
        long emAndamento = registros.values().stream()
                .filter(registro -> AnaliseEmAndamentoResponse.PROCESSANDO.equals(registro.status))
                .count();
        if (emAndamento >= LIMITE_EM_ANDAMENTO) {
            throw new CatalogException("analises_demais", 429);
        }
        Registro registro = new Registro(novoId(), empresaId);
        registros.put(registro.id, registro);
        executor.execute(() -> executar(registro, trabalho));
        return registro.resposta();
    }

    Optional<AnaliseEmAndamentoResponse> consultar(Integer empresaId, String id) {
        descartarVencidas();
        return Optional.ofNullable(registros.get(id))
                .filter(registro -> registro.empresaId.equals(empresaId))
                .map(Registro::resposta);
    }

    private void executar(Registro registro, Supplier<AnaliseDeFdsResponse> trabalho) {
        try {
            registro.analise = trabalho.get();
            registro.status = AnaliseEmAndamentoResponse.CONCLUIDA;
        } catch (CatalogException recusada) {
            registro.codigo = recusada.status();
            registro.detail = recusada.code();
            registro.status = AnaliseEmAndamentoResponse.FALHOU;
        } catch (RuntimeException inesperada) {
            log.error("falha inesperada na análise de FDS {}", registro.id, inesperada);
            registro.codigo = 500;
            registro.detail = ERRO_INESPERADO;
            registro.status = AnaliseEmAndamentoResponse.FALHOU;
        } finally {
            registro.finalizadaEm = relogio.instant();
        }
    }

    private void descartarVencidas() {
        Instant limite = relogio.instant().minus(VALIDADE);
        registros.values().removeIf(registro -> registro.finalizadaEm != null && registro.finalizadaEm.isBefore(limite));
    }

    private static String novoId() {
        byte[] bytes = new byte[BYTES_DO_ID];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }
}
