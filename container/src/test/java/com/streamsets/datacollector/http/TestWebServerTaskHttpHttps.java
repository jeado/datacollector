/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.datacollector.http;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableSet;
import com.streamsets.datacollector.main.RuntimeInfo;
import com.streamsets.datacollector.main.RuntimeModule;
import com.streamsets.datacollector.util.Configuration;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TestWebServerTaskHttpHttps {

  private static class PingServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.getWriter().write("ping");
    }
  }

  @SuppressWarnings("unchecked")
  private WebServerTask createWebServerTask(
      final String confDir,
      final Configuration conf,
      final Set<WebAppProvider> webAppProviders
  ) throws Exception {
    RuntimeInfo ri = new RuntimeInfo(
        RuntimeModule.SDC_PROPERTY_PREFIX,
        new MetricRegistry(),
        Collections.<ClassLoader>emptyList()
    ) {
      @Override
      public String getConfigDir() {
        return confDir;
      }
    };
    Set<ContextConfigurator> configurators = new HashSet<>();
    configurators.add(new ContextConfigurator() {
      @Override
      public void init(ServletContextHandler context) {
        context.addServlet(new ServletHolder(new PingServlet()), "/ping");
      }
    });
    return new WebServerTask(ri, conf, configurators, webAppProviders);
  }

  @SuppressWarnings("unchecked")
  private WebServerTask createWebServerTask(final String confDir, final Configuration conf) throws Exception {
    return createWebServerTask(confDir, conf, Collections.<WebAppProvider>emptySet());
  }

  private String createTestDir() {
    File dir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(dir.mkdirs());
    return dir.getAbsolutePath();
  }

  private int getRandomPort() throws Exception {
    ServerSocket ss = new ServerSocket(0);
    int port = ss.getLocalPort();
    ss.close();
    return port;
  }

  @Test
  public void testInvalidPorts() throws Exception {
    Configuration conf = new Configuration();
    conf.set(WebServerTask.AUTHENTICATION_KEY, "none");
    conf.set(WebServerTask.HTTP_PORT_KEY, 0);
    conf.set(WebServerTask.HTTPS_PORT_KEY, 0);
    WebServerTask ws = createWebServerTask(createTestDir(), conf);
    try {
      ws.initTask();
    } catch (IllegalArgumentException iae) {
      //
    }
    conf.set(WebServerTask.HTTP_PORT_KEY, 0);
    conf.set(WebServerTask.HTTPS_PORT_KEY, 10000);
    ws = createWebServerTask(createTestDir(), conf);
    try {
      ws.initTask();
    } catch (IllegalArgumentException iae) {
      //
    }

    conf.set(WebServerTask.HTTP_PORT_KEY, 10000);
    conf.set(WebServerTask.HTTPS_PORT_KEY, 0);
    try {
      ws.initTask();
    } catch (IllegalArgumentException iae) {
      //
    }
  }

  @Test
  public void testGetServerURI() throws Exception {
    Configuration conf = new Configuration();
    int httpPort = getRandomPort();
    conf.set(WebServerTask.AUTHENTICATION_KEY, "none");
    conf.set(WebServerTask.HTTP_PORT_KEY, httpPort);
    final WebServerTask ws = createWebServerTask(createTestDir(), conf);
    ws.initTask();
    // Server hasn't yet started
    try {
      ws.getServerURI();
      Assert.fail("Expected ServerNotYetRunningException but didn't get any");
    } catch (ServerNotYetRunningException se) {
      // Expected
    } catch (Exception e) {
      Assert.fail("Expected ServerNotYetRunningException but got " + e);
    }
    // Now start the server
    try {
      new Thread() {
        @Override
        public void run() {
          ws.runTask();
        }
      }.start();
      Thread.sleep(1000);
      Assert.assertEquals(httpPort, ws.getServerURI().getPort());
    } finally {
      ws.stopTask();
    }
  }

  @Test
  public void testHttp() throws Exception {
    Configuration conf = new Configuration();
    int httpPort = getRandomPort();
    conf.set(WebServerTask.AUTHENTICATION_KEY, "none");
    conf.set(WebServerTask.HTTP_PORT_KEY, httpPort);
    final WebServerTask ws = createWebServerTask(createTestDir(), conf);
    try {
      ws.initTask();
      new Thread() {
        @Override
        public void run() {
          ws.runTask();
        }
      }.start();
      Thread.sleep(1000);

      HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + httpPort  + "/ping").openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
    } finally {
      ws.stopTask();
    }
  }

  @Test
  public void testHttpRandomPort() throws Exception {
    Configuration conf = new Configuration();
    conf.set(WebServerTask.AUTHENTICATION_KEY, "none");
    conf.set(WebServerTask.HTTP_PORT_KEY, 0);
    final WebServerTask ws = createWebServerTask(createTestDir(), conf);
    try {
      ws.initTask();
      new Thread() {
        @Override
        public void run() {
          ws.runTask();
        }
      }.start();
      Thread.sleep(1000);

      HttpURLConnection conn =
          (HttpURLConnection) new URL("http://127.0.0.1:" + ws.getServerURI().getPort() + "/ping")
              .openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
    } finally {
      ws.stopTask();
    }
  }

  //making the url connection to trust a self signed cert on localhost
  private void configureHttpsUrlConnection(HttpsURLConnection conn) throws Exception {
    SSLContext sc = SSLContext.getInstance("SSL");
    TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
          public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }
          public void checkClientTrusted(
              java.security.cert.X509Certificate[] certs, String authType) {
          }
          public void checkServerTrusted(
              java.security.cert.X509Certificate[] certs, String authType) {
          }
        }
    };
    sc.init(null, trustAllCerts, new java.security.SecureRandom());
    conn.setSSLSocketFactory(sc.getSocketFactory());
    conn.setHostnameVerifier(new HostnameVerifier() {
      @Override
      public boolean verify(String s, SSLSession sslSession) {
        return true;
      }
    });
  }

  @Test
  public void testHttps() throws Exception {
    Configuration conf = new Configuration();
    int httpsPort = getRandomPort();
    String confDir = createTestDir();
    String keyStore = new File(confDir, "sdc-keystore.jks").getAbsolutePath();
    OutputStream os = new FileOutputStream(keyStore);
    IOUtils.copy(getClass().getClassLoader().getResourceAsStream("sdc-keystore.jks"),os);
    os.close();
    conf.set(WebServerTask.AUTHENTICATION_KEY, "none");
    conf.set(WebServerTask.HTTP_PORT_KEY, -1);
    conf.set(WebServerTask.HTTPS_PORT_KEY, httpsPort);
    conf.set(WebServerTask.HTTPS_KEYSTORE_PATH_KEY, "sdc-keystore.jks");
    conf.set(WebServerTask.HTTPS_KEYSTORE_PASSWORD_KEY, "password");
    final WebServerTask ws = createWebServerTask(confDir, conf);
    try {
      ws.initTask();
      new Thread() {
        @Override
        public void run() {
          ws.runTask();
        }
      }.start();
      Thread.sleep(1000);
      HttpsURLConnection conn = (HttpsURLConnection) new URL("https://127.0.0.1:" + httpsPort + "/ping")
          .openConnection();
      configureHttpsUrlConnection(conn);
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
    } finally {
      ws.stopTask();
    }
  }

  @Test
  public void testHttpsRandomPort() throws Exception {
    Configuration conf = new Configuration();
    String confDir = createTestDir();
    String keyStore = new File(confDir, "sdc-keystore.jks").getAbsolutePath();
    OutputStream os = new FileOutputStream(keyStore);
    IOUtils.copy(getClass().getClassLoader().getResourceAsStream("sdc-keystore.jks"), os);
    os.close();
    conf.set(WebServerTask.AUTHENTICATION_KEY, "none");
    conf.set(WebServerTask.HTTP_PORT_KEY, -1);
    conf.set(WebServerTask.HTTPS_PORT_KEY, 0);
    conf.set(WebServerTask.HTTPS_KEYSTORE_PATH_KEY, "sdc-keystore.jks");
    conf.set(WebServerTask.HTTPS_KEYSTORE_PASSWORD_KEY, "password");
    final WebServerTask ws = createWebServerTask(confDir, conf);
    try {
      ws.initTask();
      new Thread() {
        @Override
        public void run() {
          ws.runTask();
        }
      }.start();
      Thread.sleep(1000);

      HttpsURLConnection conn =
          (HttpsURLConnection) new URL("https://127.0.0.1:" + ws.getServerURI().getPort() + "/ping")
              .openConnection();
      configureHttpsUrlConnection(conn);
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
    } finally {
      ws.stopTask();
    }
  }

  @Test
  public void testHttpRedirectToHttpss() throws Exception {
    Configuration conf = new Configuration();
    int httpPort = getRandomPort();
    int httpsPort = getRandomPort();
    String confDir = createTestDir();
    String keyStore = new File(confDir, "sdc-keystore.jks").getAbsolutePath();
    OutputStream os = new FileOutputStream(keyStore);
    IOUtils.copy(getClass().getClassLoader().getResourceAsStream("sdc-keystore.jks"),os);
    os.close();
    conf.set(WebServerTask.AUTHENTICATION_KEY, "none");
    conf.set(WebServerTask.HTTP_PORT_KEY, httpPort);
    conf.set(WebServerTask.HTTPS_PORT_KEY, httpsPort);
    conf.set(WebServerTask.HTTPS_KEYSTORE_PATH_KEY, "sdc-keystore.jks");
    conf.set(WebServerTask.HTTPS_KEYSTORE_PASSWORD_KEY, "password");
    final WebServerTask ws = createWebServerTask(confDir, conf);
    try {
      ws.initTask();
      new Thread() {
        @Override
        public void run() {
          ws.runTask();
        }
      }.start();
      Thread.sleep(1000);
      HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + httpPort + "/ping").openConnection();
      conn.setInstanceFollowRedirects(false);
      Assert.assertTrue(conn.getResponseCode() >= 300 && conn.getResponseCode() < 400);
      Assert.assertTrue(conn.getHeaderField("Location").startsWith("https://"));
      HttpsURLConnection conns = (HttpsURLConnection) new URL(conn.getHeaderField("Location")).openConnection();
      configureHttpsUrlConnection(conns);
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conns.getResponseCode());
    } finally {
      ws.stopTask();
    }
  }

  @Test
  public void testWebApp() throws Exception {
    WebAppProvider webAppProvider = new WebAppProvider() {
      @Override
      public ServletContextHandler get() {
        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath("/webapp");
        handler.addServlet(new ServletHolder(new PingServlet()), "/ping");
        return handler;
      }
    };
    Configuration conf = new Configuration();
    int httpPort = getRandomPort();
    conf.set(WebServerTask.AUTHENTICATION_KEY, "none");
    conf.set(WebServerTask.HTTP_PORT_KEY, httpPort);
    final WebServerTask ws = createWebServerTask(createTestDir(), conf, ImmutableSet.of(webAppProvider));
    try {
      ws.initTask();
      new Thread() {
        @Override
        public void run() {
          ws.runTask();
        }
      }.start();
      Thread.sleep(1000);

      HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + httpPort + "/ping").openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
      conn = (HttpURLConnection) new URL("http://127.0.0.1:" + httpPort + "/webapp/ping").openConnection();
      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
    } finally {
      ws.stopTask();
    }
  }

}
