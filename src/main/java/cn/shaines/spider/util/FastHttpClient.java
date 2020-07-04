package cn.shaines.spider.util;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Asserts;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * FastHttpClient 基于 apache HttpClient 5.0 进一步封装, 提供方便的API
 * 官网: http://hc.apache.org/httpcomponents-client-5.0.x/examples.html
 * <p>
 * 依赖:
 * <dependency>
 * <groupId>org.apache.httpcomponents.client5</groupId>
 * <artifactId>httpclient5</artifactId>
 * <version>5.0</version>
 * </dependency>
 * <p>
 * ------------------------------------------------------- 简单使用如下 -------------------------------------------------------
 * <p>
 * =============== GET ===============
 * FastHttpClient httpClient = FastHttpClient.builder().build();
 * HttpGet httpGet = httpClient.buildGet("https://www.baidu.com");
 * Response<String> result = httpClient.execute(httpGet, ResponseHandler.ofString());
 * String data = result.getData();
 * <p>
 * =============== POST ==============
 * FastHttpClient httpClient = FastHttpClient.builder().build();
 * HttpPost httpPost = httpClient.buildPost("https://search.jd.com/image?op=upload");
 * httpPost.addHeader("cookie", "shshsh1");
 * httpPost.addHeader("referer", "https://search.jd.com/image");
 * httpPost.addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Safari/537.36");
 * <p>
 * HttpEntity entity = MultipartEntityBuilder.create()
 * // 相当于<input type="file" name="file"/>
 * // .addPart("file", new FileBody(new File("C:\\Users\\houyu\\Desktop\\123.jpg"), ContentType.IMAGE_JPEG))
 * .addBinaryBody("file", new File("C:\\Users\\houyu\\Desktop\\123.jpg"), ContentType.IMAGE_JPEG, "123.jpg")
 * // 添加文本内容
 * // .addTextBody("type", "xxx")
 * .build();
 * httpPost.setEntity(entity);
 * Response<String> result = httpClient.execute(httpPost, ResponseHandler.ofString());
 * String data = result.getData();
 *
 * @author for.houyu@qq.com
 */
public class FastHttpClient {

    /***/
    private CloseableHttpClient closeableHttpClient = null;
    /***/
    private final PoolingHttpClientConnectionManager manager;
    private final HttpClientContext context;
    private final RequestConfig requestConfig;
    private final String userAgent;

    private FastHttpClient(FastHttpClientBuilder builder) {
        this.manager = builder.manager;
        this.context = builder.context;
        this.requestConfig = builder.requestConfig;
        this.userAgent = builder.userAgent;
        //
        this.init();
    }

    private void init() {
        /** 自定义参数创建 */
        if (closeableHttpClient == null) {
            closeableHttpClient = HttpClients.custom()
                    // 设置连接池管理器
                    .setConnectionManager(manager)
                    // 设置默认的请求配置
                    .setDefaultRequestConfig(requestConfig)
                    // 设置cookie管理 (这里不在这里使用cookie管理, 而是通过每次请求的时候手动设置context)
                    // .setDefaultCookieStore(cookieStore)
                    // 设置默认的 userAgent
                    .setUserAgent(userAgent)
                    // 构建
                    .build();
        }
    }

