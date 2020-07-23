package org.oliot.epcis.service.unit;

import eu.nimble.service.epcis.EPCISRepositoryApplication;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oliot.epcis.service.capture.mongodb.MongoCaptureUtil;
import org.oliot.epcis.service.query.mongodb.MongoQueryService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = EPCISRepositoryApplication.class)
public class JsonProductionProcTemplateTest {

    MongoCaptureUtil mongoCaptureUtil = new MongoCaptureUtil();
    MongoQueryService mongoQueryService = new MongoQueryService();

        String token = "testUser";
        String testJson = "{\n" +
                "  \"productClass\": \"testProductionClass\",\n" +
                "  \"productionProcessTemplate\": [\n" +
                "    {\n" +
                "      \"id\": \"1\",\n" +
                "      \"hasPrev\": \"\",\n" +
                "      \"readPoint\": \"urn:epc:id:sgln:readPoint.lindbacks.1\",\n" +
                "      \"bizLocation\": \"urn:epc:id:sgln:bizLocation.lindbacks.2\",\n" +
                "      \"bizStep\": \"urn:epcglobal:cbv:bizstep:other\",\n" +
                "      \"hasNext\": \"2\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"2\",\n" +
                "      \"hasPrev\": \"1\",\n" +
                "      \"readPoint\": \"urn:epc:id:sgln:readPoint.lindbacks.4\",\n" +
                "      \"bizLocation\": \"urn:epc:id:sgln:bizLocation.lindbacks.5\",\n" +
                "      \"bizStep\": \"urn:epcglobal:cbv:bizstep:shipping\",\n" +
                "      \"hasNext\": \"3\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        String resultTestJson = "[\n" +
                " {\n" +
                "  \"bizStep\": \"urn:epcglobal:cbv:bizstep:other\",\n" +
                "  \"hasPrev\": \"\",\n" +
                "  \"readPoint\": \"urn:epc:id:sgln:readPoint.lindbacks.1\",\n" +
                "  \"bizLocation\": \"urn:epc:id:sgln:bizLocation.lindbacks.2\",\n" +
                "  \"hasNext\": \"2\",\n" +
                "  \"id\": \"1\"\n" +
                " },\n" +
                " {\n" +
                "  \"bizStep\": \"urn:epcglobal:cbv:bizstep:shipping\",\n" +
                "  \"hasPrev\": \"1\",\n" +
                "  \"readPoint\": \"urn:epc:id:sgln:readPoint.lindbacks.4\",\n" +
                "  \"bizLocation\": \"urn:epc:id:sgln:bizLocation.lindbacks.5\",\n" +
                "  \"hasNext\": \"3\",\n" +
                "  \"id\": \"2\"\n" +
                " }\n" +
                "]";

    /**
     * @Test
     * Capture Json Production Process Template and
     * compare with resultTestJson
     */
    @Test
    public void captureJSONProductionProcTemplate() {
        JSONObject jsonProductionProc = new JSONObject(testJson);
        mongoCaptureUtil.captureJSONProductionProcTemplate(jsonProductionProc, token);
        try {
            assertTrue(mongoQueryService.pollProductionProcTemplateQuery("testProductionClass").equals(resultTestJson));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @Test
     * This class "test" does not have entry on database
     */
    @Test
    public void getProductionProcessTemplateWithoutClass() {
        try {
            assertTrue(mongoQueryService.pollProductionProcTemplateQuery("test").equals("[]"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @Test
     * This class "testProductionClass" have entry of database
     */
    @Test
    public void getProductionProcessTemplateWithClass() {
        try {
            assertTrue(mongoQueryService.pollProductionProcTemplateQuery("testProductionClass").equals(resultTestJson));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
