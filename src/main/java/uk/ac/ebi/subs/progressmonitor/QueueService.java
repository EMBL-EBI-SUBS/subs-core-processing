package uk.ac.ebi.subs.progressmonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.FullSubmission;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.submittable.*;
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
import uk.ac.ebi.subs.repository.submittable.*;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class QueueService {
    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    SupportingSampleRepository supportingSampleRepository;

    @Autowired
    FullSubmissionService fullSubmissionService;

    @Autowired
    AnalysisRepository analysisRepository;
    @Autowired
    AssayDataRepository assayDataRepository;
    @Autowired
    AssayRepository assayRepository;
    @Autowired
    EgaDacPolicyRepository egaDacPolicyRepository;
    @Autowired
    EgaDacRepository egaDacRepository;
    @Autowired
    EgaDatasetRepository egaDatasetRepository;
    @Autowired
    ProjectRepository projectRepository;
    @Autowired
    ProtocolRepository protocolRepository;
    @Autowired
    SampleGroupRepository sampleGroupRepository;
    @Autowired
    SampleRepository sampleRepository;
    @Autowired
    StudyRepository studyRepository;


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

        submission.setStatus(processingCertificate.getProcessingStatus().name()); //TODO rewrite this to use submission status

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


    /**
     * Consumes certificates from a map, using them to update the status of submittables.
     *
     * @param certByUuid
     * @param submittables
     * @param repository
     */
    private void updateStatusByCertificate(Map<String, ProcessingCertificate> certByUuid, List submittables, CrudRepository repository) {
        ListIterator listIterator = submittables.listIterator();

        while (listIterator.hasNext()) {
            Submittable s = (Submittable) listIterator.next();

            if (certByUuid.containsKey(s.getId())) {
                ProcessingCertificate cert = certByUuid.remove(s.getId());
                s.setStatus(cert.getProcessingStatus().name());
                if (!s.isAccessioned() && cert.getAccession() != null){
                    s.setAccession(cert.getAccession());
                }
            } else {
                listIterator.remove(); //don't bother updating these
            }
        }
        if (!submittables.isEmpty()) {
            repository.save(submittables);
        }
    }

    @RabbitListener(queues = Queues.SUBMISSION_MONITOR)
    public void checkForProcessedSubmissions(ProcessingCertificateEnvelope processingCertificateEnvelope) {

        String submissionId = processingCertificateEnvelope.getSubmissionId();


        logger.info("received agent results for submission {} with {} certificates ",
                submissionId, processingCertificateEnvelope.getProcessingCertificates().size());

        Map<String, ProcessingCertificate> certByUuid = new HashMap<>();
        processingCertificateEnvelope.getProcessingCertificates().forEach(c -> certByUuid.put(c.getSubmittableId(), c));


        if (!certByUuid.isEmpty()){
            List<Sample> samples = sampleRepository.findBySubmissionId(submissionId);
            updateStatusByCertificate(certByUuid,samples,sampleRepository);
        }
        if (!certByUuid.isEmpty()){
            List<Study> studies= studyRepository.findBySubmissionId(submissionId);
            updateStatusByCertificate(certByUuid,studies,studyRepository);
        }
        if (!certByUuid.isEmpty()){
            List<Assay> assays = assayRepository.findBySubmissionId(submissionId);
            updateStatusByCertificate(certByUuid,assays,assayRepository);
        }
        if (!certByUuid.isEmpty()){
            List<AssayData> assayData = assayDataRepository.findBySubmissionId(submissionId);
            updateStatusByCertificate(certByUuid,assayData,assayDataRepository);
        }

        if (!certByUuid.isEmpty()){
            List<Analysis> analyses = analysisRepository.findBySubmissionId(submissionId);
            updateStatusByCertificate(certByUuid,analyses,analysisRepository);
        }
        if (!certByUuid.isEmpty()){
            List<EgaDacPolicy> egaDacPolicies = egaDacPolicyRepository.findBySubmissionId(submissionId);
            updateStatusByCertificate(certByUuid,egaDacPolicies,egaDacPolicyRepository);
        }
        if (!certByUuid.isEmpty()){
            List<EgaDac> egaDacs = egaDacRepository.findBySubmissionId(submissionId);
            updateStatusByCertificate(certByUuid,egaDacs,egaDacRepository);
        }
        if (!certByUuid.isEmpty()){
            List<EgaDataset> egaDatasets = egaDatasetRepository.findBySubmissionId(submissionId);
            updateStatusByCertificate(certByUuid,egaDatasets,egaDatasetRepository);
        }
        if (!certByUuid.isEmpty()){
            List<Project> projects = projectRepository.findBySubmissionId(submissionId);
            updateStatusByCertificate(certByUuid,projects,projectRepository);
        }
        if (!certByUuid.isEmpty()){
            List<Protocol> protocols = protocolRepository.findBySubmissionId(submissionId);
            updateStatusByCertificate(certByUuid,protocols,protocolRepository);
        }
        if (!certByUuid.isEmpty()){
            List<SampleGroup> sampleGroups = sampleGroupRepository.findBySubmissionId(submissionId);
            updateStatusByCertificate(certByUuid,sampleGroups,sampleGroupRepository);
        }



        if (!certByUuid.isEmpty()){
            logger.error("Certificates remain to be processed for submission {}, but we can't find the entries they refer to {}",submissionId,certByUuid.values());
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
