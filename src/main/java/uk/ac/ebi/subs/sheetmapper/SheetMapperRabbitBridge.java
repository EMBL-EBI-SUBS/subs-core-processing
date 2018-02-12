package uk.ac.ebi.subs.sheetmapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.repository.model.sheets.Sheet;
import uk.ac.ebi.subs.repository.repos.SheetRepository;

@Component
public class SheetMapperRabbitBridge {

    private static final Logger logger = LoggerFactory.getLogger(SheetMapperRabbitBridge.class);

    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private SheetMapperService sheetMapperService;
    private SheetRepository sheetRepository;

    public SheetMapperRabbitBridge(RabbitMessagingTemplate rabbitMessagingTemplate, SheetMapperService sheetMapperService, SheetRepository sheetRepository) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.sheetMapperService = sheetMapperService;
        this.sheetRepository = sheetRepository;
    }

    @RabbitListener(queues = SheetMapperQueueConfig.SHEET_SUBMITTED_QUEUE)
    public void onSubmissionLoadSheetContents(Sheet sheet) {

        logger.info("sheet ready for loading {}", sheet.getId());

        sheetMapperService.mapSheet(sheet);

        logger.info("sheet mapped", sheet.getId());

        sheetRepository.delete(sheet);

        logger.info("sheet deleted", sheet.getId());
    }


}
