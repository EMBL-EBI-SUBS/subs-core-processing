package uk.ac.ebi.subs.sheetmapper;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.ac.ebi.subs.messaging.Queues;

@Configuration
public class SheetMapperQueueConfig {

    public final static String SHEET_SUBMITTED_QUEUE = "usi-sheet-submitted-load-contents";
    private final String SHEET_SUBMITTED_ROUTING_KEY = "usi.sheet.submitted";

    /**
     * Queue for cleaning up contents of a submission if the user deletes it
     */
    @Bean
    Queue onSubmitParseSheetQueue(){return Queues.buildQueueWithDlx(SHEET_SUBMITTED_QUEUE);}

    @Bean
    Binding onSubmitParseSheetBinding(Queue onSubmitParseSheetQueue, TopicExchange submissionExchange) {
        return BindingBuilder.bind(onSubmitParseSheetQueue).to(submissionExchange).with(SHEET_SUBMITTED_ROUTING_KEY);
    }
}
