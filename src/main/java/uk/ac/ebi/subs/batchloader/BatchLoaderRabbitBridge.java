package uk.ac.ebi.subs.batchloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.repository.model.SubmittablesBatch;
import uk.ac.ebi.subs.repository.repos.SubmittablesBatchRepository;

import java.util.Date;

@Component
public class BatchLoaderRabbitBridge {

    private static final Logger logger = LoggerFactory.getLogger(BatchLoaderRabbitBridge.class);

    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private SubmittablesBatchLoaderService submittablesBatchLoaderService;
    private SubmittablesBatchRepository submittablesBatchRepository;

    public BatchLoaderRabbitBridge(RabbitMessagingTemplate rabbitMessagingTemplate, SubmittablesBatchLoaderService submittablesBatchLoaderService, SubmittablesBatchRepository submittablesBatchRepository) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.submittablesBatchLoaderService = submittablesBatchLoaderService;
        this.submittablesBatchRepository = submittablesBatchRepository;
    }

    @RabbitListener(queues = BatchLoaderQueueConfig.BATCH_SUBMITTED_QUEUE)
    public void onSubmissionLoadSheetContents(SubmittablesBatch batch) {

        logger.info("batch ready for loading {}", batch.getId());

        submittablesBatchLoaderService.loadBatch(batch);

        logger.info("batch mapped", batch.getId());

        batch = submittablesBatchRepository.findOne(batch.getId());
        batch.setStatus("Completed");
        batch.setVersion( batch.getVersion() + 1 );
        batch.setLastModifiedDate(new Date());
        submittablesBatchRepository.save(batch);

        logger.info("batch status updated", batch.getId());
    }


}
