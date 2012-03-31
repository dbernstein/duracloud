/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.duradmin.control;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.duracloud.client.ContentStoreManager;
import org.duracloud.duradmin.util.MessageUtils;
import org.duracloud.duradmin.util.NavigationUtils;
import org.duracloud.serviceapi.ServicesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.binding.message.Message;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractCommandController;
import org.springframework.web.servlet.view.RedirectView;

public abstract class BaseCommandController
        extends AbstractCommandController {

    public static final String APP_NAME = "DurAdmin";

    protected final Logger log = LoggerFactory.getLogger(BaseCommandController.class);

    @Autowired
    protected ControllerSupport controllerSupport;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request,
                                      HttpServletResponse response)
            throws Exception {
        ModelAndView mav = super.handleRequest(request, response);
        return controllerSupport.handle(mav, request, response);
    }

    public ContentStoreManager getContentStoreManager() {
        return controllerSupport.getContentStoreManager();
    }

    protected ServicesManager getServicesManager() throws Exception {
        return controllerSupport.getServicesManager();
    }

    public void setControllerSupport(ControllerSupport controllerSupport) {
        this.controllerSupport = controllerSupport;
    }

    protected ModelAndView setView(HttpServletRequest request,
                                   ModelAndView mav,
                                   Message message) {
        String url = NavigationUtils.getReturnTo(request);
        url = MessageUtils.appendRedirectMessage(url, message, request);
        RedirectView redirect = new RedirectView(url, false);
        mav.setView(redirect);
        return mav;
    }

}
