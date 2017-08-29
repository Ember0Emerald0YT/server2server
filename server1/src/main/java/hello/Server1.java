package hello;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.client.*;
import io.undertow.connector.ByteBufferPool;
import io.undertow.io.Receiver;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Methods;
import io.undertow.util.StringReadChannelListener;
import io.undertow.util.StringWriteChannelListener;
import org.xnio.*;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.ssl.XnioSsl;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class Server1 {
    private static final char[] STORE_PASSWORD = "password".toCharArray();

    public static final AttachmentKey<String> RESPONSE_BODY = AttachmentKey.create(String.class);

    public static final int BUFFER_SIZE = 8192 * 3;
    public static final OptionMap DEFAULT_OPTIONS = OptionMap.builder()
            .set(Options.WORKER_IO_THREADS, 8)
            .set(Options.TCP_NODELAY, true)
            .set(Options.KEEP_ALIVE, true)
            .set(Options.WORKER_NAME, "Client").getMap();
    public static final ByteBufferPool POOL = new DefaultByteBufferPool(true, BUFFER_SIZE, 1000, 10, 100);
    public static final ByteBufferPool SSL_BUFFER_POOL = new DefaultByteBufferPool(true, 17 * 1024);
    public static XnioWorker WORKER;
    public static XnioSsl SSL;
    public static ClientConnection connection;

    public static void main(final String[] args) throws Exception {
        String version = System.getProperty("java.version");
        System.out.println("Java version " + version);
        if(version.charAt(0) == '1' && Integer.parseInt(version.charAt(2) + "") < 8 ) {
            System.out.println("This example requires Java 1.8 or later");
            System.out.println("The HTTP2 spec requires certain cyphers that are not present in older JVM's");
            System.out.println("See section 9.2.2 of the HTTP2 specification for details");
            System.exit(1);
        }
        String bindAddress = System.getProperty("bind.address", "localhost");
        SSLContext sslContext = createSSLContext(loadKeyStore("/server.keystore"), loadKeyStore("/server.truststore"));
        Undertow server = Undertow.builder()
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .addHttpListener(7001, bindAddress)
                .addHttpsListener(7443, bindAddress, sslContext)
                .setHandler(getHandler()).build();

        server.start();
    }

    private static HttpHandler getHandler() throws Exception {
        SSLContext clientSslContext = createSSLContext(loadKeyStore("/client.keystore"), loadKeyStore("/client.truststore"));
        UndertowClient client = UndertowClient.getInstance();
        final Xnio xnio = Xnio.getInstance();
        WORKER = xnio.createWorker(null, DEFAULT_OPTIONS);
        SSL = new UndertowXnioSsl(WORKER.getXnio(), OptionMap.EMPTY, SSL_BUFFER_POOL, clientSslContext);
        connection = client.connect(new URI("h2c-prior://localhost:8443"), WORKER, SSL, POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();

        return Handlers.routing()
                .add(Methods.GET, "/get", exchange -> {
                    // call server2 get endpoint with UndertowClient
                    final CountDownLatch latch = new CountDownLatch(1);
                    final AtomicReference<ClientResponse> reference = new AtomicReference<>();
                    try {
                        ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath("/get");
                        connection.sendRequest(request, createClientCallback(reference, latch));
                        latch.await();
                        int statusCode = reference.get().getResponseCode();
                        String body = reference.get().getAttachment(RESPONSE_BODY);
                        if(statusCode == 200) {
                            exchange.getResponseSender().send(body);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .add(Methods.POST, "/post", exchange -> exchange.getRequestReceiver().receiveFullString(new Receiver.FullStringCallback() {
                    @Override
                    public void handle(HttpServerExchange exchange, String message) {
                        // call server2 post endpoint with UndertowClient
                        exchange.getResponseSender().send(message + " World");
                    }
                }));
    }


    public static ClientCallback<ClientExchange> createClientCallback(final AtomicReference<ClientResponse> reference, final CountDownLatch latch) {
        return new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {
                result.setResponseListener(new ClientCallback<ClientExchange>() {
                    @Override
                    public void completed(final ClientExchange result) {
                        reference.set(result.getResponse());
                        new StringReadChannelListener(result.getConnection().getBufferPool()) {

                            @Override
                            protected void stringDone(String string) {
                                result.getResponse().putAttachment(RESPONSE_BODY, string);
                                latch.countDown();
                            }

                            @Override
                            protected void error(IOException e) {
                                e.printStackTrace();

                                latch.countDown();
                            }
                        }.setup(result.getResponseChannel());
                    }

                    @Override
                    public void failed(IOException e) {
                        e.printStackTrace();

                        latch.countDown();
                    }
                });
                try {
                    result.getRequestChannel().shutdownWrites();
                    if(!result.getRequestChannel().flush()) {
                        result.getRequestChannel().getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(null, null));
                        result.getRequestChannel().resumeWrites();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    latch.countDown();
                }
            }

            @Override
            public void failed(IOException e) {
                e.printStackTrace();
                latch.countDown();
            }
        };
    }

    public static ClientCallback<ClientExchange> createClientCallback(final AtomicReference<ClientResponse> reference, final CountDownLatch latch, final String requestBody) {
        return new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {
                new StringWriteChannelListener(requestBody).setup(result.getRequestChannel());
                result.setResponseListener(new ClientCallback<ClientExchange>() {
                    @Override
                    public void completed(ClientExchange result) {
                        reference.set(result.getResponse());
                        new StringReadChannelListener(POOL) {
                            @Override
                            protected void stringDone(String string) {
                                result.getResponse().putAttachment(RESPONSE_BODY, string);
                                latch.countDown();
                            }

                            @Override
                            protected void error(IOException e) {
                                e.printStackTrace();
                                latch.countDown();
                            }
                        }.setup(result.getResponseChannel());
                    }

                    @Override
                    public void failed(IOException e) {
                        e.printStackTrace();
                        latch.countDown();
                    }
                });
            }

            @Override
            public void failed(IOException e) {
                e.printStackTrace();
                latch.countDown();
            }
        };
    }

    private static KeyStore loadKeyStore(String name) throws Exception {
        String storeLoc = System.getProperty(name);
        final InputStream stream;
        if(storeLoc == null) {
            stream = Server1.class.getResourceAsStream(name);
        } else {
            stream = Files.newInputStream(Paths.get(storeLoc));
        }

        if(stream == null) {
            throw new RuntimeException("Could not load keystore");
        }
        try(InputStream is = stream) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(is, password(name));
            return loadedKeystore;
        }
    }

    static char[] password(String name) {
        String pw = System.getProperty(name + ".password");
        return pw != null ? pw.toCharArray() : STORE_PASSWORD;
    }


    private static SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore) throws Exception {
        KeyManager[] keyManagers;
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password("key"));
        keyManagers = keyManagerFactory.getKeyManagers();

        TrustManager[] trustManagers;
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        trustManagers = trustManagerFactory.getTrustManagers();

        SSLContext sslContext;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);

        return sslContext;
    }

}
