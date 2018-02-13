package uk.ac.ebi.subs.sheetmapper;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    private static final String TOKEN_CACHE_NAME = "aapJwtToken";
    private static final int TOKEN_LIFETIME_IN_SECONDS = 60 * 60;


    @Value("${aap.domains.url}/auth")
    private String authUrl;

    @Value("${usi.tokenservice.username}")
    private String username;

    @Value("${usi.tokenservice.password}")
    private String password;

    @Cacheable(TOKEN_CACHE_NAME)
    public String aapToken() {
        try {
            logger.info("requesting new aap token");
            String token = getJWTToken(
                    authUrl,
                    username,
                    password
            );
            logger.info("new token obtained");
            return token;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @CacheEvict(allEntries = true, cacheNames = TOKEN_CACHE_NAME)
    @Scheduled(fixedDelay = TOKEN_LIFETIME_IN_SECONDS - 60)
    public void cacheEvict() {
        //empty method clears the cache

    }

    public static String getJWTToken(String authUrl, String username, String password) throws IOException {
        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
        provider.setCredentials(AuthScope.ANY, credentials);

        HttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
        HttpResponse response = client.execute(new HttpGet(authUrl));

        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            logger.error(
                    "Could not get token. Status {} Body {}",
                    response.getStatusLine().getStatusCode(),
                    EntityUtils.toString(response.getEntity())
            );
            throw new CouldNotGetTokenException("ERROR: An error occurred when trying to obtain the AAP token.");
        }
        return EntityUtils.toString(response.getEntity());
    }
}

