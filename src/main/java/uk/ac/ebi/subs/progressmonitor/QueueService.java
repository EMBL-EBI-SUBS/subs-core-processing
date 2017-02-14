package uk.ac.ebi.subs.progressmonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.FullSubmission;
import uk.ac.ebi.subs.data.Submission;

import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;
import uk.ac.ebi.subs.processing.ProcessingCertificate;
import uk.ac.ebi.subs.processing.ProcessingCertificateEnvelope;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.FullSubmissionService;
import uk.ac.ebi.subs.repository.SubmissionRepository;
import uk.ac.ebi.subs.repository.processing.SupportingSample;
import uk.ac.ebi.subs.repository.processing.SupportingSampleRepository;
import uk.ac.ebi.subs.repository.repos.SubmittablesBulkOperations;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class QueueService {
    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);

    @Autowired
    List<Class> submittablesClassList;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    SupportingSampleRepository supportingSampleRepository;

    @Autowired
    FullSubmissionService fullSubmissionService;

    @Autowired
    SubmittablesBulkOperations submittablesBulkOperations;


    private RabbitMessagingTemplate rabbitMessagingTemplate;

    @Autowired
    public QueueService(RabbitMessagingTemplate rabbitMessagingTemplate) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
    }

    @RabbitListener(queues = Queues.SUBMISSION_MONITOR_STATUS_UPDATE)
    public void submissionStatusUpdated(ProcessingCertificate processingCertificate) {
        if (processingCertificate.getSubmittableId() == null) return;

        Submission submission = submissionRepository.findOne(processingCertificate.getSubmittableId());

        if (submission == null) return;

//TODO fix in SUBS-333        submission.setStatus(processingCertificate.getProcessingStatus().name()); //TODO rewrite this to use submission status

        submissionRepository.save(submission);
    }

    @RabbitListener(queues = Queues.SUBMISSION_SUPPORTING_INFO_PROVIDED)
    public void handleSupportingInfo(SubmissionEnvelope submissionEnvelope) {

        final String submissionId = submissionEnvelope.getSubmission().getId();


        List<SupportingSample> supportingSamples = submissionEnvelope.getSupportingSamples().stream()
                .map(s -> new SupportingSample(submissionId, s))
                .collect(Collectors.toList());

        //store supporting info,
        logger.info(
                "storing supporting sample info for submission {}, {} samples",
                submissionEnvelope.getSubmission().getId(),
                supportingSamples.size()
        );

        supportingSampleRepository.save(supportingSamples);

        //send submission to the dispatcher

        sendSubmissionUpdated(submissionId);
    }


    @RabbitListener(queues = Queues.SUBMISSION_MONITOR)
    public void checkForProcessedSubmissions(ProcessingCertificateEnvelope processingCertificateEnvelope) {

        logger.info("received agent results for submission {} with {} certificates ",
                processingCertificateEnvelope.getSubmissionId(), processingCertificateEnvelope.getProcessingCertificates().size());


        for (Class submittableClass : submittablesClassList) {
            submittablesBulkOperations.applyProcessingCertificates(processingCertificateEnvelope, submittableClass);
        }

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
        FullSubmission submission = fullSubmissionService.fetchOne(submissionId);

        List<Sample> supportingSamples = supportingSampleRepository
                .findBySubmissionId(submissionId)
                .stream()
                .map(ss -> ss.getSample())
                .collect(Collectors.toList());


        SubmissionEnvelope submissionEnvelope = new SubmissionEnvelope(submission);
        submissionEnvelope.setSupportingSamples(supportingSamples);


        rabbitMessagingTemplate.convertAndSend(
                Exchanges.SUBMISSIONS,
                Topics.EVENT_SUBMISSION_UPDATED,
                submissionEnvelope
        );

        logger.info("submission {} update message sent", submissionId);
    }

}
