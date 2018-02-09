package uk.ac.ebi.subs.sheetmapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.apisupport.ApiSupportService;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.sheets.Sheet;
import uk.ac.ebi.subs.repository.repos.SheetRepository;

@Component
public class SheetMapperRabbitBridge {

    private static final Logger logger = LoggerFactory.getLogger(SheetMapperRabbitBridge.class);

    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private ApiSupportService apiSupportService;
    private SheetRepository sheetRepository;

    public SheetMapperRabbitBridge(RabbitMessagingTemplate rabbitMessagingTemplate, ApiSupportService apiSupportService, SheetRepository sheetRepository) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.apiSupportService = apiSupportService;
        this.sheetRepository = sheetRepository;
    }

    @RabbitListener(queues = SheetMapperQueueConfig.SHEET_SUBMITTED_QUEUE)
    public void onSubmissionLoadSheetContents(Sheet sheet) {

        logger.info("sheet ready for loading {}", sheet.getId());

        sheetRepository.delete(sheet);
    }



}
