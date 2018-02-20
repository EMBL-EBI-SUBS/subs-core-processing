package uk.ac.ebi.subs.batchloader;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.ac.ebi.subs.messaging.Queues;

@Configuration
public class BatchLoaderQueueConfig {

    public final static String BATCH_SUBMITTED_QUEUE = "usi-submittablesBatch-submitted-load-contents";
    private final String BATCH_SUBMITTED_ROUTING_KEY = "usi.submittablesBatch.submitted";

    /**
     * Queue for cleaning up contents of a submission if the user deletes it
     */
    @Bean
    Queue onSubmitLoadBatchQueue(){return Queues.buildQueueWithDlx(BATCH_SUBMITTED_QUEUE);}

    @Bean
    Binding onSubmitLoadBatchBinding(Queue onSubmitLoadBatchQueue, TopicExchange submissionExchange) {
        return BindingBuilder.bind(onSubmitLoadBatchQueue).to(submissionExchange).with(BATCH_SUBMITTED_ROUTING_KEY);
    }
}
