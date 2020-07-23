package org.oliot.model.epcis;

public class NIMBLEUserInfo {
    private String username;
    private String password;
    private String bearerToken;
    private String userPartyID;

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getBearerToken() {
        return bearerToken;
    }
    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }
    public String getUserPartyID() {
        return userPartyID;
    }
    public void setUserPartyID(String userPartyID) {
        this.userPartyID = userPartyID;
    }
}
