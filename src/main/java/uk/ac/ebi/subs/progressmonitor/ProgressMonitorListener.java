package uk.ac.ebi.subs.progressmonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.error.EntityNotFoundException;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;
import uk.ac.ebi.subs.processing.ProcessingCertificateEnvelope;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;

import java.util.Optional;

/**
 * Monitor is responsible for receiving information from archive agents and updating the state of the submission in our
 * database. Once the state is updated, a notification has to be sent to Rabbit to prompt the next dispatch cycle
 */
@Component
public class ProgressMonitorListener {
    private static final Logger logger = LoggerFactory.getLogger(ProgressMonitorListener.class);

    public ProgressMonitorListener(ProgressMonitorService monitorService, RabbitMessagingTemplate rabbitMessagingTemplate, SubmissionRepository submissionRepository) {
        this.monitorService = monitorService;
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.submissionRepository = submissionRepository;
    }

    private ProgressMonitorService monitorService;
    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private SubmissionRepository submissionRepository;

    @RabbitListener(queues = Queues.SUBMISSION_SUPPORTING_INFO_PROVIDED)
    public void storeSupportingInformation(SubmissionEnvelope submissionEnvelope) {
        monitorService.storeSupportingInformation(submissionEnvelope);

        sendSubmissionUpdated(submissionEnvelope.getSubmission().getId(), submissionEnvelope.getJWTToken());
    }

    @RabbitListener(queues = Queues.SUBMISSION_MONITOR)
    public void updateSubmittablesFromCertificates(ProcessingCertificateEnvelope processingCertificateEnvelope) {
        monitorService.updateSubmittablesFromCertificates(processingCertificateEnvelope);

        sendSubmissionUpdated(processingCertificateEnvelope.getSubmissionId(), processingCertificateEnvelope.getJWTToken());
    }

    /**
     * Submission or its supporting information has been updated
     * <p>
     * Recreate the submission envelope from storage and send it as a message
     *
     * @param submissionId the ID of the {@link Submission} to update
     */
    private void sendSubmissionUpdated(String submissionId, String jwtToken) {
        SubmissionEnvelope submissionEnvelope = new SubmissionEnvelope(
                Optional.of(submissionRepository.findOne(submissionId))
                .orElseThrow(() -> new EntityNotFoundException(
                    String.format("Submission entity with ID: %s is not found in the database.", submissionId)))
        );

        submissionEnvelope.setJWTToken(jwtToken);

        rabbitMessagingTemplate.convertAndSend(
                Exchanges.SUBMISSIONS,
                Topics.EVENT_SUBMISSION_PROCESSING_UPDATED,
                submissionEnvelope
        );

        logger.info("submission {} update message sent", submissionId);
    }

}
