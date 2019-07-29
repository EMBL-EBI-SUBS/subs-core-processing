package uk.ac.ebi.subs.processing.dispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;

import java.util.HashMap;
import java.util.Map;

/**
 * Dispatcher looks at the state of a submission and works out which archives need to handle it next.
 * This can be for the purposes of getting supporting information, or for archiving
 */
@Service
@EnableConfigurationProperties(DispatcherRoutingKeyProperties.class)
public class DispatcherRabbitBridge {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherRabbitBridge.class);

    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private DispatcherService dispatcherService;
    private SubmissionCompletionService submissionCompletionService;
    private DispatcherRoutingKeyProperties dispatcherRoutingKeyProperties;

    public DispatcherRabbitBridge(
            RabbitMessagingTemplate rabbitMessagingTemplate,
            MessageConverter messageConverter,
            DispatcherService dispatcherService,
            SubmissionCompletionService submissionCompletionService,
            DispatcherRoutingKeyProperties dispatcherRoutingKeyProperties
    ) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.rabbitMessagingTemplate.setMessageConverter(messageConverter);
        this.dispatcherService = dispatcherService;
        this.submissionCompletionService = submissionCompletionService;
        this.dispatcherRoutingKeyProperties = dispatcherRoutingKeyProperties;
    }


    /**
     * Determine what supporting information is required from the archives
     *
     * @param submissionEnvelopeSent that contains the submission object
     */
    @RabbitListener(queues = Queues.SUBMISSION_SUBMITTED_CHECK_SUPPORTING_INFO)
    public void checkSupportingInfoRequirement(SubmissionEnvelope submissionEnvelopeSent) {
        Submission submission = submissionEnvelopeSent.getSubmission();

        logger.info("checkSupportingInfoRequirement {}", submission);

        Map<Archive, SubmissionEnvelope> submissionEnvelopesForArchives =
                dispatcherService.determineSupportingInformationRequired(submissionEnvelopeSent);

        if (!submissionEnvelopesForArchives.containsKey(Archive.BioSamples)) {
            return;
        }

        //TODO only handles BioSamples
        SubmissionEnvelope submissionEnvelope = submissionEnvelopesForArchives.get(Archive.BioSamples);

        if (submissionEnvelope.getSupportingSamplesRequired().isEmpty()) {
            return;
        }

        rabbitMessagingTemplate.convertAndSend(
                Exchanges.SUBMISSIONS,
                Topics.EVENT_SUBMISSION_NEEDS_SAMPLES,
                submissionEnvelope
        );

    }

    /**
     * For a submission, assess which archives can be sent information for archiving. Send them the information
     * as a message
     *
     * @param submissionEnvelope
     */
    @RabbitListener(queues = Queues.SUBMISSION_DISPATCHER)
    public void dispatchToArchives(SubmissionEnvelope submissionEnvelope) throws InterruptedException {
        uk.ac.ebi.subs.data.Submission submission = submissionEnvelope.getSubmission();
        logger.debug("dispatchToArchives {}", submission);

        final String submissionId = submission.getId();

        if (submissionCompletionService.allSubmittablesProcessingFinished(submissionId)){
            submissionCompletionService.markSubmissionWithFinishedStatus(submissionId);
            logger.debug("submission completed {}", submission);
            return;
        }

        String jwtToken = submissionEnvelope.getJWTToken();

        Map<Archive, SubmissionEnvelope> readyToDispatch = dispatcherService
                .assessDispatchReadiness(submission, jwtToken);

        Map<String, String> archiveTopic = new HashMap<>();
        for (Map.Entry<String, String> routingKey : dispatcherRoutingKeyProperties.getRoutingKey().entrySet()) {
            final String archiveFromProperties = routingKey.getKey();
            if (dispatcherRoutingKeyProperties.getEnabled().contains(archiveFromProperties)) {
                archiveTopic.put(archiveFromProperties, routingKey.getValue());
            }
        }

        for (Map.Entry<Archive, SubmissionEnvelope> entry : readyToDispatch.entrySet()) {

            Archive archive = entry.getKey();
            SubmissionEnvelope submissionEnvelopeToTransmit = entry.getValue();

            if (!archiveTopic.containsKey(archive.name())) {
                throw new IllegalStateException("Dispatcher does not have topic mapping for archive " + archive + ". Processing submission " + submissionId);
            }

            String targetTopic = archiveTopic.get(archive.name());

            dispatcherService.updateSubmittablesStatusToSubmitted(archive, submissionEnvelopeToTransmit);

            Thread.sleep(10000);

            dispatcherService.insertReferencedSamples(submissionEnvelopeToTransmit);

            dispatcherService.insertUploadedFiles(submissionEnvelopeToTransmit);

            rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS, targetTopic, submissionEnvelopeToTransmit);
            logger.info("sent submission {} to {}", submissionId, targetTopic);
        }
    }
}