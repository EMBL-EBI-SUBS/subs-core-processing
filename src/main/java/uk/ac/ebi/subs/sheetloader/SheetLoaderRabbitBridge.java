package uk.ac.ebi.subs.sheetloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.repository.model.sheets.Sheet;
import uk.ac.ebi.subs.repository.model.sheets.SheetStatusEnum;
import uk.ac.ebi.subs.repository.repos.SheetRepository;

import java.util.Date;

@Component
public class SheetLoaderRabbitBridge {

    private static final Logger logger = LoggerFactory.getLogger(SheetLoaderRabbitBridge.class);

    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private SheetLoaderService sheetLoaderService;
    private SheetRepository sheetRepository;

    public SheetLoaderRabbitBridge(RabbitMessagingTemplate rabbitMessagingTemplate, SheetLoaderService sheetLoaderService, SheetRepository sheetRepository) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.sheetLoaderService = sheetLoaderService;
        this.sheetRepository = sheetRepository;
    }

    @RabbitListener(queues = SheetLoaderQueueConfig.SHEET_SUBMITTED_QUEUE)
    public void onSubmissionLoadSheetContents(Sheet sheet) {

        logger.info("sheet ready for loading {}", sheet.getId());

        sheetLoaderService.loadSheet(sheet);

        logger.info("sheet mapped", sheet.getId());


    }


}
