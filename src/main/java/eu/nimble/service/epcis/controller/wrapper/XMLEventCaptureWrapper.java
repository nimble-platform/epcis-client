package eu.nimble.service.epcis.controller.wrapper;

import eu.nimble.service.epcis.controller.BaseRestController;
import eu.nimble.service.epcis.services.AuthorizationSrv;
import eu.nimble.service.epcis.services.BlockchainService;
import eu.nimble.service.epcis.services.NIMBLETokenService;
import org.bson.BsonDocument;
import org.json.JSONObject;
import org.oliot.epcis.configuration.Configuration;
import org.oliot.epcis.service.capture.CaptureUtil;
import org.oliot.epcis.service.capture.JSONEventCaptureService;
import org.oliot.epcis.service.capture.mongodb.MongoCaptureUtil;
import org.oliot.model.epcis.EPCISDocumentType;
import org.oliot.model.epcis.NIMBLEUserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBElement;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class XMLEventCaptureWrapper extends BaseRestController {
    private static Logger log = LoggerFactory.getLogger(XMLEventCaptureWrapper.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    NIMBLETokenService nimbleTokenService;

    @Autowired
    BlockchainService blockchainService;

    @Autowired
    JSONEventCaptureService jsonEventCaptureSrv;

    @Autowired
    AuthorizationSrv authorizationSrv;

    @Value("${data-replication.remote_nimble_epcis_server.url}")
    public String remoteNIMBLEEPCISURL;

    @Value("${data-replication.remote_nimble_epcis_server.enabled}")
    public boolean remoteNIMBLEEPCISEnabled;

    @Value("${data-replication.blockchain.enable}")
    public boolean blockchainEnabled;

    @Value("${data-replication.blockchain.url}")
    public String blockchainURL;

    @PostMapping("/IntelligentXMLEventCapture")
    public ResponseEntity<?> post(@RequestBody String inputString,
                                  @RequestParam(required = false) Integer gcpLength,
                                  @RequestHeader(value = "Authorization", required = true) String token) {

        // Check NIMBLE authorization
        String userID = authorizationSrv.checkToken(token);
        if (userID == null) {
            return new ResponseEntity<>(new String("Invalid AccessToken"), HttpStatus.UNAUTHORIZED);
        }

        log.info(" EPCIS Document Capture Started.... ");

        // XSD based Validation
        if (Configuration.isCaptureVerfificationOn == true) {
            InputStream validateStream = CaptureUtil.getXMLDocumentInputStream(inputString);
            boolean isValidated = CaptureUtil.validate(validateStream,
                    "EPCglobal-epcis-1_2.xsd");
            if (isValidated == false) {
                return new ResponseEntity<>(
                        new String("[Error] Input EPCIS Document does not comply the standard schema"),
                        HttpStatus.BAD_REQUEST);
            }
           log.info(" EPCIS Document : Validated ");
        }

        InputStream epcisStream = CaptureUtil.getXMLDocumentInputStream(inputString);
        EPCISDocumentType epcisDocument = JAXB.unmarshal(epcisStream, EPCISDocumentType.class);

        if (Configuration.isCaptureVerfificationOn == true) {
            ResponseEntity<?> error = CaptureUtil.checkDocumentHeader(epcisDocument);
            if (error != null)
                return error;
        }

        List<Object> eventList = epcisDocument.getEPCISBody().getEventList()
                .getObjectEventOrAggregationEventOrQuantityEvent();


        List<BsonDocument> bsonDocumentList = eventList.parallelStream().parallel()
                .map(jaxbEvent -> prepareEvent(jaxbEvent, userID, gcpLength))
                .filter(doc -> doc != null).collect(Collectors.toList());


        List<JSONObject> validJsonEventList =  new ArrayList<>();

        for(BsonDocument filterDoc : bsonDocumentList){
            JSONObject jsonObject = new JSONObject(filterDoc.toJson());
            validJsonEventList.add(jsonObject);
        }

        // Local Capture
        log.info("Capture Events into company local storage.... ");
        jsonEventCaptureSrv.capturePreparedJSONEvents(validJsonEventList, userID);

        String bearerToken = null;
        String userPartyID = null;

        // Remote NIMBLE Server Capture
        boolean remoteCaptureSuccess = true;
        if (remoteNIMBLEEPCISEnabled) {
            // Login into NIMBLE for replication
            NIMBLEUserInfo nimbleUser = nimbleTokenService.loginNIMBLE();
            if (null == nimbleUser) {
                String msg = "Fail to replicate EPCIS event to NIMBLE platform and Blockchain, because failed to authorize the user with given name and password!";
                log.error(msg);
                return new ResponseEntity<>(msg, HttpStatus.INTERNAL_SERVER_ERROR);
            }
            bearerToken = nimbleUser.getBearerToken();
            userPartyID = nimbleUser.getUserPartyID();

            remoteCaptureSuccess = this.replicateRemoteEPCIS(inputString, bearerToken);
        }

        // Blockchain Capture
        boolean blockchainCaptureSuccess = true;
        if (blockchainEnabled) {
            blockchainCaptureSuccess = this.replicateBlockChain(validJsonEventList, userPartyID);
        }

        String responseMsg = "EPCIS Json event captured: " + validJsonEventList.size();
        HttpStatus status = HttpStatus.OK;
        if (!remoteCaptureSuccess) {
            responseMsg = responseMsg + "; failed to replicate json events to remote NIMBLE EPCIS server!";
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (!blockchainCaptureSuccess) {
            responseMsg = responseMsg + "; failed to replicate json events to remote Blockchain server!";
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return new ResponseEntity<>(responseMsg, status);
    }

    private BsonDocument prepareEvent(Object jaxbEvent, String userID, Integer gcpLength) {
        JAXBElement eventElement = (JAXBElement) jaxbEvent;
        Object event = eventElement.getValue();
        CaptureUtil.isCorrectEvent(event);
        MongoCaptureUtil m = new MongoCaptureUtil();
        BsonDocument doc = m.convert(event, userID, gcpLength);
        return doc;
    }


    /**
     * Replicate the xml events string into remote NIMBLE EPCIS server
     *
     * @param inputString
     * @return true, on success; false, otherwise
     */
    public boolean replicateRemoteEPCIS(String inputString, String bearerToken) {
        boolean success = true;

        String xmlCaptureURL = remoteNIMBLEEPCISURL + "EventCapture";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set("Authorization", bearerToken);
        HttpEntity<String> entity = new HttpEntity<String>(inputString, headers);

        try {
            restTemplate.exchange(xmlCaptureURL, HttpMethod.POST, entity, String.class);

            log.info("Captured Events into NIMBLE Server. ");

        } catch (HttpStatusCodeException e) {
            log.error(
                    "Received error during replicate xml events into NIMBLE platform: " + e.getResponseBodyAsString());
            success = false;
        }

        return success;
    }

    /**
     * Replicate each xml event into blockchain
     *
     * @param validJsonEventList
     * @return true, on success; false, otherwise
     */
    private boolean replicateBlockChain(List<JSONObject> validJsonEventList, String userPartyID) {

        boolean success = true;

        String eventCaptureURL = blockchainURL;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            for (JSONObject jsonObj : validJsonEventList) {
                JSONObject jsonObjForBlockchain = blockchainService.buildJSONEventForBC(jsonObj, userPartyID);

                HttpEntity<String> entity = new HttpEntity<String>(jsonObjForBlockchain.toString(), headers);

                restTemplate.exchange(eventCaptureURL, HttpMethod.POST, entity, String.class);
            }

            log.info("Captured Events into Blockchain: " + validJsonEventList.size());

        } catch (HttpStatusCodeException e) {
            log.error(
                    "Received error during replicate json events into Blockchain: " + e.getResponseBodyAsString());
            success = false;
        }

        return success;
    }
}
