package com.artisan.developer.monitor.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class WebUtils {

    private static final String DEFAULT_CHARSET = StandardCharsets.UTF_8.name();
    public static final String CONTENT_TYPE_JSON = "application/json;charset=utf-8";
    private static boolean ignoreSSLCheck = true; // 忽略SSL检查
    private static boolean ignoreHostCheck = true; // 忽略HOST检查

    public static class TrustAllTrustManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }
    }

    private WebUtils() {
    }

    public static HttpResponseData doPost(String url, String content, int connectTimeout, int readTimeout) throws IOException {
        return _doPost(url, content.getBytes(DEFAULT_CHARSET), connectTimeout, readTimeout, null);
    }

    private static HttpResponseData _doPost(String url, byte[] content, int connectTimeout, int readTimeout, Map<String, String> headerMap) throws IOException {
        HttpURLConnection conn = null;
        OutputStream out = null;
        String rsp = null;
        HttpResponseData data = new HttpResponseData();
        try {
            conn = getConnection(new URL(url), "POST", CONTENT_TYPE_JSON, headerMap);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            out = conn.getOutputStream();
            out.write(content);
            rsp = getResponseAsString(conn);
            data.setBody(rsp);
            Map<String, List<String>> headers = conn.getHeaderFields();
            data.setHeaders(headers);
        } finally {
            if (out != null) {
                out.close();
            }
            if (conn != null) {
                conn.disconnect();
            }
        }

        return data;
    }

    private static HttpURLConnection getConnection(URL url, String method, String ctype, Map<String, String> headerMap) throws IOException {
        HttpURLConnection conn = null;
        conn = (HttpURLConnection) url.openConnection();

        if (conn instanceof HttpsURLConnection) {
            HttpsURLConnection connHttps = (HttpsURLConnection) conn;
            if (ignoreSSLCheck) {
                try {
                    SSLContext ctx = SSLContext.getInstance("TLS");
                    ctx.init(null, new TrustManager[]{new TrustAllTrustManager()}, new SecureRandom());
                    connHttps.setSSLSocketFactory(ctx.getSocketFactory());
                    connHttps.setHostnameVerifier(new HostnameVerifier() {
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    });
                } catch (Exception e) {
                    throw new IOException(e.toString());
                }
            } else {
                if (ignoreHostCheck) {
                    connHttps.setHostnameVerifier(new HostnameVerifier() {
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    });
                }
            }
            conn = connHttps;
        }

        conn.setRequestMethod(method);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setRequestProperty("Host", url.getHost());

        conn.setRequestProperty("Accept", "text/xml,text/javascript");
        conn.setRequestProperty("Content-Type", ctype);
        if (headerMap != null) {
            for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return conn;
    }

    protected static String getResponseAsString(HttpURLConnection conn) throws IOException {
        String charset = DEFAULT_CHARSET;
        if (conn.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
            return getStreamAsString(conn.getInputStream(), charset);
        } else {
            // OAuth bad request always return 400 status
            if (conn.getResponseCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
                InputStream error = conn.getErrorStream();
                if (error != null) {
                    return getStreamAsString(error, charset);
                }
            }
            // Client Error 4xx and Server Error 5xx
            throw new IOException(conn.getResponseCode() + " " + conn.getResponseMessage());
        }
    }

    public static String getStreamAsString(InputStream stream, String charset) throws IOException {
        try {
            Reader reader = new InputStreamReader(stream, charset);
            StringBuilder response = new StringBuilder();

            final char[] buff = new char[1024];
            int read = 0;
            while ((read = reader.read(buff)) > 0) {
                response.append(buff, 0, read);
            }

            return response.toString();
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
