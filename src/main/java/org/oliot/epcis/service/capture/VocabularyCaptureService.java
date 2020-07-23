package org.oliot.epcis.service.capture;

import org.json.JSONObject;
import org.oliot.epcis.configuration.Configuration;
import org.oliot.model.epcis.EPCISMasterDataDocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXB;
import java.io.InputStream;

@Component
public class VocabularyCaptureService {

    private static Logger log = LoggerFactory.getLogger(VocabularyCaptureService.class);

    /**
     * Verify vocabular data in XML document, in case of required.
     * @param inputString vocabular data
     * @return  vocabular data, in case of valid; null, otherwise
     */
    public String prepareXMLVocabular(String inputString) {
        if (!Configuration.isCaptureVerfificationOn) {
            return inputString;
        }

        InputStream validateStream = CaptureUtil.getXMLDocumentInputStream(inputString);
        // Parsing and Validating data
        boolean isValidated = CaptureUtil.validate(validateStream,
                 "EPCglobal-epcis-masterdata-1_2.xsd");

        if (!isValidated) {
            return null;
        }

        return inputString;
    }

    public JSONObject capturePreparedXMLVocabular(String inputString, String userID, Integer gcpLength)
    {
        JSONObject retMsg = new JSONObject();

        InputStream epcisStream = CaptureUtil.getXMLDocumentInputStream(inputString);
        EPCISMasterDataDocumentType epcisMasterDataDocument = JAXB.unmarshal(epcisStream,
                EPCISMasterDataDocumentType.class);
        CaptureService cs = new CaptureService();
        retMsg = cs.capture(epcisMasterDataDocument, userID, gcpLength);
        log.info(" EPCIS Masterdata Document : Capturing ");

        return retMsg;
    }
}
