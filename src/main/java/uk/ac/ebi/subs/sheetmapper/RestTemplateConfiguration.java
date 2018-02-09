package uk.ac.ebi.subs.sheetmapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@Configuration
public class RestTemplateConfiguration {

    private final AapAuthInterceptor aapAuthInterceptor;

    public RestTemplateConfiguration(AapAuthInterceptor aapAuthInterceptor) {
        this.aapAuthInterceptor = aapAuthInterceptor;
    }

    @Bean
    public RestTemplate restTemplate(){

            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setInterceptors(Arrays.asList(aapAuthInterceptor));
            return restTemplate;

    }
}
