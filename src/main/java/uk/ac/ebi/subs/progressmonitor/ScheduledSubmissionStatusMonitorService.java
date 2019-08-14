package uk.ac.ebi.subs.progressmonitor;

import com.mongodb.BasicDBObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.aggregation.UnwindOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;

import java.sql.Date;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static uk.ac.ebi.subs.processing.initialsubmissionprocessing.SubmissionStatusMessages.PROCESSING_IN_PROGRESS_MESSAGE;
import static uk.ac.ebi.subs.processing.initialsubmissionprocessing.SubmissionStatusMessages.SUBMITTED_MESSAGE;

@Service
@AllArgsConstructor
public class ScheduledSubmissionStatusMonitorService {

    private MongoTemplate mongoTemplate;
    private SubmissionStatusRepository submissionStatusRepository;

    private static final Logger logger = LoggerFactory.getLogger(ScheduledSubmissionStatusMonitorService.class);

    @Scheduled(cron = "0 */4 * * * *")
    public void setSubmissionStatusMessageIfOldAndNotInFinishedStatus() {
        logger.debug("Started to check old and not yet finished submissions.");
        Aggregation aggregation = Aggregation.newAggregation(
                getInitialProjection(),
                unwindSubmissionStatus(),
                matchSubmissionDateExistenceAndSubmissionStatus(),
                lookupSubmissionStatus(),
                projectSubmissionStatusFromEmbeddedDocumentAndSubmissionDate(),
                filterBySubmissionStatusAndSubmissionDate(),
                projectSubmissionStatusAndSubmissionDate(),
                sortBySubmissionDate()
        );

        AggregationResults<NotFinishedSubmissionData> notFinishedSubmissionData =
                this.mongoTemplate.aggregate(aggregation, "submission", NotFinishedSubmissionData.class);

        notFinishedSubmissionData.forEach(submissionData -> {
            SubmissionStatus submissionStatus =
                    submissionStatusRepository.findOne(submissionData.getSubmissionStatus_id());
            if (submissionStatus.getStatus().equals(SubmissionStatusEnum.Processing.name())) {
                setStatusMessage(submissionStatus, PROCESSING_IN_PROGRESS_MESSAGE);
            } else if (submissionStatus.getStatus().equals(SubmissionStatusEnum.Submitted.name())) {
                setStatusMessage(submissionStatus, SUBMITTED_MESSAGE);
            }
        });
    }

    private void setStatusMessage(SubmissionStatus submissionStatus, String statusMessage) {
        if (submissionStatus.getMessage() == null || !submissionStatus.getMessage().equals(statusMessage)) {
            logger.debug("Set status message of SubmissionStatus: {}", submissionStatus.getId());
            submissionStatus.setMessage(statusMessage);
            submissionStatusRepository.save(submissionStatus);
        }
    }

    private ProjectionOperation getInitialProjection() {
        AggregationExpression submissionStatusRelationProjection = aggregationOperationContext ->
                new BasicDBObject("$objectToArray", "$$ROOT.submissionStatus");

        return Aggregation.project("submissionDate")
                .and(submissionStatusRelationProjection).as("submissionStatus");
    }

    private UnwindOperation unwindSubmissionStatus() {
        return Aggregation.unwind("submissionStatus");
    }

    private MatchOperation matchSubmissionDateExistenceAndSubmissionStatus() {
        return Aggregation.match(Criteria.where("submissionStatus.k").is("$id")
                .and("submissionDate").exists(true));
    }

    private LookupOperation lookupSubmissionStatus() {
        return Aggregation.lookup("submissionStatus", "submissionStatus.v",
                "_id", "submissionStatus_document");
    }

    private ProjectionOperation projectSubmissionStatusFromEmbeddedDocumentAndSubmissionDate() {
        return Aggregation.project("submissionDate")
                    .and("submissionStatus_document.status").arrayElementAt(0).as("submissionStatus")
                    .and("submissionStatus_document._id").arrayElementAt(0).as("submissionStatus_id");
    }

    private MatchOperation filterBySubmissionStatusAndSubmissionDate() {
        LocalDateTime nowMinus12Hours = LocalDateTime.now().minusHours(12L);

        Criteria oldSubmissionDateCriteria =
                Criteria.where("submissionDate")
                        .lte(Date.from(nowMinus12Hours.toInstant(ZoneOffset.UTC)));

        Criteria submissionStatusCriteria =
                new Criteria().orOperator(Criteria.where("submissionStatus").is("Processing"),
                        Criteria.where("submissionStatus").is("Submitted"));

        return Aggregation.match(oldSubmissionDateCriteria.andOperator(submissionStatusCriteria));
    }

    private ProjectionOperation projectSubmissionStatusAndSubmissionDate() {
        return Aggregation.project("submissionDate", "submissionStatus_id", "submissionStatus");
    }

    private SortOperation sortBySubmissionDate() {
        return Aggregation.sort(new Sort(Sort.Direction.DESC, "submissionDate"));
    }

    @Data
    private class NotFinishedSubmissionData {

        @Id
        private String _id;
        private LocalDateTime submissionDate;
        private String submissionStatus_id;
        private String submissionStatus;
    }
}
