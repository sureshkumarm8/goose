/**
 * Licensed to Gravity.com under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  Gravity.com licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package me.angrybyte.goose.network;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpVersion;
import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.params.CookiePolicy;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.conn.ClientConnectionManager;
import cz.msebera.android.httpclient.conn.params.ConnManagerParams;
import cz.msebera.android.httpclient.conn.scheme.PlainSocketFactory;
import cz.msebera.android.httpclient.conn.scheme.Scheme;
import cz.msebera.android.httpclient.conn.scheme.SchemeRegistry;
import cz.msebera.android.httpclient.conn.ssl.SSLSocketFactory;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;
import cz.msebera.android.httpclient.impl.conn.tsccm.ThreadSafeClientConnManager;
import cz.msebera.android.httpclient.params.BasicHttpParams;
import cz.msebera.android.httpclient.params.HttpConnectionParams;
import cz.msebera.android.httpclient.params.HttpParams;
import cz.msebera.android.httpclient.params.HttpProtocolParams;
import cz.msebera.android.httpclient.protocol.BasicHttpContext;
import cz.msebera.android.httpclient.protocol.HttpContext;
import cz.msebera.android.httpclient.util.EntityUtils;

/**
 * Downloads an HTML object, nothing special.
 */
@SuppressWarnings("deprecation")
public class HtmlFetcher {

    /**
     * Holds a reference to our override cookie store, we don't want to store cookies for head requests, only slows shit down
     */
    public static CookieStore emptyCookieStore;

    /**
     * Holds the HttpClient object for making requests
     */
    private static HttpClient httpClient;

    static {
        initClient();
    }

    public static HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Makes an http fetch to go retrieve the HTML from a url, store it to disk and pass it off
     *
     * @throws MaxBytesException
     * @throws NotHtmlException
     */
    public static String getHtml(String url) throws MaxBytesException, NotHtmlException {
        HttpGet httpget = null;
        String htmlResult = null;
        InputStream inStream = null;
        HttpEntity entity;
        try {
            HttpContext localContext = new BasicHttpContext();
            localContext.setAttribute(HttpClientContext.COOKIE_STORE, HtmlFetcher.emptyCookieStore);
            httpget = new HttpGet(url);

            HttpResponse response = httpClient.execute(httpget, localContext);

            entity = response.getEntity();

            if (entity != null) {
                inStream = entity.getContent();

                // set the encoding type if utf-8 or otherwise
                String encodingType = "UTF-8";
                try {

                    // encoding detection could be improved
                    encodingType = EntityUtils.getContentCharSet(entity);

                    if (encodingType == null) {
                        encodingType = "UTF-8";
                    }
                } catch (Exception ignored) {
                }

                try {
                    String resultRaw = HtmlFetcher.convertStreamToString(inStream, 15728640, encodingType);
                    if (resultRaw != null) {
                        htmlResult = resultRaw.trim();
                    }
                } finally {
                    entity.consumeContent();
                }
            }
        } catch (MaxBytesException e) {
            throw e;
        } catch (NullPointerException | SocketException | SocketTimeoutException ignored) {
        } catch (Exception e) {
            return null;
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (Exception ignored) {
                }
            }
            if (httpget != null) {
                try {
                    httpget.abort();
                } catch (Exception ignored) {
                }
            }

        }

        if (htmlResult == null || htmlResult.length() < 1) {
            throw new NotHtmlException();
        }

        InputStream is;
        String mimeType;
        try {
            is = new ByteArrayInputStream(htmlResult.getBytes("UTF-8"));

            mimeType = URLConnection.guessContentTypeFromStream(is);

            if (mimeType != null) {

                if (mimeType.equals("text/html") || mimeType.equals("application/xml")) {
                    return htmlResult;
                } else {
                    if (htmlResult.contains("<title>") && htmlResult.contains("<p>")) {
                        return htmlResult;
                    }
                    throw new NotHtmlException();
                }

            } else {
                throw new NotHtmlException();
            }

        } catch (IOException ignored) {
        }

        return htmlResult;
    }

    private static void initClient() {
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, 10 * 1000);
        HttpConnectionParams.setSoTimeout(httpParams, 10 * 1000);
        ConnManagerParams.setMaxTotalConnections(httpParams, 20000);

        HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);

        /**
         * We don't want anything to do with cookies at this time
         */
        emptyCookieStore = new CookieStore() {
            public void addCookie(Cookie cookie) {
            }

            ArrayList<Cookie> emptyList = new ArrayList<>();

            public List<Cookie> getCookies() {
                return emptyList;
            }

            @Override
            public boolean clearExpired(final Date date) {
                return false;
            }

            public void clear() {
            }
        };

        // set request params
        httpParams.setParameter("http.protocol.cookie-policy", CookiePolicy.BROWSER_COMPATIBILITY);
        httpParams.setParameter("http.User-Agent",
                "Mozilla/5.0 (X11; U; Linux x86_64; de; rv:1.9.2.8) Gecko/20100723 Ubuntu/10.04 (lucid) Firefox/3.6.8");
        httpParams.setParameter("http.language.Accept-Language", "en-us");
        httpParams.setParameter("http.protocol.content-charset", "UTF-8");
        httpParams.setParameter("Accept", "application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
        httpParams.setParameter("Cache-Control", "max-age=0");
        httpParams.setParameter("http.connection.stalecheck", false); // turn off stale check checking for performance reasons

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

        final ClientConnectionManager cm = new ThreadSafeClientConnManager(httpParams, schemeRegistry);

        httpClient = new DefaultHttpClient(cm, httpParams);

        httpClient.getParams().setParameter("http.conn-manager.timeout", 120000L);
        httpClient.getParams().setParameter("http.protocol.wait-for-continue", 10000L);
        httpClient.getParams().setParameter("http.tcp.nodelay", true);
    }

    /**
     * reads bytes off the string and returns a string
     *
     * @param maxBytes The max bytes that we want to read from the input stream
     * @return String
     */
    public static String convertStreamToString(InputStream is, int maxBytes, String encodingType) throws MaxBytesException {

        char[] buf = new char[2048];
        Reader r = null;
        try {
            r = new InputStreamReader(is, encodingType);
            StringBuilder s = new StringBuilder();
            int bytesRead = 2048;
            while (true) {

                if (bytesRead >= maxBytes) {
                    throw new MaxBytesException();
                }

                int n = r.read(buf);
                bytesRead += 2048;
                if (n < 0)
                    break;
                s.append(buf, 0, n);
            }

            return s.toString();
        } catch (IOException ignored) {
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                }
            }
        }
        return null;

    }
}
