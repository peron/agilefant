package fi.hut.soberit.agilefant.web;


import fi.hut.soberit.agilefant.business.UserBusiness;
import fi.hut.soberit.agilefant.model.User;
import fi.hut.soberit.agilefant.security.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.opensymphony.xwork2.ActionSupport;

import fi.hut.soberit.agilefant.business.BacklogBusiness;
import fi.hut.soberit.agilefant.business.SettingBusiness;

@Component("loginContextAction")
@Scope("prototype")
public class LoginContextAction extends ActionSupport {

    private static final long serialVersionUID = -477483113446767662L;

    @Autowired
    private SettingBusiness settingBusiness;
    
    @Autowired
    private BacklogBusiness backlogBusiness;

    @Override
    public String execute(){

        User user = SecurityUtil.getLoggedUser();
        if (user.getEmail() == null || user.getEmail().length() == 0
                || user.getInitials() == null || user.getInitials().length() == 0) {
            return "edit";
        } else if (backlogBusiness.countAll() == 0)
            return "help";
        else if (settingBusiness.isDailyWork()) {
            return "dailyWork";
        }
        else {
            return "selectBacklog";
        }
    }
}