    public <T> Response<T> execute(ClassicHttpRequest request, ResponseHandler<T> responseHandler) {
        handleCookie(request);
        try (CloseableHttpResponse response = this.closeableHttpClient.execute(request, this.context)) {
            return responseHandler.handle(response, this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> Response<T> execute(HttpHost target, ClassicHttpRequest request, ResponseHandler<T> responseHandler) {
        handleCookie(request);
        try (CloseableHttpResponse response = this.closeableHttpClient.execute(target, request, this.context)) {
            return responseHandler.handle(response, this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ClassicHttpRequest handleCookie(ClassicHttpRequest request) {
        String cookie;
        if (this.context != null && !request.containsHeader("cookie") && (cookie = this.getCookie()) != null) {
            request.addHeader("cookie", cookie);
        }
        return request;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * 创建建造者
     *
     * @return
     */
    public static FastHttpClientBuilder builder() {
        return new FastHttpClientBuilder();
    }

    /**
     * 编码uri
     *
     * @param uri     uri
     * @param charset charset
     */
    public static String encodeUri(String uri, Charset charset) {
        if (uri.contains("?")) {
            String[] split = uri.split("\\?", 2);
            return split[0] + "?" + Arrays.stream(split[1].split("&"))
                    .map(v -> v.split("=", 2))
                    .map(v -> v[0] + "=" + encodeURLText(v[1], charset))
                    .collect(Collectors.joining("&"));
        }
        return uri;
    }

    public static String encodeURLText(String text, Charset charset) {
        try {
            return URLEncoder.encode(text, charset.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String decodeURLText(String text, Charset charset) {
        try {
            return URLDecoder.decode(text, charset.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private String fixUri(String uri) {
        if (uri.startsWith("//")) {
            return "http:" + uri;
        } else if (uri.startsWith("://")) {
            return "http" + uri;
        } else if (!uri.toLowerCase().startsWith("http")) {
            return "http://" + uri;
        }
        return uri;
    }

    public HttpGet buildGet(String uri) {
        return new HttpGet(fixUri(uri));
    }

    public HttpPost buildPost(String uri) {
        return new HttpPost(fixUri(uri));
    }

    public HttpPut buildPut(String uri) {
        return new HttpPut(fixUri(uri));
    }

    public HttpDelete buildDelete(String uri) {
        return new HttpDelete(fixUri(uri));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public CloseableHttpClient getCloseableHttpClient() {
        return closeableHttpClient;
    }

    public PoolingHttpClientConnectionManager getManager() {
        return manager;
    }

    public HttpClientContext getContext() {
        return context;
    }

    public RequestConfig getRequestConfig() {
        return requestConfig;
    }

    public String getCookie() {
        return Optional.ofNullable(this.getContext())
                .map(HttpClientContext::getCookieStore)
                .map(CookieStore::getCookies)
                .map(c -> c.stream().map(v -> v.getName() + "=" + v.getValue()).collect(Collectors.joining("; "))).orElse(null);
    }

    public Map<String, String> getCookieAsMap() {
        return Optional.ofNullable(this.getContext())
                .map(HttpClientContext::getCookieStore)
                .map(CookieStore::getCookies)
                .map(c -> c.stream().collect(Collectors.toMap(Cookie::getName, Cookie::getValue))).orElse(Collections.emptyMap());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * 响应处理器处理器
     *
     * @param <T>
     */
    public interface ResponseHandler<T> {
        /**
         * 下载回调
         *
         * @param response 响应
         * @param client   fastHttpClient
         * @return
         * @throws RuntimeException
         */
        Response<T> handle(ClassicHttpResponse response, FastHttpClient client) throws RuntimeException;

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        /**
         * 返回字符串
         *
         * @param defaultCharset 默认字符集
         */
        static ResponseHandler<String> ofString(Charset... defaultCharset) {
            return (response, client) -> {
                try {
                    Charset charset = defaultCharset != null && defaultCharset.length > 0 ? defaultCharset[0] : Charset.defaultCharset();
                    return Response.build(response, EntityUtils.toString(response.getEntity(), charset));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }

        /**
         * 保存为文件
         *
         * @param path    保存文件路径
         * @param options StandardCopyOption: REPLACE_EXISTING(替换更新), COPY_ATTRIBUTES(复制属性), ATOMIC_MOVE(原子移动)
         */
        static ResponseHandler<File> ofFile(Path path, CopyOption... options) {
            return (response, client) -> {
                File file = path.toFile();
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                }
                try (InputStream in = response.getEntity().getContent()) {
                    Files.copy(in, path, options);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return Response.build(response, file);
            };
        }
    }

    /**
     * 响应结果
     *
     * @param <T>
     */
    public static class Response<T> {

        private final int code;
        private final HttpEntity entity;
        private final T data;
        private final ProtocolVersion version;
        private final Locale locale;
        private final String reasonPhrase;

        public Response(int code, HttpEntity entity, T data, ProtocolVersion version, Locale locale, String reasonPhrase) {
            this.code = code;
            this.entity = entity;
            this.data = data;
            this.version = version;
            this.locale = locale;
            this.reasonPhrase = reasonPhrase;
        }

        public static <T> Response<T> build(ClassicHttpResponse response, T data) {
            return new Response<T>(response.getCode(), response.getEntity(), data, response.getVersion(), response.getLocale(), response.getReasonPhrase());
        }

        public int getCode() {
            return code;
        }

        public HttpEntity getEntity() {
            return entity;
        }

        public T getData() {
            return data;
        }

        public ProtocolVersion getVersion() {
            return version;
        }

        public Locale getLocale() {
            return locale;
        }

        public String getReasonPhrase() {
            return reasonPhrase;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Response{");
            sb.append("code=").append(code);
            sb.append(", entity=").append(entity);
            sb.append(", data=").append(data);
            sb.append(", version=").append(version);
            sb.append(", locale=").append(locale);
            sb.append(", reasonPhrase='").append(reasonPhrase).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * 建造者
     */
    public static class FastHttpClientBuilder {

        private PoolingHttpClientConnectionManager manager;
        private HttpClientContext context;
        private CookieStore cookieStore;
        private RequestConfig requestConfig;
        private String userAgent;

        public FastHttpClientBuilder() {
            /** 创建连接池管理器 */
            manager = new PoolingHttpClientConnectionManager();
            // 设置总的连接最大数
            manager.setMaxTotal(100);
            // 设置单独路由的连接最大数
            manager.setDefaultMaxPerRoute(manager.getMaxTotal() / 2);
            /** 创建上下文 */
            context = HttpClientContext.create();
            cookieStore = new BasicCookieStore();
            context.setCookieStore(cookieStore);
            /** 创建默认的请求配置 */
            requestConfig = RequestConfig.custom()
                    // 设置启用重定向
                    .setRedirectsEnabled(true)
                    // 设置最大重定向次数
                    .setMaxRedirects(30)
                    // 设置默认的cookie
                    // .setCookieSpec(cookie)
                    // 设置连接超时
                    .setConnectTimeout(2, TimeUnit.MINUTES)
                    // 设置响应超时
                    .setResponseTimeout(2, TimeUnit.MINUTES)
                    // 设置请求参数
                    .setConnectionRequestTimeout(2, TimeUnit.MINUTES)
                    // 构建
                    .build();
            /** 设置默认的userAgent */
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Safari/537.36";
        }

        public FastHttpClient build() {
            return new FastHttpClient(this);
        }

        public FastHttpClientBuilder setManager(PoolingHttpClientConnectionManager manager) {
            Asserts.notNull(manager, "manager");
            this.manager = manager;
            return this;
        }

        public FastHttpClientBuilder setContext(HttpClientContext context) {
            // Asserts.notNull(context, "context");
            this.context = context;
            return this;
        }

        public FastHttpClientBuilder setRequestConfig(RequestConfig requestConfig) {
            Asserts.notNull(requestConfig, "requestConfig");
            this.requestConfig = requestConfig;
            return this;
        }

        public FastHttpClientBuilder setUserAgent(String userAgent) {
            Asserts.notNull(userAgent, "userAgent");
            this.userAgent = userAgent;
            return this;
        }

        public FastHttpClientBuilder setCookie(String cookie) {
            Asserts.notNull(cookie, "cookie");
            Asserts.notNull(context, "context");
            //
            if (!cookie.trim().isEmpty()) {
                this.cookieStore = context.getCookieStore() == null ? this.cookieStore : context.getCookieStore();
                Arrays.stream(cookie.split("; ")).map(v -> v.split("=", 2)).forEach(v -> this.cookieStore.addCookie(new BasicClientCookie(v[0], v[1])));
                context.setCookieStore(this.cookieStore);
            }
            return this;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}

/*
public class ClientWithResponseHandler {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            final HttpGet httpget = new HttpGet("http://httpbin.org/get");

            System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());

            // Create a custom response handler
            final HttpClientResponseHandler<String> responseHandler = new HttpClientResponseHandler<String>() {

                @Override
                public String handleResponse(
                        final ClassicHttpResponse response) throws IOException {
                    final int status = response.getCode();
                    if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                        final HttpEntity entity = response.getEntity();
                        try {
                            return entity != null ? EntityUtils.toString(entity) : null;
                        } catch (final ParseException ex) {
                            throw new ClientProtocolException(ex);
                        }
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }

            };
            final String responseBody = httpclient.execute(httpget, responseHandler);
            System.out.println("----------------------------------------");
            System.out.println(responseBody);
        }
    }

}
 */

/*
public class ClientConfiguration {

    public final static void main(final String[] args) throws Exception {

        // Use custom message parser / writer to customize the way HTTP
        // messages are parsed from and written out to the data stream.
        final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory = new DefaultHttpResponseParserFactory() {

            @Override
            public HttpMessageParser<ClassicHttpResponse> create(final Http1Config h1Config) {
                final LineParser lineParser = new BasicLineParser() {

                    @Override
                    public Header parseHeader(final CharArrayBuffer buffer) {
                        try {
                            return super.parseHeader(buffer);
                        } catch (final ParseException ex) {
                            return new BasicHeader(buffer.toString(), null);
                        }
                    }

                };
                return new DefaultHttpResponseParser(lineParser, DefaultClassicHttpResponseFactory.INSTANCE, h1Config);
            }

        };
        final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory = new DefaultHttpRequestWriterFactory();

        // Create HTTP/1.1 protocol configuration
        final Http1Config h1Config = Http1Config.custom()
                .setMaxHeaderCount(200)
                .setMaxLineLength(2000)
                .build();
        // Create connection configuration
        final CharCodingConfig connectionConfig = CharCodingConfig.custom()
                .setMalformedInputAction(CodingErrorAction.IGNORE)
                .setUnmappableInputAction(CodingErrorAction.IGNORE)
                .setCharset(StandardCharsets.UTF_8)
                .build();

        // Use a custom connection factory to customize the process of
        // initialization of outgoing HTTP connections. Beside standard connection
        // configuration parameters HTTP connection factory can define message
        // parser / writer routines to be employed by individual connections.
        final HttpConnectionFactory<ManagedHttpClientConnection> connFactory = new ManagedHttpClientConnectionFactory(
                h1Config, connectionConfig, requestWriterFactory, responseParserFactory);

        // Client HTTP connection objects when fully initialized can be bound to
        // an arbitrary network socket. The process of network socket initialization,
        // its connection to a remote address and binding to a local one is controlled
        // by a connection socket factory.

        // SSL context for secure connections can be created either based on
        // system or application specific properties.
        final SSLContext sslcontext = SSLContexts.createSystemDefault();

        // Create a registry of custom connection socket factories for supported
        // protocol schemes.
        final Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.INSTANCE)
            .register("https", new SSLConnectionSocketFactory(sslcontext))
            .build();

        // Use custom DNS resolver to override the system DNS resolution.
        final DnsResolver dnsResolver = new SystemDefaultDnsResolver() {

            @Override
            public InetAddress[] resolve(final String host) throws UnknownHostException {
                if (host.equalsIgnoreCase("myhost")) {
                    return new InetAddress[] { InetAddress.getByAddress(new byte[] {127, 0, 0, 1}) };
                } else {
                    return super.resolve(host);
                }
            }

        };

        // Create a connection manager with custom configuration.
        final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(
                socketFactoryRegistry, PoolConcurrencyPolicy.STRICT, PoolReusePolicy.LIFO, TimeValue.ofMinutes(5),
                null, dnsResolver, null);

        // Create socket configuration
        final SocketConfig socketConfig = SocketConfig.custom()
            .setTcpNoDelay(true)
            .build();
        // Configure the connection manager to use socket configuration either
        // by default or for a specific host.
        connManager.setDefaultSocketConfig(socketConfig);
        // Validate connections after 1 sec of inactivity
        connManager.setValidateAfterInactivity(TimeValue.ofSeconds(10));

        // Configure total max or per route limits for persistent connections
        // that can be kept in the pool or leased by the connection manager.
        connManager.setMaxTotal(100);
        connManager.setDefaultMaxPerRoute(10);
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("somehost", 80)), 20);

        // Use custom cookie store if necessary.
        final CookieStore cookieStore = new BasicCookieStore();
        // Use custom credentials provider if necessary.
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        // Create global request configuration
        final RequestConfig defaultRequestConfig = RequestConfig.custom()
            .setCookieSpec(StandardCookieSpec.STRICT)
            .setExpectContinueEnabled(true)
            .setTargetPreferredAuthSchemes(Arrays.asList(StandardAuthScheme.NTLM, StandardAuthScheme.DIGEST))
            .setProxyPreferredAuthSchemes(Arrays.asList(StandardAuthScheme.BASIC))
            .build();

        // Create an HttpClient with the given custom dependencies and configuration.

        try (final CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultCookieStore(cookieStore)
                .setDefaultCredentialsProvider(credentialsProvider)
                .setProxy(new HttpHost("myproxy", 8080))
                .setDefaultRequestConfig(defaultRequestConfig)
                .build()) {
            final HttpGet httpget = new HttpGet("http://httpbin.org/get");
            // Request configuration can be overridden at the request level.
            // They will take precedence over the one set at the client level.
            final RequestConfig requestConfig = RequestConfig.copy(defaultRequestConfig)
                    .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                    .setConnectTimeout(Timeout.ofSeconds(5))
                    .setProxy(new HttpHost("myotherproxy", 8080))
                    .build();
            httpget.setConfig(requestConfig);

            // Execution context can be customized locally.
            final HttpClientContext context = HttpClientContext.create();
            // Contextual attributes set the local context level will take
            // precedence over those set at the client level.
            context.setCookieStore(cookieStore);
            context.setCredentialsProvider(credentialsProvider);

            System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());
            try (final CloseableHttpResponse response = httpclient.execute(httpget, context)) {
                System.out.println("----------------------------------------");
                System.out.println(response.getCode() + " " + response.getReasonPhrase());
                System.out.println(EntityUtils.toString(response.getEntity()));

                // Once the request has been executed the local context can
                // be used to examine updated state and various objects affected
                // by the request execution.

                // Last executed request
                context.getRequest();
                // Execution route
                context.getHttpRoute();
                // Auth exchanges
                context.getAuthExchanges();
                // Cookie origin
                context.getCookieOrigin();
                // Cookie spec used
                context.getCookieSpec();
                // User security token
                context.getUserToken();

            }
        }
    }

}
 */

/*
public class ClientFormLogin {

    public static void main(final String[] args) throws Exception {
        final BasicCookieStore cookieStore = new BasicCookieStore();
        try (final CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .build()) {
            final HttpGet httpget = new HttpGet("https://someportal/");
            try (final CloseableHttpResponse response1 = httpclient.execute(httpget)) {
                final HttpEntity entity = response1.getEntity();

                System.out.println("Login form get: " + response1.getCode() + " " + response1.getReasonPhrase());
                EntityUtils.consume(entity);

                System.out.println("Initial set of cookies:");
                final List<Cookie> cookies = cookieStore.getCookies();
                if (cookies.isEmpty()) {
                    System.out.println("None");
                } else {
                    for (int i = 0; i < cookies.size(); i++) {
                        System.out.println("- " + cookies.get(i).toString());
                    }
                }
            }

            final ClassicHttpRequest login = ClassicRequestBuilder.post()
                    .setUri(new URI("https://someportal/"))
                    .addParameter("IDToken1", "username")
                    .addParameter("IDToken2", "password")
                    .build();
            try (final CloseableHttpResponse response2 = httpclient.execute(login)) {
                final HttpEntity entity = response2.getEntity();

                System.out.println("Login form get: " + response2.getCode() + " " + response2.getReasonPhrase());
                EntityUtils.consume(entity);

                System.out.println("Post logon cookies:");
                final List<Cookie> cookies = cookieStore.getCookies();
                if (cookies.isEmpty()) {
                    System.out.println("None");
                } else {
                    for (int i = 0; i < cookies.size(); i++) {
                        System.out.println("- " + cookies.get(i).toString());
                    }
                }
            }
        }
    }
}
 */

/*
public class ClientCustomSSL {

    public final static void main(final String[] args) throws Exception {
        // Trust standard CA and those trusted by our custom strategy
        final SSLContext sslcontext = SSLContexts.custom()
                .loadTrustMaterial(new TrustStrategy() {

                    @Override
                    public boolean isTrusted(
                            final X509Certificate[] chain,
                            final String authType) throws CertificateException {
                        final X509Certificate cert = chain[0];
                        return "CN=httpbin.org".equalsIgnoreCase(cert.getSubjectDN().getName());
                    }

                })
                .build();
        // Allow TLSv1.2 protocol only
        final SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslcontext)
                .setTlsVersions(TLS.V_1_2)
                .build();
        final HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build();
        try (CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                .build()) {

            final HttpGet httpget = new HttpGet("https://httpbin.org/");

            System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());

            final HttpClientContext clientContext = HttpClientContext.create();
            try (CloseableHttpResponse response = httpclient.execute(httpget, clientContext)) {
                System.out.println("----------------------------------------");
                System.out.println(response.getCode() + " " + response.getReasonPhrase());
                System.out.println(EntityUtils.toString(response.getEntity()));

                final SSLSession sslSession = clientContext.getSSLSession();
                if (sslSession != null) {
                    System.out.println("SSL protocol " + sslSession.getProtocol());
                    System.out.println("SSL cipher suite " + sslSession.getCipherSuite());
                }
            }
        }
    }

}
 */

/*
public class ClientExecuteProxy {

    public static void main(final String[] args)throws Exception {
        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            final HttpHost target = new HttpHost("https", "httpbin.org", 443);
            final HttpHost proxy = new HttpHost("http", "127.0.0.1", 8080);

            final RequestConfig config = RequestConfig.custom()
                    .setProxy(proxy)
                    .build();
            final HttpGet request = new HttpGet("/get");
            request.setConfig(config);

            System.out.println("Executing request " + request.getMethod() + " " + request.getUri() +
                    " via " + proxy);

            try (final CloseableHttpResponse response = httpclient.execute(target, request)) {
                System.out.println("----------------------------------------");
                System.out.println(response.getCode() + " " + response.getReasonPhrase());
                System.out.println(EntityUtils.toString(response.getEntity()));
            }
        }
    }

}
 */

/*
public class ClientMultiThreadedExecution {

    public static void main(final String[] args) throws Exception {
        // Create an HttpClient with the PoolingHttpClientConnectionManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(100);

        try (final CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                .build()) {
            // create an array of URIs to perform GETs on
            final String[] urisToGet = {
                    "http://hc.apache.org/",
                    "http://hc.apache.org/httpcomponents-core-ga/",
                    "http://hc.apache.org/httpcomponents-client-ga/",
            };

            // create a thread for each URI
            final GetThread[] threads = new GetThread[urisToGet.length];
            for (int i = 0; i < threads.length; i++) {
                final HttpGet httpget = new HttpGet(urisToGet[i]);
                threads[i] = new GetThread(httpclient, httpget, i + 1);
            }

            // start the threads
            for (final GetThread thread : threads) {
                thread.start();
            }

            // join the threads
            for (final GetThread thread : threads) {
                thread.join();
            }

        }
    }

    static class GetThread extends Thread {

        private final CloseableHttpClient httpClient;
        private final HttpContext context;
        private final HttpGet httpget;
        private final int id;

        public GetThread(final CloseableHttpClient httpClient, final HttpGet httpget, final int id) {
            this.httpClient = httpClient;
            this.context = new BasicHttpContext();
            this.httpget = httpget;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                System.out.println(id + " - about to get something from " + httpget.getUri());
                try (CloseableHttpResponse response = httpClient.execute(httpget, context)) {
                    System.out.println(id + " - get executed");
                    // get the response body as an array of bytes
                    final HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        final byte[] bytes = EntityUtils.toByteArray(entity);
                        System.out.println(id + " - " + bytes.length + " bytes read");
                    }
                }
            } catch (final Exception e) {
                System.out.println(id + " - error: " + e);
            }
        }

    }

}
 */

/*
public class ClientInterceptors {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpClient httpclient = HttpClients.custom()

                // Add a simple request ID to each outgoing request

                .addRequestInterceptorFirst(new HttpRequestInterceptor() {

                    private final AtomicLong count = new AtomicLong(0);

                    @Override
                    public void process(
                            final HttpRequest request,
                            final EntityDetails entity,
                            final HttpContext context) throws HttpException, IOException {
                        request.setHeader("request-id", Long.toString(count.incrementAndGet()));
                    }
                })

                // Simulate a 404 response for some requests without passing the message down to the backend

                .addExecInterceptorAfter(ChainElement.PROTOCOL.name(), "custom", new ExecChainHandler() {

                    @Override
                    public ClassicHttpResponse execute(
                            final ClassicHttpRequest request,
                            final ExecChain.Scope scope,
                            final ExecChain chain) throws IOException, HttpException {

                        final Header idHeader = request.getFirstHeader("request-id");
                        if (idHeader != null && "13".equalsIgnoreCase(idHeader.getValue())) {
                            final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NOT_FOUND, "Oppsie");
                            response.setEntity(new StringEntity("bad luck", ContentType.TEXT_PLAIN));
                            return response;
                        } else {
                            return chain.proceed(request, scope);
                        }
                    }

                })
                .build()) {

            for (int i = 0; i < 20; i++) {
                final HttpGet httpget = new HttpGet("http://httpbin.org/get");

                System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());

                try (final CloseableHttpResponse response = httpclient.execute(httpget)) {
                    System.out.println("----------------------------------------");
                    System.out.println(response.getCode() + " " + response.getReasonPhrase());
                    System.out.println(EntityUtils.toString(response.getEntity()));
                }
            }
        }
    }

}

 */

/*
public class ClientMultipartFormPost {

    public static void main(final String[] args) throws Exception {
        if (args.length != 1)  {
            System.out.println("File path not given");
            System.exit(1);
        }
        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            final HttpPost httppost = new HttpPost("http://localhost:8080" +
                    "/servlets-examples/servlet/RequestInfoExample");

            final FileBody bin = new FileBody(new File(args[0]));
            final StringBody comment = new StringBody("A binary file of some kind", ContentType.TEXT_PLAIN);

            final HttpEntity reqEntity = MultipartEntityBuilder.create()
                    .addPart("bin", bin)
                    .addPart("comment", comment)
                    .build();


            httppost.setEntity(reqEntity);

            System.out.println("executing request " + httppost);
            try (final CloseableHttpResponse response = httpclient.execute(httppost)) {
                System.out.println("----------------------------------------");
                System.out.println(response);
                final HttpEntity resEntity = response.getEntity();
                if (resEntity != null) {
                    System.out.println("Response content length: " + resEntity.getContentLength());
                }
                EntityUtils.consume(resEntity);
            }
        }
    }

}
 */

/*
private static void test1() throws IOException, URISyntaxException, ParseException {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()){
        final HttpPost httppost = new HttpPost("https://search.jd.com/image?op=upload");
        // final HttpPost httppost = new HttpPost("http://localhost:8888/test");

        httppost.addHeader("cookie", "__jXLEMQAG6LTE");
        httppost.addHeader("referer", "https://search.jd.com/image");
        httppost.addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Safari/537.36");

        final File file = new File("C:\\Users\\houyu\\Desktop\\1.jpg");
        FileBody bin = new FileBody(file, ContentType.IMAGE_JPEG);

        HttpEntity reqEntity = MultipartEntityBuilder.create()
                // 相当于<input type="file" name="file"/>
                .addPart("file", bin)
                .build();

        httppost.setEntity(reqEntity);

        System.out.println("Executing request " + httppost.getMethod() + " " + httppost.getUri());
        try (final CloseableHttpResponse response = httpClient.execute(httppost)) {
            System.out.println("----------------------------------------");
            System.out.println(response.getCode() + " " + response.getReasonPhrase());
            System.out.println(EntityUtils.toString(response.getEntity()));
        }

    }
}
 */


