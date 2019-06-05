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
import java.util.Collections;
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
        Archive archive = Archive.Ena;
        ProcessingCertificateEnvelope processingCertificateEnvelope =
                createProcessingCertificateEnvelope(archive, SUBMISSION_IDS.get(0));

        accessionConsumerAndPublisher.consumeAccessionIds(processingCertificateEnvelope);

        assertNull(accessionIdRepository.findBySubmissionId(SUBMISSION_IDS.get(0)));
    }

    @Test
    public void whenHasAccessionIdsFromBioStudiesButBioSamplesNotExistYetThenDoNotSendMessages() {
        doNothing().when(rabbitMessagingTemplate).convertAndSend(
                Exchanges.SUBMISSIONS,
                USI_ARCHIVE_ACCESSIONIDS_PUBLISHED_ROUTING_KEY,
                any(AccessionIdWrapper.class)
        );

        Archive archive = Archive.BioStudies;
        ProcessingCertificateEnvelope processingCertificateEnvelope =
                createProcessingCertificateEnvelope(archive, SUBMISSION_IDS.get(0));

        accessionConsumerAndPublisher.consumeAccessionIds(processingCertificateEnvelope);

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

        Archive archive = Archive.BioStudies;
        ProcessingCertificateEnvelope processingCertificateEnvelope =
                createProcessingCertificateEnvelope(archive, SUBMISSION_IDS.get(0));

        accessionConsumerAndPublisher.consumeAccessionIds(processingCertificateEnvelope);

        archive = Archive.BioSamples;
        processingCertificateEnvelope =
                createProcessingCertificateEnvelope(archive, SUBMISSION_IDS.get(0));

        accessionConsumerAndPublisher.consumeAccessionIds(processingCertificateEnvelope);

        accessionConsumerAndPublisher.sendAccessionIDs();

        verify(rabbitMessagingTemplate, times(1))
                .convertAndSend(Matchers.any(String.class), Matchers.any(String.class), Matchers.any(Object.class));
    }

    private ProcessingCertificateEnvelope createProcessingCertificateEnvelope(
            Archive archive, String submissionId) {
        ProcessingCertificateEnvelope processingCertificateEnvelope = new ProcessingCertificateEnvelope();
        processingCertificateEnvelope.setSubmissionId(submissionId);

        List<ProcessingCertificate> processingCertificates = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_SUBMITTABLE; i++) {
            processingCertificates.add(generateProcessingCertificate(archive));
            if (archive.equals(Archive.BioStudies)) {
                break;
            }
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
