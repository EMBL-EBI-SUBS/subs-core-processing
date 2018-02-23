package uk.ac.ebi.subs.sheetloader;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Component
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    @Value("${aap.domains.url}/auth")
    private String aapUri;

    @Value("${usi.tokenservice.username}")
    private String username;

    @Value("${usi.tokenservice.password}")
    private String password;
    private Logger log = LoggerFactory.getLogger(getClass());

    private final RestOperations restOperations = new RestTemplate();

    private Optional<String> jwt = Optional.empty();
    private Optional<Date> expiry = Optional.empty();

    public synchronized String aapToken() {
        log.debug("JWT requested");
        if (username == null || username.trim().length() == 0
                || password == null || password.trim().length() == 0) {
            return null;
        }

        if (isFreshTokenRequired()) {
            log.debug("Fetching fresh JWT");

            String auth = username + ":" + password;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(Charset.forName("US-ASCII")) );
            String authHeader = "Basic " + new String( encodedAuth );

            RequestEntity<?> request = RequestEntity.get(URI.create(aapUri))
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .build();

            ResponseEntity<String> response = restOperations.exchange(request, String.class);

            jwt = Optional.of(response.getBody());

            try {
                DecodedJWT decodedJwt = JWT.decode(jwt.get());
                Optional<Date> tokenExpiry = Optional.of(decodedJwt.getExpiresAt());

                expiry = shortenTokenLifetime(tokenExpiry);

            } catch (JWTDecodeException e){
                //Invalid token
                throw new RuntimeException(e);
            }

            log.debug("Fresh JWT obtained, expires {}, {}",expiry,jwt);
        }

        return jwt.get();
    }

    private Optional<Date> shortenTokenLifetime(Optional<Date> tokenExpiry) {
        if (!tokenExpiry.isPresent()){
            return tokenExpiry;
        }

        Date expiryTime = tokenExpiry.get();
        Date fiveMinutesEarlier = new Date( expiryTime.getTime() - FIVE_MINS_IN_MILLIS);

        return Optional.of(fiveMinutesEarlier);
    }

    private static final long FIVE_MINS_IN_MILLIS = 5 * 60 * 1000;


    private boolean isFreshTokenRequired() {
        Date now = new Date();
        return !jwt.isPresent() || (expiry.isPresent() && expiry.get().before(now));
    }
}

