package uk.ac.ebi.subs.progressmonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.SubmissionEnvelope;
import uk.ac.ebi.subs.data.submittable.*;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.processing.AgentResults;
import uk.ac.ebi.subs.repository.SubmissionRepository;
import uk.ac.ebi.subs.repository.submittable.*;

import java.util.ArrayList;
import java.util.List;

@Component
public class QueueService {
    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);

    @Autowired SubmissionRepository submissionRepository;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired AssayRepository assayRepository;
    @Autowired AssayDataRepository assayDataRepository;
    @Autowired EgaDacRepository egaDacRepository;
    @Autowired EgaDacPolicyRepository egaDacPolicyRepository;
    @Autowired EgaDatasetRepository egaDatasetRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired ProtocolRepository protocolRepository;
    @Autowired SampleRepository sampleRepository;
    @Autowired SampleGroupRepository sampleGroupRepository;
    @Autowired StudyRepository studyRepository;

    private RabbitMessagingTemplate rabbitMessagingTemplate;

    @Autowired
    public QueueService(RabbitMessagingTemplate rabbitMessagingTemplate) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
    }

    @RabbitListener(queues = Queues.SUBMISSION_SUPPORTING_INFO_PROVIDED)
    public void handleSupportingInfo(SubmissionEnvelope submissionEnvelope){
        //TODO add a way to store supporting info
        //TODO store supporting info,
        //TODO send submission to the dispatcher
    }


    @RabbitListener(queues = Queues.SUBMISSION_MONITOR)
    public void checkForProcessedSubmissions(AgentResults agentResults) {
        //TODO reconstruct submission+envelope from repo
        //TODO update repo with certs
        //TODO send assembled submission+envelope to dispatcher

        Submission queueSubmission = submissionEnvelope.getSubmission();

        logger.info("received submission {}, most recent handler was {}",
                queueSubmission.getId(),
                submissionEnvelope.mostRecentHandler());
        Submission mongoSubmission = submissionRepository.findOne(queueSubmission.getId());

        if(checkForUpdates(queueSubmission, mongoSubmission)) {
            //FIXME - Is this store/save doing an upsert? Check and fix if required.
            saveSubmissionContents(mongoSubmission);

            logger.info("updated submission {}",queueSubmission.getId());
        }
        else {
            logger.info("no changes for submission {}",queueSubmission.getId());
        }
    }

    private boolean checkForUpdates(Submission queueSubmission, Submission mongoSubmission) {
        boolean updates = false;

        List<Analysis> analyses = queueSubmission.getAnalyses();
        if(!analyses.isEmpty() && !StringUtils.isEmpty(analyses.get(0).getAccession())) {
            mongoSubmission.setAnalyses(analyses);
            updates = true;
        }

        List<Assay> assays = queueSubmission.getAssays();
        if(!assays.isEmpty() && !StringUtils.isEmpty(assays.get(0).getAccession())) {
            mongoSubmission.setAssays(assays);
            updates = true;
        }

        List<AssayData> assayData = new ArrayList<>();
        if(!assayData.isEmpty() && !StringUtils.isEmpty(assayData.get(0).getAccession())) {
            mongoSubmission.setAssayData(assayData);
            updates = true;
        }

        List<EgaDac> egaDacs = new ArrayList<>();
        if(!egaDacs.isEmpty() && !StringUtils.isEmpty(egaDacs.get(0).getAccession())) {
            mongoSubmission.setEgaDacs(egaDacs);
            updates = true;
        }

        List<EgaDacPolicy> egaDacPolicies = new ArrayList<>();
        if(!egaDacPolicies.isEmpty() && !StringUtils.isEmpty(egaDacPolicies.get(0).getAccession())) {
            mongoSubmission.setEgaDacPolicies(egaDacPolicies);
            updates = true;
        }

        List<EgaDataset> egaDatasets = new ArrayList<>();
        if(!egaDatasets.isEmpty() && !StringUtils.isEmpty(egaDatasets.get(0).getAccession())) {
            mongoSubmission.setEgaDatasets(egaDatasets);
            updates = true;
        }

        List<Project> projects = new ArrayList<>();
        if(!projects.isEmpty() && !StringUtils.isEmpty(projects.get(0).getAccession())) {
            mongoSubmission.setProjects(projects);
            updates = true;
        }

        List<Sample> samples = queueSubmission.getSamples();
        if(!samples.isEmpty() && !StringUtils.isEmpty(samples.get(0).getAccession())){
            mongoSubmission.setSamples(samples);
            updates = true;
        }

        List<Study> studies = new ArrayList<>();
        if(!studies.isEmpty() && !StringUtils.isEmpty(studies.get(0).getAccession())){
            mongoSubmission.setStudies(studies);
            updates = true;
        }

        return updates;
    }

    private void saveSubmissionContents(Submission submission) {
        analysisRepository.save(submission.getAnalyses());
        logger.debug("saved analyses {}");

        assayRepository.save(submission.getAssays());
        logger.debug("saved assays {}");

        assayDataRepository.save(submission.getAssayData());
        logger.debug("saved assayData {}");

        egaDacRepository.save(submission.getEgaDacs());
        logger.debug("saved egaDacs {}");

        egaDacPolicyRepository.save(submission.getEgaDacPolicies());
        logger.debug("saved egaDacPolicies {}");

        egaDatasetRepository.save(submission.getEgaDatasets());
        logger.debug("saved egaDatasets {}");

        projectRepository.save(submission.getProjects());
        logger.debug("saved projects {}");

        protocolRepository.save(submission.getProtocols());
        logger.debug("saved protocols {}");

        sampleRepository.save(submission.getSamples());
        logger.debug("saved samples {}");

        sampleGroupRepository.save(submission.getSampleGroups());
        logger.debug("saved sampleGroups {}");

        studyRepository.save(submission.getStudies());
        logger.debug("saved studies {}");

        submissionRepository.save(submission);
        logger.info("saved submission {}", submission.getId());
    }
}
