package uk.ac.ebi.subs.processing.dispatcher;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "usi.archive.dispatcher.routingKey")
class DispatcherRoutingKeyProperties {

    private String bioSamples;
    private String bioStudies;
    private String ena;
    private String arrayExpress;
    private String metabolights;
}