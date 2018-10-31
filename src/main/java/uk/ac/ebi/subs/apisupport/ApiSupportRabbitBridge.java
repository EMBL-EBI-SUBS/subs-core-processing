package uk.ac.ebi.subs.apisupport;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.repository.model.Submission;

/**
 * This Spring component contains listeners related to {@link Submission} operation.
 */
@Component
public class ApiSupportRabbitBridge {

    private static final Logger logger = LoggerFactory.getLogger(ApiSupportRabbitBridge.class);

    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private ApiSupportService apiSupportService;

    public ApiSupportRabbitBridge(RabbitMessagingTemplate rabbitMessagingTemplate, ApiSupportService apiSupportService) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.apiSupportService = apiSupportService;
    }

    /**
     * This listener deletes the contents of a given submission when the {@link Submission} has been deleted.
     * @param submission the deleted {@link Submission} entity
     */
    @RabbitListener(queues = Queues.SUBMISSION_DELETED_CLEANUP_CONTENTS)
    public void onDeletionCleanupContents(Submission submission) {
        logger.info("submission contents for deletion {}",submission);

        apiSupportService.deleteSubmissionContents(submission);
    }

    /**
     * Once a submission has been submitted, change the processing status of its submittables from 'draft' to 'submitted'.
     *
     * @param submission the {@link Submission} entity
     */
    @RabbitListener(queues = Queues.SUBMISSION_SUBMITTED_MARK_SUBMITTABLES)
    public void onSubmissionMarkSubmittablesSubmitted(Submission submission) {
        logger.info("Marking submittables as submitted for {}",submission.getId());

        apiSupportService.markContentsAsSubmitted(submission);
    }
}
