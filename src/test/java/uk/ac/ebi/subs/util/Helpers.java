package uk.ac.ebi.subs.util;


import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.processing.ProcessingCertificate;
import uk.ac.ebi.subs.processing.ProcessingCertificateEnvelope;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;

import java.util.List;
import java.util.UUID;

public class Helpers {

    public static ProcessingStatus generateProcessingStatus(String submittableId, ProcessingStatusEnum processingStatus) {
        ProcessingStatus ps = new ProcessingStatus(processingStatus);
        ps.setId(UUID.randomUUID().toString());
        ps.setSubmittableId(submittableId);
        ps.setArchive(Archive.BioSamples.name());
        ps.setSubmissionId(UUID.randomUUID().toString());
        ps.setStatus(processingStatus);
        return ps;
    }

    public static ProcessingCertificateEnvelope generateProcessingCertificateEnvelope(List<ProcessingCertificate> certificates) {
        ProcessingCertificateEnvelope pce = new ProcessingCertificateEnvelope();
        pce.setSubmissionId("test-submission-id");
        pce.setProcessingCertificates(certificates);
        return pce;
    }

    public static ProcessingCertificate generateProcessingCertificate(String submittableId, ProcessingStatusEnum processingStatus) {
        ProcessingCertificate pc = new ProcessingCertificate();
        pc.setSubmittableId(submittableId);
        pc.setProcessingStatus(processingStatus);
        pc.setArchive(Archive.BioSamples);
        pc.setAccession("SAMEA12345");
        return pc;
    }
}
