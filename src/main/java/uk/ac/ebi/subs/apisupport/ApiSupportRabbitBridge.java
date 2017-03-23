package uk.ac.ebi.subs.apisupport;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.submittables.SubmittableRepository;

@Component
public class ApiSupportRabbitBridge {

    private static final Logger logger = LoggerFactory.getLogger(ApiSupportRabbitBridge.class);

    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private ApiSupportService apiSupportService;

    public ApiSupportRabbitBridge(RabbitMessagingTemplate rabbitMessagingTemplate, ApiSupportService apiSupportService) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.apiSupportService = apiSupportService;
    }

    @RabbitListener(queues = Queues.SUBMISSION_DELETED_CLEANUP_CONTENTS)
    public void onDeletionCleanupContents(Submission submission) {

        logger.info("submission contents for deletion {}",submission);

        apiSupportService.deleteSubmissionContents(submission);

    }
}
