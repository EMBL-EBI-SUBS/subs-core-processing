package uk.ac.ebi.subs.apisupport;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.ac.ebi.subs.messaging.Queues;

@Configuration
public class ApiSupportQueueConfig {

    /**
     * Queue for cleaning up contents of a submission if the user deletes it
     */
    @Bean
    Queue onDeleteCleanupSubmissionContentsQueue(){return new Queue(Queues.SUBMISSION_DELETED_CLEANUP_CONTENTS,true);}

    @Bean
    Binding onDeleteCleanupSubmissionContentsBinding(Queue onDeleteCleanupSubmissionContentsQueue, TopicExchange submissionExchange) {
        return BindingBuilder.bind(onDeleteCleanupSubmissionContentsQueue).to(submissionExchange).with(Queues.SUBMISSION_DELETED_ROUTING_KEY);
    }
}
