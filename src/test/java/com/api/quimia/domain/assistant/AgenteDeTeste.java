package com.api.quimia.domain.assistant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor HTTP de verdade no loopback, no lugar do quimia-agent: responde a cada pedido com o que o teste programar,
 * inclusive atraso, e guarda os pedidos recebidos. Usa sockets bloqueantes (sem seletor NIO), como o cliente.
 */
final class AgenteDeTeste implements AutoCloseable {
    record Pedido(String metodo, String caminho, String autorizacao, String tipo, String corpo) {}

    private static final String RESPOSTA_PADRAO = "{\"resposta\":\"ok\"}";

    private final ServerSocket servidor;
    private final List<Pedido> pedidos = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile byte[] corpo = RESPOSTA_PADRAO.getBytes(StandardCharsets.UTF_8);
    private volatile Duration atraso = Duration.ZERO;

    AgenteDeTeste() throws IOException {
        servidor = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread aceitar = new Thread(this::aceitar, "agente-de-teste");
        aceitar.setDaemon(true);
        aceitar.start();
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getLocalPort();
    }

    AgenteDeTeste responder(int novoStatus, String novoCorpo) {
        return responder(novoStatus, novoCorpo.getBytes(StandardCharsets.UTF_8));
    }

    AgenteDeTeste responder(int novoStatus, byte[] novoCorpo) {
        status = novoStatus;
        corpo = novoCorpo;
        return this;
    }

    AgenteDeTeste atrasar(Duration novoAtraso) {
        atraso = novoAtraso;
        return this;
    }

    List<Pedido> pedidos() {
        return List.copyOf(pedidos);
    }

    private void aceitar() {
        while (!servidor.isClosed()) {
            try {
                Socket conexao = servidor.accept();
                Thread atender = new Thread(() -> atender(conexao), "agente-de-teste-conexao");
                atender.setDaemon(true);
                atender.start();
            } catch (SocketException fechado) {
                return;
            } catch (IOException falha) {
                throw new IllegalStateException(falha);
            }
        }
    }

    private void atender(Socket conexao) {
        try (conexao) {
            InputStream entrada = conexao.getInputStream();
            String[] linhaInicial = linha(entrada).split(" ");
            Map<String, String> cabecalhos = new HashMap<>();
            for (String cabecalho = linha(entrada); !cabecalho.isEmpty(); cabecalho = linha(entrada)) {
                int separador = cabecalho.indexOf(':');
                cabecalhos.put(cabecalho.substring(0, separador).trim().toLowerCase(Locale.ROOT),
                        cabecalho.substring(separador + 1).trim());
            }
            int tamanho = Integer.parseInt(cabecalhos.getOrDefault("content-length", "0"));
            pedidos.add(new Pedido(
                    linhaInicial[0],
                    linhaInicial[1],
                    cabecalhos.get("authorization"),
                    cabecalhos.get("content-type"),
                    new String(entrada.readNBytes(tamanho), StandardCharsets.UTF_8)));
            if (!atraso.isZero()) {
                Thread.sleep(atraso.toMillis());
            }
            byte[] resposta = corpo;
            OutputStream saida = conexao.getOutputStream();
            saida.write(("HTTP/1.1 " + status + " Status\r\nContent-Type: application/json\r\nContent-Length: "
                            + resposta.length + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            saida.write(resposta);
            saida.flush();
        } catch (IOException desistiu) {
            // O cliente desistiu (prazo esgotado) ou fechou a conexão: nada a responder.
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
        }
    }

    private static String linha(InputStream entrada) throws IOException {
        ByteArrayOutputStream linha = new ByteArrayOutputStream();
        for (int lido = entrada.read(); lido != -1 && lido != '\n'; lido = entrada.read()) {
            if (lido != '\r') {
                linha.write(lido);
            }
        }
        return linha.toString(StandardCharsets.US_ASCII);
    }

    @Override
    public void close() throws IOException {
        servidor.close();
    }
}
