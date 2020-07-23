package eu.nimble.service.epcis.controller.wrapper;

import eu.nimble.service.epcis.controller.BaseRestController;
import eu.nimble.service.epcis.services.AuthorizationSrv;
import eu.nimble.service.epcis.services.NIMBLETokenService;
import org.json.JSONObject;
import org.oliot.epcis.service.capture.VocabularyCaptureService;
import org.oliot.model.epcis.NIMBLEUserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@RestController
public class VocabularyCaptureWrapper extends BaseRestController {
    private static Logger log = LoggerFactory.getLogger(VocabularyCaptureWrapper.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    NIMBLETokenService nimbleTokenService;

    @Autowired
    VocabularyCaptureService captureService;

    @Autowired
    AuthorizationSrv authorizationSrv;

    @Value("${data-replication.remote_nimble_epcis_server.url}")
    public String remoteNIMBLEEPCISURL;

    @Value("${data-replication.remote_nimble_epcis_server.enabled}")
    public boolean remoteNIMBLEEPCISEnabled;

    @PostMapping("/IntelligentVocabularyCapture")
    public ResponseEntity<?> post(@RequestBody String inputString,
                @RequestParam(required = false) Integer gcpLength, @RequestHeader(value = "Authorization", required = true) String token) {

        // Check NIMBLE authorization
        String userID = authorizationSrv.checkToken(token);
        if (userID == null) {
            return new ResponseEntity<>(new String("Invalid AccessToken"), HttpStatus.UNAUTHORIZED);
        }
        log.info(" EPCIS XML Master Document Capture Started.... ");

        String preparedMasterData = captureService.prepareXMLVocabular(inputString);
        if (preparedMasterData == null) {
            return new ResponseEntity<>(new String("Error: EPCIS Masterdata Document is not validated"),
                    HttpStatus.BAD_REQUEST);
        }

        // Local Capture
       log.info("Capture XML Master Data into company local storage.... ");
        JSONObject localRetMsg = captureService.capturePreparedXMLVocabular(preparedMasterData, userID, gcpLength);

        String bearerToken = null;
        // Remote NIMBLE Server Capture
        boolean remoteCaptureSuccess = true;
        if (remoteNIMBLEEPCISEnabled) {
            NIMBLEUserInfo nimbleUser = nimbleTokenService.loginNIMBLE();
            if (null == nimbleUser) {
                String msg = "Fail to replicate EPCIS event to NIMBLE platform and Blockchain, because failed to authorize the user with given name and password!";
                log.error(msg);
                return new ResponseEntity<>(msg, HttpStatus.INTERNAL_SERVER_ERROR);
            }
            bearerToken = nimbleUser.getBearerToken();
            remoteCaptureSuccess = this.replicateRemoteEPCIS(preparedMasterData, bearerToken);
        }


        String responseMsg = "EPCIS XML Master data captured." ;
        HttpStatus status = HttpStatus.OK;
        if (localRetMsg.isNull("error")) {
            responseMsg = " EPCIS Masterdata Document : Local Capture Sucessful! ";
            status = HttpStatus.OK;
        }
        else {
            responseMsg = " EPCIS Masterdata Document : Local Capture Failed! ";
            status = HttpStatus.BAD_REQUEST;
        }

        if (!remoteCaptureSuccess) {
            responseMsg = responseMsg + "; failed to replicate XML master data to remote NIMBLE EPCIS server!";
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        else if(remoteNIMBLEEPCISEnabled){
            responseMsg = responseMsg + "; successful to replicate XML master data to remote NIMBLE EPCIS server!";
        }

        return new ResponseEntity<>(responseMsg, status);
    }

    /**
     * Replicate the json events string into remote NIMBLE EPCIS server
     *
     * @param inputString
     * @return true, on success; false, otherwise
     */
    public boolean replicateRemoteEPCIS(String inputString, String bearerToken) {
        boolean success = true;

        String captureURL = remoteNIMBLEEPCISURL + "VocabularyCapture";

        if (null == bearerToken) {
           log.error(
                    "Fail to send EPCIS event to NIMBLE platform because failed to authorize the user with given name and password!");
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set("Authorization", bearerToken);
        HttpEntity<String> entity = new HttpEntity<String>(inputString, headers);

        try {
            restTemplate.exchange(captureURL, HttpMethod.POST, entity, String.class);

           log.info("Captured XML Master data into NIMBLE Server. ");

        } catch (HttpStatusCodeException e) {
            log.error(
                    "Received error during replicate XML Master data into NIMBLE platform: " + e.getResponseBodyAsString());
            success = false;
        }

        return success;
    }
}
