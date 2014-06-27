/***************************************************************************
 * Copyright (c) 2014 VMware, Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***************************************************************************/
package com.vmware.bdd.plugin.ambari.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.annotations.VisibleForTesting;

public class AmbariManagerClientbuilder {
   public static final int DEFAULT_TCP_PORT = 8080;
   public static final long DEFAULT_CONNECTION_TIMEOUT = 0;
   public static final TimeUnit DEFAULT_CONNECTION_TIMEOUT_UNITS =
         TimeUnit.MILLISECONDS;
   public static final long DEFAULT_RECEIVE_TIMEOUT = 0;
   public static final TimeUnit DEFAULT_RECEIVE_TIMEOUT_UNITS =
         TimeUnit.MILLISECONDS;

   private URL baseUrl;
   private String hostname;
   private int port = DEFAULT_TCP_PORT;
   private boolean enableTLS = false;
   private boolean enableLogging = false;
   private String username;
   private String password;
   private long connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
   private TimeUnit connectionTimeoutUnits = DEFAULT_CONNECTION_TIMEOUT_UNITS;
   private long receiveTimeout = DEFAULT_RECEIVE_TIMEOUT;
   private TimeUnit receiveTimeoutUnits = DEFAULT_RECEIVE_TIMEOUT_UNITS;
   private boolean validateCerts = true;
   private boolean validateCn = true;

   public AmbariManagerClientbuilder withBaseURL(URL baseUrl) {
      this.baseUrl = baseUrl;
      return this;
   }

   public AmbariManagerClientbuilder withHost(String hostname) {
      this.hostname = hostname;
      return this;
   }

   public AmbariManagerClientbuilder withPort(int port) {
      this.port = port;
      return this;
   }

   public AmbariManagerClientbuilder enableTLS() {
      this.enableTLS = true;
      return this;
   }

   public AmbariManagerClientbuilder enableLogging() {
      this.enableLogging = true;
      return this;
   }

   public AmbariManagerClientbuilder withUsernamePassword(String username,
         String password) {
      this.username = username;
      this.password = password;
      return this;
   }

   public AmbariManagerClientbuilder withConnectionTimeout(
         long connectionTimeout, TimeUnit connectionTimeoutUnits) {
      this.connectionTimeout = connectionTimeout;
      this.connectionTimeoutUnits = connectionTimeoutUnits;
      return this;
   }

   public AmbariManagerClientbuilder withReceiveTimeout(long receiveTimeout,
         TimeUnit receiveTimeoutUnits) {
      this.receiveTimeout = receiveTimeout;
      this.receiveTimeoutUnits = receiveTimeoutUnits;
      return this;
   }

   public AmbariManagerClientbuilder disableTlsCertValidation() {
      this.validateCerts = false;
      return this;
   }

   public AmbariManagerClientbuilder disableTlsCnValidation() {
      this.validateCn = false;
      return this;
   }

   @VisibleForTesting
   String generateAddress() {
      final String apiRootPath = "api/";

      if (baseUrl != null) {
         // Short-circuit and use the base URL to generate the full URL
         return String.format("%s/%s", baseUrl.toExternalForm(), apiRootPath);
      }

      if (hostname == null) {
         throw new IllegalArgumentException("hostname or full url must be set");
      }

      if (port <= 0) {
         throw new IllegalArgumentException(String.format(
               "'%d' is not a valid port number", port));
      }
      String urlString =
            String.format("%s://%s:%d/%s", enableTLS ? "https" : "http",
                  hostname, port, apiRootPath);
      try {
         // Check the syntax of the generated URL string
         new URI(urlString);
      } catch (URISyntaxException e) {
         throw new IllegalArgumentException(String.format(
               "'%s' is not a valid hostname", hostname), e);
      }
      return urlString;
   }

   public ApiRootResource build() {
      return build(ApiRootResource.class);
   }

   protected <T> T build(Class<T> proxyType) {
      JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();

      String address = generateAddress();
      boolean isTlsEnabled = address.startsWith("https://");
      bean.setAddress(address);
      if (username != null) {
         bean.setUsername(username);
         bean.setPassword(password);
      }
      if (enableLogging) {
         bean.setFeatures(Arrays.<AbstractFeature> asList(new LoggingFeature()));
      }

      Map<String, String> headers = new HashMap<String, String>();
      headers.put("X-Requested-By", "ambari");
      bean.setHeaders(headers);
      bean.setInheritHeaders(true);

      bean.setResourceClass(proxyType);
      bean.setProvider(new JacksonJsonProvider(new ApiObjectMapper()));

      T rootResource = bean.create(proxyType);
      ClientConfiguration config = WebClient.getConfig(rootResource);
      HTTPConduit conduit = (HTTPConduit) config.getConduit();
      if (isTlsEnabled) {
         TLSClientParameters tlsParams = new TLSClientParameters();
         if (!validateCerts) {
            tlsParams
                  .setTrustManagers(new TrustManager[] { new AcceptAllTrustManager() });
         }
         tlsParams.setDisableCNCheck(!validateCn);
         conduit.setTlsClientParameters(tlsParams);
      }

      HTTPClientPolicy policy = conduit.getClient();
      policy.setConnectionTimeout(connectionTimeoutUnits
            .toMillis(connectionTimeout));
      policy.setReceiveTimeout(receiveTimeoutUnits.toMillis(receiveTimeout));
      return rootResource;
   }

   /**
    * Closes the transport level conduit in the client. Reopening a new
    * connection, requires creating a new client object using the build() method
    * in this builder.
    *
    * @param root
    *           The resource returned by the build() method of this builder
    *           class
    */
   public static void closeClient(ApiRootResource root) {
      ClientConfiguration config = WebClient.getConfig(root);
      HTTPConduit conduit = config.getHttpConduit();
      if (conduit == null) {
         throw new IllegalArgumentException(
               "Client is not using the HTTP transport");
      }
      conduit.close();
   }

   /** A trust manager that will accept all certificates. */
   private static class AcceptAllTrustManager implements X509TrustManager {

      public void checkClientTrusted(X509Certificate[] chain, String authType) {
         // no op.
      }

      public void checkServerTrusted(X509Certificate[] chain, String authType) {
         // no op.
      }

      public X509Certificate[] getAcceptedIssuers() {
         return null;
      }

   }
}
