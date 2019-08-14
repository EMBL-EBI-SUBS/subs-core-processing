package uk.ac.ebi.subs.processing.initialsubmissionprocessing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Topics;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.model.Submission;

/**
 * This Spring component contains listeners related to {@link Submission} assign to archives operation.
 */
@Component
public class Listener {

    private static final Logger logger = LoggerFactory.getLogger(Listener.class);

    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private SubmissionProcessingService submissionProcessingService;

    @RabbitListener(queues = QueueConfig.SUBMISSION_ARCHIVE_ASSIGNMENT)
    public void assignArchives(SubmissionEnvelope submissionEnvelope) {
        final uk.ac.ebi.subs.data.Submission submission = submissionEnvelope.getSubmission();
        final String submissionId = submission.getId();
        submissionProcessingService.setSubmissionStatusToProcessing(submissionId);
        submissionProcessingService.assignArchives(submissionId);

        logger.info("archives assigned {}", submission);

        rabbitMessagingTemplate.convertAndSend(
                Exchanges.SUBMISSIONS,
                Topics.EVENT_SUBMISSION_PROCESSING_UPDATED,
                submissionEnvelope
        );
    }

    public Listener(RabbitMessagingTemplate rabbitMessagingTemplate, SubmissionProcessingService submissionProcessingService) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.submissionProcessingService = submissionProcessingService;
    }
}
