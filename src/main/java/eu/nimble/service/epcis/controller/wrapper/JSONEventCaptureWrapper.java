package eu.nimble.service.epcis.controller.wrapper;

import eu.nimble.service.epcis.controller.BaseRestController;
import eu.nimble.service.epcis.services.AuthorizationSrv;
import eu.nimble.service.epcis.services.BlockchainService;
import eu.nimble.service.epcis.services.NIMBLETokenService;
import org.json.JSONObject;
import org.oliot.epcis.service.capture.JSONEventCaptureService;
import org.oliot.model.epcis.NIMBLEUserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import java.util.List;

@RestController
public class JSONEventCaptureWrapper extends BaseRestController {

    private static Logger log = LoggerFactory.getLogger(JSONEventCaptureWrapper.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    NIMBLETokenService nimbleTokenService;

    @Autowired
    BlockchainService blockchainService;

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

    @PostMapping("/IntelligentJSONEventCapture")
    public ResponseEntity<?> post(@RequestBody String inputString,
                                  @RequestHeader(value = "Authorization", required = true) String token) {
        // Check NIMBLE authorization
        String userID = authorizationSrv.checkToken(token);
        if (userID == null) {
            return new ResponseEntity<>(new String("Invalid AccessToken"), HttpStatus.UNAUTHORIZED);
        }

        log.info(" EPCIS Json Document Capture Started.... ");

        JSONEventCaptureService jsonEventCaptureSrv = new JSONEventCaptureService();

        List<JSONObject> validJsonEventList = jsonEventCaptureSrv.prepareJSONEvents(inputString);
        if (null == validJsonEventList) {
            log.info("No Events Captured!");
            return new ResponseEntity<>("Error: Json Document is not valid. Details can be found in the log files.",
                    HttpStatus.BAD_REQUEST);
        }

        // Local Capture
        log.info("Capture Events into company local storage.... ");
        jsonEventCaptureSrv.capturePreparedJSONEvents(validJsonEventList, userID);

        String bearerToken = null;
        String userPartyID = null;
        // Replicate to Remote NIMBLE Server
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

        // Replicate to Blockchain
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

    /**
     * Replicate each json event into blockchain
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


    /**
     * Replicate the json events string into remote NIMBLE EPCIS server
     *
     * @param inputString
     * @return true, on success; false, otherwise
     */
    private boolean replicateRemoteEPCIS(String inputString, String bearerToken) {
        boolean success = true;

        String jsonCaptureURL = remoteNIMBLEEPCISURL + "JSONEventCapture";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken);
        HttpEntity<String> entity = new HttpEntity<String>(inputString, headers);

        try {
            restTemplate.exchange(jsonCaptureURL, HttpMethod.POST, entity, String.class);

           log.info("Captured Events into NIMBLE Server. ");

        } catch (HttpStatusCodeException e) {
            log.error(
                    "Received error during replicate json events into NIMBLE platform: " + e.getResponseBodyAsString());
            success = false;
        }

        return success;
    }

}
