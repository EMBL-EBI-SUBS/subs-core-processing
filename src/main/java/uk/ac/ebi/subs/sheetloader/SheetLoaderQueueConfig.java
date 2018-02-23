package uk.ac.ebi.subs.sheetloader;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.ac.ebi.subs.messaging.Queues;

@Configuration
public class SheetLoaderQueueConfig {

    public final static String SHEET_SUBMITTED_QUEUE = "usi-sheet-submitted-load-contents";
    private final String SHEET_SUBMITTED_ROUTING_KEY = "usi.sheet.submitted";

    /**
     * Queue for cleaning up contents of a submission if the user deletes it
     */
    @Bean
    Queue onSubmitLoadSheetQueue(){return Queues.buildQueueWithDlx(SHEET_SUBMITTED_QUEUE);}

    @Bean
    Binding onSubmitLoadSheetBinding(Queue onSubmitLoadSheetQueue, TopicExchange submissionExchange) {
        return BindingBuilder.bind(onSubmitLoadSheetQueue).to(submissionExchange).with(SHEET_SUBMITTED_ROUTING_KEY);
    }
}
