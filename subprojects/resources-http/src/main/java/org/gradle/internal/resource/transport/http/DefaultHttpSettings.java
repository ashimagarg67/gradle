/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.resource.transport.http;


import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.gradle.authentication.Authentication;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.util.Collection;

public class DefaultHttpSettings implements HttpSettings {
    private final Collection<Authentication> authenticationSettings;
    private final SslContextFactory sslContextFactory;
    private final HostnameVerifier hostnameVerifier;
    private HttpProxySettings proxySettings;
    private HttpProxySettings secureProxySettings;
    private HttpTimeoutSettings timeoutSettings;

    public static DefaultHttpSettings allowUntrustedSslConnections(Collection<Authentication> authenticationSettings) {
        return new DefaultHttpSettings(authenticationSettings, new AllTrustingSslContextFactory(), ALL_TRUSTING_HOSTNAME_VERIFIER);
    }

    public DefaultHttpSettings(Collection<Authentication> authenticationSettings, SslContextFactory sslContextFactory) {
        this(authenticationSettings, sslContextFactory, new DefaultHostnameVerifier(null));
    }

    private DefaultHttpSettings(Collection<Authentication> authenticationSettings, SslContextFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
        if (authenticationSettings == null) {
            throw new IllegalArgumentException("Authentication settings cannot be null.");
        }

        this.authenticationSettings = authenticationSettings;
        this.sslContextFactory = sslContextFactory;
        this.hostnameVerifier = hostnameVerifier;
    }

    @Override
    public HttpProxySettings getProxySettings() {
        if (proxySettings == null) {
            proxySettings = new JavaSystemPropertiesHttpProxySettings();
        }
        return proxySettings;
    }

    @Override
    public HttpProxySettings getSecureProxySettings() {
        if (secureProxySettings == null) {
            secureProxySettings = new JavaSystemPropertiesSecureHttpProxySettings();
        }
        return secureProxySettings;
    }

    @Override
    public HttpTimeoutSettings getTimeoutSettings() {
        if (timeoutSettings == null) {
            timeoutSettings = new JavaSystemPropertiesHttpTimeoutSettings();
        }
        return timeoutSettings;
    }

    @Override
    public Collection<Authentication> getAuthenticationSettings() {
        return authenticationSettings;
    }

    @Override
    public SslContextFactory getSslContextFactory() {
        return sslContextFactory;
    }

    @Override
    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    private static final HostnameVerifier ALL_TRUSTING_HOSTNAME_VERIFIER = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };
}
