package uk.ac.ebi.subs.sheetmapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;

@Component
public class AapAuthInterceptor implements ClientHttpRequestInterceptor {

    private final TokenService tokenService;

    public AapAuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

        if (tokenService!= null && !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            String token = tokenService.aapToken();
            Assert.notNull(token);


            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer "+token);
        }

        //pass along to the next interceptor
        return execution.execute(request, body);
    }

}
