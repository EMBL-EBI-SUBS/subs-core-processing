package uk.ac.ebi.subs.progressmonitor;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.submittable.Submission;
import uk.ac.ebi.subs.messaging.Channels;
import uk.ac.ebi.subs.repository.SubmissionService;

import java.util.List;

@Component
public class ProgressMonitor {

    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private SubmissionService submissionService;

    private boolean samplesStatus;
    private boolean enaStatus;
    private boolean aeStatus;

    private List<Submission> submissions;

    @Autowired
    public ProgressMonitor(RabbitMessagingTemplate rabbitMessagingTemplate, SubmissionService submissionService) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.submissionService = submissionService;
    }

    @RabbitListener(queues = Channels.SUBMISSION_PROCESSED)
    public boolean getSamplesStatus(Submission submission) {
        return samplesStatus;
    }

    public boolean getEnaStatus() {
        return enaStatus;
    }

    public boolean getAeStatus() {
        return aeStatus;
    }
}
