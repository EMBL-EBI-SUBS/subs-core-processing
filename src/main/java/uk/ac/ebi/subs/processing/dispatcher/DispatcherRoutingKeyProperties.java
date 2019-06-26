package uk.ac.ebi.subs.processing.dispatcher;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "usi.archive.dispatcher")
class DispatcherRoutingKeyProperties {
    private Map<String, String> routingKey;
    private List<String> enabled;
}