package org.oliot.epcis.service.capture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.oliot.epcis.configuration.Configuration;
import org.oliot.epcis.service.capture.mongodb.MongoCaptureUtil;
import org.oliot.model.jsonschema.JsonSchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JSONMasterCaptureService {

    private static Logger log = LoggerFactory.getLogger(JSONMasterCaptureService.class);

    /**
     * Verify JSON masters against JSON master Schema, in case of required.
     * @param inputString JSON masters
     * @return Valid JSON master list; null, in case of existing invalid JSON master
     */

    public List<JSONObject> prepareJSONMasters(String inputString) {
        List<JSONObject> INVALID_JSON_MASTERS = null;
        List<JSONObject> validJSONMasterList = new ArrayList<>();

        if (Configuration.isCaptureVerfificationOn == true) {

            // JSONParser parser = new JSONParser();
            JsonSchemaLoader schemaLoader = new JsonSchemaLoader();

            try {
                JSONObject jsonMaster = new JSONObject(inputString);
                JSONObject jsonMasterSchema = schemaLoader.getMasterDataSchema();

                if (!CaptureUtil.validate(jsonMaster, jsonMasterSchema)) {
                    log.info("Json Document is invalid" + " about general_validcheck");
                    return INVALID_JSON_MASTERS;
                }

                /* Schema check for Capture */
                JSONArray jsonMasterList = jsonMaster.getJSONObject("epcismd").getJSONObject("EPCISBody")
                        .getJSONArray("VocabularyList");

                for (int i = 0; i < jsonMasterList.length(); i++) {
                    JSONObject jsonMasterElement = jsonMasterList.getJSONObject(i);

                    if (jsonMasterElement.has("Vocabulary") == true) {

                        /* startpoint of validation logic for ObjectMaster */
                        JSONObject objectMasterSchema = schemaLoader.getObjectMasterSchema();
                        JSONObject jsonObjectMaster = jsonMasterElement.getJSONObject("Vocabulary");

                        if (!CaptureUtil.validate(jsonObjectMaster, objectMasterSchema)) {
                            log.info("Json Master Document is not valid" + " detail validation check for object Master");
                            return INVALID_JSON_MASTERS;
                        }
                        validJSONMasterList.add(jsonObjectMaster);
                    }  else {
                        log.info("Json Document is not valid. " + " It doesn't have standard event_type");
                        return INVALID_JSON_MASTERS;
                    }

                }
                if (jsonMasterList.length() != 0)
                    log.info(" EPCIS Document : Captured ");

            } catch (JSONException e) {
                log.info(" Json Document is not valid " + "second_validcheck");
                return INVALID_JSON_MASTERS;
            } catch (Exception e) {
                log.error( e.toString());
                return INVALID_JSON_MASTERS;
            }

            return validJSONMasterList;
        } else {
            JSONObject jsonEvent = new JSONObject(inputString);
            JSONArray jsonMasterList = jsonEvent.getJSONObject("epcismd").getJSONObject("EPCISBody")
                    .getJSONArray("VocabularyList");

            for (int i = 0; i < jsonMasterList.length(); i++) {
                JSONObject jsonMasterElement = jsonMasterList.getJSONObject(i);
                if (jsonMasterElement.has("Vocabulary") == true) {
                    validJSONMasterList.add(jsonMasterElement.getJSONObject("Vocabulary"));
                }
            }
        }
        return INVALID_JSON_MASTERS;
    }

    public void capturePreparedJSONMasters(List<JSONObject> validJsonMasterList, String userID) {
        if(null == validJsonMasterList)
        {
            log.info("No Masters Captured!");
            return;
        }
        MongoCaptureUtil m = new MongoCaptureUtil();
        for (JSONObject jsonObj :  validJsonMasterList) {
            m.captureJSONMaster(jsonObj, userID);
        }

        log.info("Masters Captured:" + validJsonMasterList.size());
    }

}
