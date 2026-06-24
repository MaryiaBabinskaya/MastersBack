package com.krakow.theaters.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

@Slf4j
@Configuration
public class SSLConfig {

    @PostConstruct
    public void disableSSLVerification() {
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, buildTrustAllCerts(), new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            log.info("SSL verification disabled");
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("Failed to disable SSL verification", e);
        }
    }

    private TrustManager[] buildTrustAllCerts() {
        return new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };
    }
}
