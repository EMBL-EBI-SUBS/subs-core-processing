package uk.ac.ebi.subs.processing.accession;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.ac.ebi.subs.messaging.Queues;

@Configuration
public class AccessionQueueConfig {

    public static final String USI_ARCHIVE_ACCESSIONIDS_PUBLISHED_ROUTING_KEY = "usi.archiveaccessionids.published";

    /**
     * The message queue name for receiving the accession IDs from the varios agents
     */
    static final String USI_ACCESSIONIDS_CONSUMER = "usi-accessionids-consumer";
    /**
     * Routing key for a message coming from an agent that produced some accessionIDs
     */
    private static final String EVENT_SUBMISSION_AGENT_RESULTS_ROUTING_KEY = "usi.agentresults.produced";

    /**
     * Queue for get accession IDs from the various archive agents
     * @return a Queue instance for the accession queue
     */
    @Bean
    Queue accessionQueue() {
        return Queues.buildQueueWithDlx(USI_ACCESSIONIDS_CONSUMER);
    }

    @Bean
    Binding accessionConsumingBinding(Queue accessionQueue, TopicExchange submissionExchange) {
        return BindingBuilder.bind(accessionQueue).to(submissionExchange).with(EVENT_SUBMISSION_AGENT_RESULTS_ROUTING_KEY);
    }
}
