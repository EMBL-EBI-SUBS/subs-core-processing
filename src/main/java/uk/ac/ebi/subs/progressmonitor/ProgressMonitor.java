package uk.ac.ebi.subs.progressmonitor;

import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.repository.SubmissionService;

@Component
public class ProgressMonitor {

    private RabbitMessagingTemplate rabbitMessagingTemplate;

    private SubmissionService submissionService;

    @Autowired
    public ProgressMonitor(RabbitMessagingTemplate rabbitMessagingTemplate, SubmissionService submissionService) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.submissionService = submissionService;
    }


}
