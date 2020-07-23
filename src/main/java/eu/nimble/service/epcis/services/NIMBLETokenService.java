package eu.nimble.service.epcis.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.json.JSONObject;
import org.oliot.model.epcis.NIMBLEUserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.TimeUnit;

@Service
public class NIMBLETokenService {

    private static Logger log = LoggerFactory.getLogger(NIMBLETokenService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${data-replication.remote_nimble_epcis_server.url}")
    public String remoteNIMBLEEPCISURL;

    @Value("${spring.identity-service.url}")
    public String identityServiceURL;

    @Value("${data-replication.remote_nimble_epcis_server.username}")
    public String remoteEPCISUsername;

    @Value("${data-replication.remote_nimble_epcis_server.password}")
    public String remoteEPCISPassword;

    //TODO: Clean the code; tranform the project into spring boot project; and then use spring boot configurations
    private static Cache<String, String> tokenCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    /**
     * Login NIMBLE with configured username and password.
     *
     * @return NIMBLEUserInfo, on success; null, failed.
     */
    public NIMBLEUserInfo loginNIMBLE()
    {
        NIMBLEUserInfo user = null;

        String bearerToken = this.getBearerToken();
        String userPartyID = this.getUserPartyID(bearerToken);

        if(bearerToken != null && userPartyID != null)
        {
            user = new NIMBLEUserInfo();
            user.setUsername(remoteEPCISUsername);
            user.setPassword(remoteEPCISPassword);
            user.setBearerToken(bearerToken);
            user.setUserPartyID(userPartyID);
        }


        return user;
    }

    public String getBearerToken() {
        String tokenCacheKey = remoteEPCISUsername + "_" + remoteEPCISPassword;

        String token = null;

        token = tokenCache.getIfPresent(tokenCacheKey);
        if(token == null)
        {
            token = getBearerToken(remoteEPCISUsername, remoteEPCISPassword);
            if(token != null)
            {
                tokenCache.put(tokenCacheKey, token);
            }
        }

//		String token = tokenCache.get(tokenCacheKey,
//				k->getBearerToken(remoteEPCISUsername, remoteEPCISPassword)
//				);

        return token;
    }

    public String getUserPartyID(String accessToken)
    {
        String userPartyID = tokenCache.getIfPresent(accessToken);
        if(userPartyID == null)
        {
            userPartyID = checkToken(accessToken);
            if(userPartyID != null)
            {
                tokenCache.put(accessToken, userPartyID);
            }
        }

        return userPartyID;

    }

    /**
     * Extracts the identity from an OpenID Connect token and fetches the associated company from the Identity Service.
     *
     * @param accessToken  OpenID Connect token storing identity.
     * @return  Identifier of associated company; null, in case of exception or no valid user.
     */
    private String checkToken(String accessToken)
    {
        if (accessToken == null) {
            log.error("No token is given");
            return null;
        }

        String url = identityServiceURL + "/user-info";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.set("Authorization", accessToken);

        String userPartyID = "";
        try {
            HttpEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<Object>(headers) , String.class);
            JSONObject jsonUser = new JSONObject(response.getBody());
            userPartyID = jsonUser.getString("ublPartyID");

            return userPartyID;
        } catch (HttpStatusCodeException e) {
           log.error("Error or the token is invalid in NIMBLE platform: " + e.getResponseBodyAsString());
        }

        return null;
    }

    /**
     * Get BearerToken for remote NIMBLE EPCIS server
     *
     * @return BearerToken on success; null, otherwise
     */
    //@Cacheable(value = "tokens", sync = true)
    private String getBearerToken(String username, String password) {
        String url = identityServiceURL + "/login";

        log.info("URL:" + url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        JSONObject json = new JSONObject();
        json.put("username", username);
        json.put("password", password);
        HttpEntity<String> entity = new HttpEntity<String>(json.toString(),headers);

        String bearerToken = null;
        try {
            String result = restTemplate.postForObject(url, entity, String.class);

            JSONObject jsonUser = new JSONObject(result);
            String token = jsonUser.getString("accessToken");

            bearerToken = "Bearer " +  token;

        } catch (HttpStatusCodeException e) {
            log.error("Received error during login into NIMBLE platform: " + e.getResponseBodyAsString());
        }

        return bearerToken;

    }
}
