package eu.nimble.service.epcis.form;

import org.hibernate.validator.constraints.NotEmpty;

public class Login {

    @NotEmpty
    private String userName;

    @NotEmpty
    private String passWord;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassWord() {
        return passWord;
    }

    public void setPassWord(String passWord) {
        this.passWord = passWord;
    }
}
