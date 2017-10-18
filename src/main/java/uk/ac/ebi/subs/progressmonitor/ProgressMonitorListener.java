package uk.ac.ebi.subs.progressmonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;
import uk.ac.ebi.subs.processing.ProcessingCertificateEnvelope;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;

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

        sendSubmissionUpdated(submissionEnvelope.getSubmission().getId());
    }

    @RabbitListener(queues = Queues.SUBMISSION_MONITOR)
    public void updateSubmittablesFromCertificates(ProcessingCertificateEnvelope processingCertificateEnvelope) {
        monitorService.updateSubmittablesFromCertificates(processingCertificateEnvelope);

        sendSubmissionUpdated(processingCertificateEnvelope.getSubmissionId());
    }

    /**
     * Submission or it's supporting information has been updated
     * <p>
     * Recreate the submission envelope from storage and send it as a message
     *
     * @param submissionId
     */
    private void sendSubmissionUpdated(String submissionId) {

        rabbitMessagingTemplate.convertAndSend(
                Exchanges.SUBMISSIONS,
                Topics.EVENT_SUBMISSION_PROCESSING_UPDATED,
                submissionRepository.findOne(submissionId)
        );

        logger.info("submission {} update message sent", submissionId);
    }

}
