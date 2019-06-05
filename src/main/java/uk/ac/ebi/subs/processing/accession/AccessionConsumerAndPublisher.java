package uk.ac.ebi.subs.processing.accession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.processing.AccessionIdEnvelope;
import uk.ac.ebi.subs.processing.ProcessingCertificate;
import uk.ac.ebi.subs.processing.ProcessingCertificateEnvelope;
import uk.ac.ebi.subs.repository.model.accession.AccessionIdWrapper;
import uk.ac.ebi.subs.repository.repos.AccessionIdRepository;

import java.util.List;
import java.util.stream.Collectors;

import static uk.ac.ebi.subs.processing.accession.AccessionQueueConfig.USI_ACCESSIONIDS_CONSUMER;
import static uk.ac.ebi.subs.processing.accession.AccessionQueueConfig.USI_ARCHIVE_ACCESSIONIDS_PUBLISHED_ROUTING_KEY;

@Service
public class AccessionConsumerAndPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessionConsumerAndPublisher.class);

    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private AccessionIdRepository accessionIdRepository;

    public AccessionConsumerAndPublisher(RabbitMessagingTemplate rabbitMessagingTemplate, AccessionIdRepository accessionIdRepository) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.accessionIdRepository = accessionIdRepository;
    }

    @RabbitListener(queues = USI_ACCESSIONIDS_CONSUMER)
    public void consumeAccessionIds(ProcessingCertificateEnvelope processingCertificateEnvelope) {
        String submissionId = processingCertificateEnvelope.getSubmissionId();
        List<Archive> archives = processingCertificateEnvelope.getProcessingCertificates().stream()
                .filter(processingCertificate ->
                        processingCertificate.getArchive() == Archive.BioSamples
                        || processingCertificate.getArchive() == Archive.BioStudies)
                .map(ProcessingCertificate::getArchive)
                .distinct()
                .collect(Collectors.toList());

        if (archives.size() == 0) return;

        archives.forEach(archive -> {
            if (accessionIdRepository.findBySubmissionId(submissionId) == null) {
                if (archive == Archive.BioStudies) {
                    accessionIdRepository.save(
                            createAccessionIdWrapper(submissionId, getBioStudiesAccessionID(processingCertificateEnvelope), null));
                } else {
                    List<String> bioSamplesAccessionIDs = collectBioSamplesAccessionIDs(processingCertificateEnvelope);
                    accessionIdRepository.save(
                            createAccessionIdWrapper(submissionId, null, bioSamplesAccessionIDs));
                }
            } else {
                AccessionIdWrapper accessionIDWrapper = accessionIdRepository.findBySubmissionId(submissionId);
                if (archive == Archive.BioStudies) {
                    final String bioStudiesAccessionID = getBioStudiesAccessionID(processingCertificateEnvelope);

                    LOGGER.info("Update biostudies accessionID {} for submission: {}", bioStudiesAccessionID, submissionId);

                    accessionIDWrapper.setBioStudiesAccessionId(bioStudiesAccessionID);

                } else {
                    final List<String> bioSamplesAccessionIds = collectBioSamplesAccessionIDs(processingCertificateEnvelope);

                    LOGGER.info("Update biosamples accessionIDs {} for submission: {}", bioSamplesAccessionIds, submissionId);

                    accessionIDWrapper.setBioSamplesAccessionIds(bioSamplesAccessionIds);
                }

                accessionIdRepository.save(accessionIDWrapper);
            }
        });

    }

    private AccessionIdWrapper createAccessionIdWrapper(
            String submissionId, String bioStudiesAccessionId, List<String> bioSamplesAccessionIds) {
        AccessionIdWrapper accessionIdWrapper = new AccessionIdWrapper();
        accessionIdWrapper.setSubmissionId(submissionId);
        accessionIdWrapper.setBioStudiesAccessionId(bioStudiesAccessionId);
        accessionIdWrapper.setBioSamplesAccessionIds(bioSamplesAccessionIds);

        LOGGER.info("Persist accessionIDs to DB: {}", accessionIdWrapper);

        return accessionIdWrapper;
    }

    private String getBioStudiesAccessionID(ProcessingCertificateEnvelope processingCertificateEnvelope) {
        return processingCertificateEnvelope.getProcessingCertificates().get(0).getAccession();
    }

    private List<String> collectBioSamplesAccessionIDs(ProcessingCertificateEnvelope processingCertificateEnvelope) {
        return processingCertificateEnvelope.getProcessingCertificates().stream()
                .filter(processingCertificate ->
                        processingCertificate.getArchive() == Archive.BioSamples)
                .map(ProcessingCertificate::getAccession)
                .collect(Collectors.toList());
    }

    @Scheduled(fixedDelayString = "${usi.dispatcher.accessionid.delayTime}")
    public void sendAccessionIDs() {
        List<AccessionIdWrapper> accessionIdWrappers = accessionIdRepository.findByMessageSentDateIsNull();

        accessionIdWrappers.forEach(
            accessionIDWrapper -> {
                if (accessionIDWrapper.getBioSamplesAccessionIds() != null
                    && accessionIDWrapper.getBioSamplesAccessionIds().size() > 0
                    && accessionIDWrapper.getBioStudiesAccessionId() != null
                    && !accessionIDWrapper.getBioStudiesAccessionId().isEmpty()) {

                    AccessionIdEnvelope accessionIdEnvelope = createAndPopulateAccessionIdEnvelope(accessionIDWrapper);

                    LOGGER.info("Sent accessionIdEnvelope: {} to archives", accessionIdEnvelope);

                    rabbitMessagingTemplate.convertAndSend(
                                Exchanges.SUBMISSIONS,
                                USI_ARCHIVE_ACCESSIONIDS_PUBLISHED_ROUTING_KEY,
                                accessionIdEnvelope
                    );
                }
            }
        );
    }

    private AccessionIdEnvelope createAndPopulateAccessionIdEnvelope(AccessionIdWrapper accessionIDWrapper) {
        AccessionIdEnvelope accessionIdEnvelope = new AccessionIdEnvelope();
        accessionIdEnvelope.setBioSamplesAccessionIds(accessionIDWrapper.getBioSamplesAccessionIds());
        accessionIdEnvelope.setBioStudiesAccessionId(accessionIDWrapper.getBioStudiesAccessionId());

        return accessionIdEnvelope;
    }
}
