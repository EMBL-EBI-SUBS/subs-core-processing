package uk.ac.ebi.subs.processing;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.processing.accession.AccessionConsumerAndPublisher;
import uk.ac.ebi.subs.repository.model.accession.AccessionIdWrapper;
import uk.ac.ebi.subs.repository.repos.AccessionIdRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.any;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.ac.ebi.subs.processing.accession.AccessionQueueConfig.USI_ARCHIVE_ACCESSIONIDS_PUBLISHED_ROUTING_KEY;

@RunWith(SpringRunner.class)
@SpringBootTest
public class AccessionConsumerAndPublisherTest {

    private static final List<String> SUBMISSION_IDS = new ArrayList<>();
    private static final int NUMBER_OF_SUBMISSION = 5;
    private static final int NUMBER_OF_SUBMITTABLE = 4;

    @Autowired
    private AccessionConsumerAndPublisher accessionConsumerAndPublisher;

    @Autowired
    private AccessionIdRepository accessionIdRepository;

    @MockBean
    private RabbitMessagingTemplate rabbitMessagingTemplate;

    @Before
    public void setup() {
        for (int i = 0; i < NUMBER_OF_SUBMISSION; i++) {
            SUBMISSION_IDS.add(UUID.randomUUID().toString());
        }
    }

    @After
    public void teardown() {
        accessionIdRepository.deleteAll();
    }

    @Test
    public void whenMessageFromNotRelevantArchiveThenRepositoryEmpty() {
        List<Archive> archives = Arrays.asList(Archive.Ena, Archive.ArrayExpress, Archive.Ena, Archive.Metabolights, Archive.Ena);
        List<ProcessingCertificateEnvelope> processingCertificateEnvelopes = generateProcessingCertificateEnvelops(archives);

        for (int i = 0; i < NUMBER_OF_SUBMISSION; i++) {
            accessionConsumerAndPublisher.consumeAccessionIds(processingCertificateEnvelopes.get(i));
        }

        for (int i = 0; i < NUMBER_OF_SUBMISSION; i++) {
            assertNull(accessionIdRepository.findBySubmissionId(SUBMISSION_IDS.get(i)));
        }
    }

    @Test
    public void whenHasAccessionIdsFromBioStudiesButBioSamplesNotExistYetThenDoNotSendMessages() {
        doNothing().when(rabbitMessagingTemplate).convertAndSend(
                Exchanges.SUBMISSIONS,
                USI_ARCHIVE_ACCESSIONIDS_PUBLISHED_ROUTING_KEY,
                any(AccessionIdWrapper.class)
        );

        List<Archive> archives = Arrays.asList(Archive.Ena, Archive.BioStudies, Archive.Ena, Archive.BioStudies, Archive.Ena);
        List<ProcessingCertificateEnvelope> processingCertificateEnvelopes = generateProcessingCertificateEnvelops(archives);

        for (int i = 0; i < NUMBER_OF_SUBMISSION; i++) {
            accessionConsumerAndPublisher.consumeAccessionIds(processingCertificateEnvelopes.get(i));
        }

        List<AccessionIdWrapper> accessionIdWrappersFromRepo = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_SUBMISSION; i++) {
            accessionIdWrappersFromRepo.add(accessionIdRepository.findBySubmissionId(SUBMISSION_IDS.get(i)));
        }
        accessionIdWrappersFromRepo.removeIf(Objects::isNull);

        assertThat(accessionIdWrappersFromRepo.size(), is(equalTo(2)));
        accessionIdWrappersFromRepo.forEach(
                accessionIdWrapper -> assertThat(accessionIdWrapper.getBioStudiesAccessionId(), is(notNullValue()))
        );

        verify(rabbitMessagingTemplate, times(0))
            .convertAndSend(Matchers.any(String.class), Matchers.any(String.class), Matchers.any(Object.class));
    }

    @Test
    public void whenAccessionIdsFromBioSamplesAndBioStudiesExistThenSendMessageToArchives() {
        doNothing().when(rabbitMessagingTemplate).convertAndSend(
                Exchanges.SUBMISSIONS,
                USI_ARCHIVE_ACCESSIONIDS_PUBLISHED_ROUTING_KEY,
                any(AccessionIdWrapper.class)
        );

        List<Archive> archives = Arrays.asList(Archive.BioStudies, Archive.BioSamples, Archive.Ena, Archive.BioSamples, Archive.Ena);
        List<ProcessingCertificateEnvelope> processingCertificateEnvelopes = generateProcessingCertificateEnvelops(archives);

        for (int i = 0; i < NUMBER_OF_SUBMISSION; i++) {
            accessionConsumerAndPublisher.consumeAccessionIds(processingCertificateEnvelopes.get(i));
        }

        List<AccessionIdWrapper> accessionIdWrappersFromRepo = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_SUBMISSION; i++) {
            accessionIdWrappersFromRepo.add(accessionIdRepository.findBySubmissionId(SUBMISSION_IDS.get(i)));
        }
        accessionIdWrappersFromRepo.removeIf(Objects::isNull);

        assertThat(accessionIdWrappersFromRepo.size(), is(equalTo(3)));

        accessionConsumerAndPublisher.sendAccessionIDs();

        verify(rabbitMessagingTemplate, times(2))
                .convertAndSend(Matchers.any(String.class), Matchers.any(String.class), Matchers.any(Object.class));
    }

    private List<ProcessingCertificateEnvelope> generateProcessingCertificateEnvelops(List<Archive> archives) {
        List<ProcessingCertificateEnvelope> processingCertificateEnvelopes = new ArrayList<>();

        for (int i = 0; i < NUMBER_OF_SUBMISSION; i++) {
            processingCertificateEnvelopes.add(
                    createProcessingCertificateEnvelope(SUBMISSION_IDS.get(i), archives.get(i)));
        }

        return processingCertificateEnvelopes;
    }

    private ProcessingCertificateEnvelope createProcessingCertificateEnvelope(
            String submissionId, Archive archive) {
        ProcessingCertificateEnvelope processingCertificateEnvelope = new ProcessingCertificateEnvelope();
        processingCertificateEnvelope.setSubmissionId(submissionId);

        List<ProcessingCertificate> processingCertificates = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_SUBMITTABLE; i++) {
            processingCertificates.add(generateProcessingCertificate(archive));
            if (archive.equals(Archive.BioStudies)) {
                break;
            }
        }

        // to make a complete accession id wrapper object
        if (archive.equals(Archive.BioSamples)) {
            processingCertificates.add(generateProcessingCertificate(Archive.BioStudies));
        }

        processingCertificateEnvelope.setProcessingCertificates(processingCertificates);

        return processingCertificateEnvelope;
    }

    private ProcessingCertificate generateProcessingCertificate(Archive archive) {
        ProcessingCertificate processingCertificate = new ProcessingCertificate();
        processingCertificate.setArchive(archive);
        processingCertificate.setAccession(UUID.randomUUID().toString());

        return processingCertificate;
    }
}
