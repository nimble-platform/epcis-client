package eu.nimble.service.epcis.controller;

import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import eu.nimble.service.epcis.services.AuthorizationSrv;
import io.swagger.annotations.*;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.oliot.epcis.configuration.Configuration;
import org.oliot.epcis.service.query.mongodb.MongoQueryService;
import org.oliot.model.epcis.PollParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Iterator;

import static com.mongodb.client.model.Filters.eq;

@Api(tags = { "EPCIS MasterData Operation" })
@RestController
public class MasterDataController extends BaseRestController{

    @Autowired
    AuthorizationSrv authorizationSrv;

    private static Logger log = LoggerFactory.getLogger(MasterDataController.class);

    @ApiOperation( value = "", hidden = true)
    @GetMapping("/GetMasterDataItem")
    public ResponseEntity<?> getMasterDataItem(@RequestParam("id") String id,
           @RequestHeader(value="Authorization", required=true) String bearerToken) {

        // Check NIMBLE authorization
        String userPartyID = authorizationSrv.checkToken(bearerToken);
        if (userPartyID == null) {
            return new ResponseEntity<>(new String("Invalid AccessToken"), HttpStatus.UNAUTHORIZED);
        }

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Content-Type", "application/json; charset=utf-8");
        MongoCollection<BsonDocument> collection = Configuration.mongoDatabase.getCollection("MasterData",
                BsonDocument.class);
        BsonDocument masterDataItem = collection.find(
                eq("_id", new ObjectId(id))
        ).first();
        return new ResponseEntity<>(masterDataItem.toJson(), responseHeaders, HttpStatus.OK);
    }

