package eu.nimble.service.epcis.controller;

import javax.servlet.http.*;
import javax.validation.Valid;
import eu.nimble.service.epcis.form.Login;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;
import java.util.*;

@Controller
@Scope("session")
public class IndexController {

    @Value("#{'${credential.username}'.split(',')}")
    private List<String> usernames;

    @Value("#{'${credential.password}'.split(',')}")
    private List<String> passwords;

    @Value("#{'${credential.accessToken}'.split(',')}")
    private List<String> accessTokens;

    @Autowired
    HttpSession session;

    @Autowired
    HttpServletRequest request;

    @GetMapping("/")
    public String main(Model model) {
        model.addAttribute("login", new Login());
        session.removeAttribute("invalidCredential");
        return "index"; //view
    }

    @PostMapping("/")
    public ModelAndView login(@Valid Login login, BindingResult bindingResult, HttpSession session) {
        ModelAndView modelAndView = new ModelAndView();
        if(bindingResult.hasErrors()) {
            session.removeAttribute("invalidCredential");
            modelAndView.addObject("login", login);
            modelAndView.setViewName("index");
            return modelAndView;
        }

        boolean authenticateUser = false;

        for(String username : usernames){
            if(username.equals(login.getUserName())){
                Integer userIndex=usernames.indexOf(username);
                if(login.getPassWord().equals(passwords.get(userIndex))) {
                    session.setAttribute("accessToken", accessTokens.get(userIndex));
                    modelAndView.setViewName("home");
                    authenticateUser = true;
                    break;
                }
            }
        }

        if(!authenticateUser) {
            session.setAttribute("invalidCredential", "Username or Password is not correct!");
            modelAndView.setViewName("index");
        }

        return modelAndView;
    }

    @GetMapping("/home")
    public ModelAndView home() {
        return checkAuthenticateAndRedirect();
    }


    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("accessToken");
        return "redirect:/";
    }

    @GetMapping("/json-event-capture")
    public ModelAndView jsonEventCapture() {
        return checkAuthenticateAndRedirect();
    }

    @GetMapping("/json-event-query")
    public ModelAndView jsonEventQuery() {
        return checkAuthenticateAndRedirect();
    }

    @GetMapping("/json-master-single-capture")
    public ModelAndView jsonMasterSingleCapture() {
        return checkAuthenticateAndRedirect();
    }

    @GetMapping("/json-master-multiple-capture")
    public ModelAndView jsonMasterMultipleCapture() {
        return checkAuthenticateAndRedirect();
    }

    @GetMapping("json-master-query")
    public ModelAndView jsonMasterQuery() {
        return checkAuthenticateAndRedirect();
    }

    @GetMapping("/xml-event-capture")
    public ModelAndView xmlEventCapture() {
       return checkAuthenticateAndRedirect();
    }

    @GetMapping("/xml-event-query")
    public ModelAndView xmlEventQuery() {
        return checkAuthenticateAndRedirect();
    }

    @GetMapping("/xml-master-capture")
    public ModelAndView xmlMasterCapture() {
        return checkAuthenticateAndRedirect();
    }

    @GetMapping("xml-master-query")
    public ModelAndView xmlMasterQuery() {
        return checkAuthenticateAndRedirect();
    }

    @GetMapping("/json-production-capture")
    public ModelAndView jsonProductionCapture() {
        return checkAuthenticateAndRedirect();
    }

    @GetMapping("/json-production-query")
    public ModelAndView jsonProductionQuery() {
        return checkAuthenticateAndRedirect();
    }

    private ModelAndView checkAuthenticateAndRedirect() {
        ModelAndView modelAndView = new ModelAndView();
        if( session.getAttribute("accessToken") != null) {
            modelAndView.addObject("currentUrl", request.getRequestURI().replace("/", ""));
        } else {
            modelAndView.addObject("login", new Login());
            modelAndView.setViewName("index");
        }
        return modelAndView;
    }
}
