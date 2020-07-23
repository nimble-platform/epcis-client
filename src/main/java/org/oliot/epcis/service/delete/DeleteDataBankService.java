package org.oliot.epcis.service.delete;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import eu.nimble.service.epcis.controller.BaseRestController;
import eu.nimble.service.epcis.services.AuthorizationSrv;
import eu.nimble.service.epcis.services.NIMBLETokenService;
import io.swagger.annotations.*;
import org.bson.BsonDocument;
import org.oliot.epcis.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.oliot.model.epcis.NIMBLEUserInfo;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Api(tags = { "EPCIS DataBank Delete" })
@RestController
public class DeleteDataBankService extends BaseRestController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    AuthorizationSrv authorizationSrv;

    @Autowired
    NIMBLETokenService nimbleTokenService;

    @Value("${data-replication.remote_nimble_epcis_server.url}")
    public String remoteNIMBLEEPCISURL;

    @Value("${data-replication.remote_nimble_epcis_server.enabled}")
    public boolean remoteNIMBLEEPCISEnabled;

    private static Logger log = LoggerFactory.getLogger(DeleteDataBankService.class);

    @ApiOperation(value = "Delete DataBank based on type", notes = "Delete DataBank based on user and type. A User automatically detect by token. We have 4 available type and they are: <br>" +
            "<textarea disabled style=\"width:98%\" class=\"body-textarea\">" +
            "EventData => To delete all EventData \n" +
            "MasterData => To delete all MasterData \n" +
            "ProductionProcessTemplate => To delete all Production Process Template Data \n" +
            "Data => To delete this all 3 types of data"
            + " </textarea>", response = String.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "success"),
            @ApiResponse(code = 400, message = "ObjectId is not valid?"),
            @ApiResponse(code = 401, message = "Unauthorized. Are the headers correct?"), })
    @PostMapping("/deleteDataBank")
    public ResponseEntity<?> deleteDataBank(@ApiParam(value = "Which type of data you want to delete?", required = true)@RequestParam("type") String type,
                @ApiParam(value = "The Bearer token provided by the identity service", required = true)
                @RequestHeader(value="Authorization", required=true) String bearerToken) {

        // Check NIMBLE authorization
        String userPartyID = authorizationSrv.checkToken(bearerToken);
        if (userPartyID == null) {
            return new ResponseEntity<>(new String("Invalid AccessToken"), HttpStatus.UNAUTHORIZED);
        }

        if(type.equals("Data")) {
            String[] dataTypes = {"EventData", "MasterData", "ProductionProcessTemplate"};
            for (String dataType : dataTypes) {
                deleteDataByType(dataType, userPartyID);
            }
        } else {
            deleteDataByType(type, userPartyID);
        }
        log.info("Delete all " + type + " from local server for User: " + userPartyID);

        // Remote NIMBLE Server Delete
        boolean remoteDeleteSuccess = true;
        if (remoteNIMBLEEPCISEnabled) {
            NIMBLEUserInfo nimbleUser = nimbleTokenService.loginNIMBLE();
            if (null == nimbleUser) {
                String msg = "Fail to dekete EPCIS data from remote server, because failed to authorize the user with given name and password!";
                log.error(msg);
                return new ResponseEntity<>(msg, HttpStatus.INTERNAL_SERVER_ERROR);
            }
            bearerToken = nimbleUser.getBearerToken();
            remoteDeleteSuccess = this.deleteRemoteEPCIS(type, bearerToken);
        }

        String responseMsg = "Delete all " + type + " for User: " + userPartyID;
        HttpStatus status = HttpStatus.OK;
        if (!remoteDeleteSuccess) {
            responseMsg = responseMsg + "; failed to delete data from remote NIMBLE EPCIS server!";
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return new ResponseEntity<>(responseMsg,status);
    }

    private void deleteDataByType(String type, String userPartyID) {
        MongoCollection<BsonDocument> collection = Configuration.mongoDatabase.getCollection(type,
                BsonDocument.class);
        BasicDBObject document = new BasicDBObject();
        document.append("userPartyID", userPartyID);
        collection.deleteMany(document);
    }

    public boolean deleteRemoteEPCIS(String type, String bearerToken) {
        boolean success = true;

        String deleteDataBankURL = remoteNIMBLEEPCISURL + "deleteDataBank";

        if (null == bearerToken) {
            log.error(
                    "Fail to delete from remote server because failed to authorize the user with given name and password!");
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        //adding the query params to the URL
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(deleteDataBankURL)
                .queryParam("type", type);

        try {
            restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.POST, entity, String.class);
            log.info("Delete data from Remote NIMBLE Server. ");
        } catch (HttpStatusCodeException e) {
            log.error(
                    "Received error during delete data from remote server: " + e.getResponseBodyAsString());
            success = false;
        }

        return success;
    }


}