    @ApiOperation(value = "Delete MasterData items for the given Id.",
            notes = "Delete all MasterData Items match on given Id", response = String.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "success"),
            @ApiResponse(code = 400, message = "ObjectId is not valid?"),
            @ApiResponse(code = 401, message = "Unauthorized. Are the headers correct?"), })
    @PostMapping("/deleteMasterDataItemById")
    public ResponseEntity<?> deleteMasterDataItemById(@ApiParam(value = "MasterData Item Id", required = true) @RequestParam("id") String id,
                                                  @ApiParam(value = "The Bearer token provided by the identity service", required = true)
                                                  @RequestHeader(value="Authorization", required=true) String bearerToken) {

        // Check NIMBLE authorization
        String userPartyID = authorizationSrv.checkToken(bearerToken);
        if (userPartyID == null) {
            return new ResponseEntity<>(new String("Invalid AccessToken"), HttpStatus.UNAUTHORIZED);
        }

        MongoCollection<BsonDocument> collection = Configuration.mongoDatabase.getCollection("MasterData",
                BsonDocument.class);
        BasicDBObject document = new BasicDBObject();
        document.append("id", id);
        collection.deleteMany(document);

        log.info("Delete All Master Data Items for Id: " + id);
        return new ResponseEntity<>("Delete All Master Data Items for Id: " + id, HttpStatus.OK);
    }


    @ApiOperation(value = "Get MasterData item for the given id.", notes = "Return one MasterData Item based on id",
            response = org.oliot.epcis.converter.mongodb.model.MasterData.class, responseContainer="List")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "success"),
            @ApiResponse(code = 401, message = "Unauthorized. Are the headers correct?"), })
    @GetMapping("/GetLatestMasterDataById")
    public ResponseEntity<?> getLatestMasterDataItemById(@ApiParam(value = "MasterData id in NIMBLE Platform", required = true) @RequestParam("id") String id,
                                               @ApiParam(value = "The Bearer token provided by the identity service", required = true)
                                               @RequestHeader(value="Authorization", required=true) String bearerToken) {

        // Check NIMBLE authorization
        String userPartyID = authorizationSrv.checkToken(bearerToken);
        if (userPartyID == null) {
            return new ResponseEntity<>(new String("Invalid AccessToken"), HttpStatus.UNAUTHORIZED);
        }

        PollParameters p = new PollParameters();
        p.setMaxElementCount(1);
        p.setEQ_name(id);
        p.setMasterDataFormat("JSON");
        p.setQueryName("SimpleMasterDataQuery");
        String masterDataItem = null;
        MongoQueryService mongoQueryService = new MongoQueryService();
        try {
            masterDataItem = mongoQueryService.poll(p, userPartyID);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ResponseEntity<>(masterDataItem, HttpStatus.OK);
    }

    @ApiOperation(value = "Get latest information of master data by type and id.", response = String.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "success"),
            @ApiResponse(code = 400, message = "ObjectId is not valid?"),
            @ApiResponse(code = 401, message = "Unauthorized. Are the headers correct?"), })
    @GetMapping("/getLatestMasterDataByTypeAndId")
    public ResponseEntity<?> getLatestMasterDataByTypeAndId(@ApiParam(value = "The Bearer token provided by the identity service", required = true)
                @RequestHeader(value="Authorization", required=true) String bearerToken,
                @ApiParam(value = "The type of master data.", required = true)
                @RequestParam(required = true) String type,
                @ApiParam(value = "The id of master data.", required = true)
                @RequestParam(required = true) String id) {

        // Check NIMBLE authorization
        String userPartyID = authorizationSrv.checkToken(bearerToken);
        if (userPartyID == null) {
            return new ResponseEntity<>(new String("Invalid AccessToken"), HttpStatus.UNAUTHORIZED);
        }

        PollParameters p = new PollParameters();
        p.setMaxElementCount(1);
        p.setEQ_name(id);
        p.setMasterDataFormat("JSON");
        p.setQueryName("SimpleMasterDataQuery");
        p.setVocabularyName(type);
        String masterDataItem = null;
        MongoQueryService mongoQueryService = new MongoQueryService();
        try {
            masterDataItem = mongoQueryService.poll(p, userPartyID);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ResponseEntity<>(masterDataItem, HttpStatus.OK);
    }

    @ApiOperation(value = "Get a list of master data BusinessLocation id.", response = String.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "success"),
            @ApiResponse(code = 400, message = "ObjectId is not valid?"),
            @ApiResponse(code = 401, message = "Unauthorized. Are the headers correct?"), })
    @GetMapping("/getBusinessLocationIdList")
    public ResponseEntity<?> getBusinessLocationIdList(@ApiParam(value = "The Bearer token provided by the identity service", required = true)
            @RequestHeader(value="Authorization", required=true) String bearerToken) {

        // Check NIMBLE authorization
        String userPartyID = authorizationSrv.checkToken(bearerToken);
        if (userPartyID == null) {
            return new ResponseEntity<>(new String("Invalid AccessToken"), HttpStatus.UNAUTHORIZED);
        }
        String type = "urn:epcglobal:epcis:vtype:BusinessLocation";
        return new ResponseEntity<>(getMasterDataIdByType(type), HttpStatus.OK);
    }

    @ApiOperation(value = "Get a list of master data ReadPoint id.", response = String.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "success"),
            @ApiResponse(code = 400, message = "ObjectId is not valid?"),
            @ApiResponse(code = 401, message = "Unauthorized. Are the headers correct?"), })
    @GetMapping("/getReadPointIdList")
    public ResponseEntity<?> getReadPointIdList(@ApiParam(value = "The Bearer token provided by the identity service", required = true)
                                                    @RequestHeader(value="Authorization", required=true) String bearerToken) {

        // Check NIMBLE authorization
        String userPartyID = authorizationSrv.checkToken(bearerToken);
        if (userPartyID == null) {
            return new ResponseEntity<>(new String("Invalid AccessToken"), HttpStatus.UNAUTHORIZED);
        }

        String type = "urn:epcglobal:epcis:vtype:ReadPoint";
        return new ResponseEntity<>(getMasterDataIdByType(type), HttpStatus.OK);
    }

    private JSONArray getMasterDataIdByType(String type) {

        MongoCollection<BsonDocument> collection = Configuration.mongoDatabase.getCollection("MasterData",
                BsonDocument.class);
        BasicDBObject document = new BasicDBObject();
        document.append("type", type);
        FindIterable<BsonDocument> findIterable = collection.find(document);
        Iterator iterator = findIterable.iterator();
        JSONArray jsonArray = new JSONArray();

        while (iterator.hasNext()) {
            BsonDocument bsonDocument = (BsonDocument) iterator.next();
            boolean exist = false;
            String currentValue = bsonDocument.getString("id").getValue();
            for (int i = 0; i<jsonArray.length(); i++) {
                if(jsonArray.get(i).equals(currentValue)) {
                    exist = true;
                }
            }
            if(!exist) {
                jsonArray.put(currentValue);
            }
        }
        return jsonArray;
    }
}
