package uk.ac.ebi.subs.progressmonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.processing.ProcessingCertificate;
import uk.ac.ebi.subs.processing.ProcessingCertificateEnvelope;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;
import uk.ac.ebi.subs.repository.SubmissionRepository;
import uk.ac.ebi.subs.repository.processing.SupportingSample;
import uk.ac.ebi.subs.repository.processing.SupportingSampleRepository;
import uk.ac.ebi.subs.repository.submittable.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class QueueService {
    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired SupportingSampleRepository supportingSampleRepository;

    private RabbitMessagingTemplate rabbitMessagingTemplate;

    @Autowired
    public QueueService(RabbitMessagingTemplate rabbitMessagingTemplate) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
    }

    @RabbitListener(queues = Queues.SUBMISSION_MONITOR_STATUS_UPDATE)
    public void submissionStatusUpdated(ProcessingCertificate processingCertificate){
        if (processingCertificate.getSubmittableId() == null) return;

        Submission submission = submissionRepository.findOne(processingCertificate.getSubmittableId());

        if (submission == null) return;

        submission.setStatus(processingCertificate.getProcessingStatus().name());

        submissionRepository.save(submission);
    }

    @RabbitListener(queues = Queues.SUBMISSION_SUPPORTING_INFO_PROVIDED)
    public void handleSupportingInfo(SubmissionEnvelope submissionEnvelope){

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

        Map<String,ProcessingCertificate> certByUuid = new HashMap<>();
        processingCertificateEnvelope.getProcessingCertificates().forEach(c -> certByUuid.put(c.getSubmittableId(),c));

        Submission submission = submissionRepository.findOne(processingCertificateEnvelope.getSubmissionId());


        //update repo based on certs
        submission.allSubmissionItemsStream().forEach(s -> {
                if (!certByUuid.containsKey(s.getId())) return;

                ProcessingCertificate c = certByUuid.get(s.getId());
                s.setAccession(c.getAccession());
                s.setStatus(c.getProcessingStatus().toString());

                logger.debug("ProcessingCertificate {} applied to {}",c,s);
            }
        );

        submissionRepository.save(submission);

        sendSubmissionUpdated(processingCertificateEnvelope.getSubmissionId());
    }

    /**
     * Submission or it's supporting information has been updated
     *
     * Recreate the submission envelope from storage and send it as a message
     *
     * @param submissionId
     */
    private void sendSubmissionUpdated(String submissionId) {
        Submission submission = submissionRepository.findOne(submissionId);

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
