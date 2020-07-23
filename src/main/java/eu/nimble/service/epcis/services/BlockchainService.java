package eu.nimble.service.epcis.services;

import com.github.wnameless.json.flattener.JsonFlattener;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BlockchainService {

    /**
     * Prepare the information that need to be kept in Blockchain for a given JSON
     * event object
     *
     * @param jsonEventObj
     * @param senderPartyID
     * @return the prepared information as JSONObject
     */
    public JSONObject buildJSONEventForBC(JSONObject jsonEventObj, String senderPartyID)
    {
        JSONObject jsonEventForBlockchain = new JSONObject();

        JSONObject unifiedJsonFields = this.unifyJsonObjFields(jsonEventObj);
        String unifiedJSONStr = this.unifyJsonString(unifiedJsonFields);
        String completeHash = DigestUtils.sha256Hex(unifiedJSONStr);
        jsonEventForBlockchain.put("hash", completeHash);

        List<String> productIDs = this.extractProductIDsFromEvent(jsonEventObj);
        JSONArray epcArray = new JSONArray(productIDs);
        jsonEventForBlockchain.put("epcList", epcArray);

        JSONObject dataHeaderField = new JSONObject();
        if(senderPartyID != null)
        {
            dataHeaderField.put("senderPartyID", senderPartyID);
        }
        JSONArray shortHashArray = new JSONArray();
        List<JSONObject> primEventList = this.getPrimitiveEventDataForAllProductIDs(jsonEventObj);
        for (JSONObject primEvent : primEventList) {
            JSONObject shortHash = new JSONObject();
            String unifiedPrimJSONStr = this.unifyJsonString(primEvent);
            String shortHashVal = DigestUtils.sha256Hex(unifiedPrimJSONStr);
            shortHash.put("hash", shortHashVal);
            shortHashArray.put(shortHash);
        }
        dataHeaderField.put("shortHashForEPCList", shortHashArray);
        JSONObject completetHashJsonObj = new JSONObject();
        completetHashJsonObj.put("hash", completeHash);
        dataHeaderField.put("completeHash", completetHashJsonObj);

        JSONObject dataField = new JSONObject();
        dataField.put("header", dataHeaderField);
        dataField.put("EventData", jsonEventObj);
        jsonEventForBlockchain.put("data", dataField);

        System.out.print("unifiedJSONStr: " + unifiedJSONStr + "\n");
        System.out.print("completeHash: " + completeHash + "\n");
        return jsonEventForBlockchain;
    }


    /**
     * Get a list of primitive JSON events. Each product id in the given JSON event will has one respective primitive JSON event.
     * Each primitive Json Event has four dimensions:
     * WHAT fields: the product id
     * WHEN fields:	 eventTimeZoneOffset, eventTime
     * WHERE fields: bizLocation, readPoint
     * WHY fields: bizStep
     * @param jsonEventObj original JSON event
     * @return a list of primitive JSON events
     */
    private List<JSONObject> getPrimitiveEventDataForAllProductIDs(JSONObject jsonEventObj)
    {
        List<JSONObject> primitiveEvents = new ArrayList<JSONObject>();

        List<String> productIDs = this.extractProductIDsFromEvent(jsonEventObj);
        JSONObject dimensionData = this.getEventPrimitiveData(jsonEventObj);
        for (String productID : productIDs) {
            JSONObject basicEventData = dimensionData;
            basicEventData.put("productID", productID);
            primitiveEvents.add(basicEventData);
        }

        return primitiveEvents;
    }

    /**
     * Get all product IDs from the event data
     *
     * @param jsonObj
     * @return any product ID in the form of EPC and EPC Class
     */
    private List<String> extractProductIDsFromEvent(JSONObject jsonObj) {
        List<String> productIDs = new ArrayList<String>();

//		List<String> MATCH_anyEPCAndEPCClass = Arrays.asList(new String[]{ "epcList.epc", "childEPCs.epc",
//				"inputEPCList.epc", "outputEPCList.epc", "parentID", "extension.quantityList.epcClass", "extension.childQuantityList.epcClass",
//				"inputQuantityList.epcClass", "outputQuantityList.epcClass" });

        List<String> MATCH_anyEPCAndEPCClassPath = Arrays
                .asList(new String[] { "$..parentID", "$..epc", "$..epcClass" });

        String jsonStr = jsonObj.toString();
        ReadContext ctx = JsonPath.parse(jsonStr);

        for (String path : MATCH_anyEPCAndEPCClassPath) {

            List<String> ids = ctx.read(path);

            productIDs.addAll(ids);
        }

        return productIDs;
    }

    /**
     * Standardize JSON fields, that will be used for hash calculation.
     * Remove the JSON fields, that may be changed after transfer to another data storage.

     * Without a unified JSON format i.e. fields, it is not possible to compare hash code for verification in Blockchain.
     * @param jsonObj
     * @return JSON Event with a unified JSON fields
     */
    private JSONObject unifyJsonObjFields(JSONObject jsonObj)
    {
        // Shallow copy
        JSONObject unifiedJsonObj = new JSONObject(jsonObj, JSONObject.getNames(jsonObj));

        // When company calculate hash, or send event data to Block-chain and local data storage,
        // the fields "_id", "userPartyID", "recordTime" are not included.
        List<String> removableFields =  Arrays.asList(new String[] { "_id", "userPartyID", "recordTime" });;

        for(String removableField : removableFields)
        {
            unifiedJsonObj.remove(removableField);
        }

        return unifiedJsonObj;
    }

    /**
     * Unify string representation of a JSON Event Object, in order to have same hash code for identical JSON objects.
     *
     * It can often happen, that identical JSON objects are presented as different strings. It will lead to different hash codes.
     * Because, for example, 1) key locations is different 2) value is different sorted in array
     *
     * In order to avoid this problem, this method will do following to have a unified string representation:
     * 1) Flatten key value pairs
     * 2) Remove the generated list numbers in the flattened key
     * 3) Each key value pair is connected as a string
     * 4) Sort the key value string
     * @param jsonObj JSON Event Object.
     * @return unified string representation.
     */
    private String unifyJsonString(JSONObject jsonObj)
    {
        Map<String, Object> flattenJson = JsonFlattener.flattenAsMap(jsonObj.toString());

        // Remove numbers from key e.g. "[0]" from "a.d[0]"
        String regexListNr = "\\[\\d+\\]";
        List<String> flattenKeyValPairList = flattenJson.entrySet().stream().
                map(entry -> entry.getKey().replaceAll(regexListNr, "") + "=" + entry.getValue()).
                collect(Collectors.toList());

        List<String> sortedFlattenKeyValPairList = flattenKeyValPairList.stream().sorted().collect(Collectors.toList());

        return String.join(",", sortedFlattenKeyValPairList);
    }


    /**
     * Get a JSON event object with the following dimensions:
     * WHEN fields:	 eventTimeZoneOffset, eventTime
     * WHERE fields: bizLocation, readPoint
     * WHY fields: bizStep
     *
     * The field "disposition" in the WHY dimension is possibly not exist, and will be included when it is necessary.
     * @param jsonObj
     * @return
     */
    private JSONObject getEventPrimitiveData(JSONObject jsonObj) {
        JSONObject basicJson = new JSONObject();

        List<String> basicFields = Arrays.asList(new String[] { "eventTimeZoneOffset", "eventTime", "bizLocation",
                "readPoint", "bizStep" });

        for (String field : basicFields) {

            if (jsonObj.has(field)) {
                basicJson.put(field, jsonObj.get(field));
            }
        }

        return basicJson;
    }

    private String getFileWithUtil(String fileName) throws IOException {
        String result = "";

        ClassLoader classLoader = getClass().getClassLoader();
        result = IOUtils.toString(classLoader.getResourceAsStream(fileName));

        return result;
    }

    public static void main(String[] args) throws Exception {
        String filename = "testFiles/eventJson_single_store.json";
        String filename2 = "testFiles/eventJson_single_store2.json";

        BlockchainService bcSrv = new BlockchainService();
        String content = bcSrv.getFileWithUtil(filename);
        JSONObject jsonObj1 = new JSONObject(content);

        String content2 = bcSrv.getFileWithUtil(filename2);
        JSONObject jsonObj2 = new JSONObject(content2);

        JSONObject jsonObjForBC = bcSrv.buildJSONEventForBC(jsonObj2, "1477");

        System.out.print(jsonObjForBC.toString());
    }
}
