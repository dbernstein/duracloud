/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.duradmin.control;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.input.AutoCloseInputStream;
import org.duracloud.appconfig.domain.DuradminConfig;
import org.duracloud.appconfig.xml.DuradminInitDocumentBinding;
import org.duracloud.client.ContentStoreManager;
import org.duracloud.duradmin.domain.AdminInit;
import org.easymock.classextension.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Andrew Woods
 *         Date: 4/18/11
 */
public class InitControllerTest {

    private InitController controller;

    private String durastoreHost = "durastore-host";
    private String durastorePort = "111";
    private String durastoreContext = "durastore-context";
    private String duraserviceHost = "duraservice-host";
    private String duraservicePort = "222";
    private String duraserviceContext = "duraservice-context";
    private String amaUrl = "http://a.com";

    private HttpServletRequest request;
    private HttpServletResponse response;
    private final Object o = null;
    private final BindException be = null;

    @Before
    public void setUp() throws Exception {
        ContentStoreManager contentStoreManager = EasyMock.createMock(
            "ContentStoreManager",
            ContentStoreManager.class);
        contentStoreManager.reinitialize((String)EasyMock.anyObject(), (String)EasyMock.anyObject(), (String)EasyMock.anyObject());
        EasyMock.expectLastCall();

        ControllerSupport support = EasyMock.createMock("ControllerSupport",
                                                        ControllerSupport.class);
        EasyMock.expect(support.getContentStoreManager()).andReturn(contentStoreManager);

        controller = new InitController();
        controller.setControllerSupport(support);

        EasyMock.replay(support, contentStoreManager);
    }

    @After
    public void tearDown() throws Exception {
        EasyMock.verify(request, response);
        resetDuradminConfig();
    }

    private void resetDuradminConfig() {
        org.duracloud.duradmin.config.DuradminConfig.setConfig(new AdminInit());
    }

    @Test
    public void testInit() throws Exception {
        doTest(SC_OK);
    }

    @Test
    public void testInitFail400() throws Exception {
        doTest(SC_BAD_REQUEST);
    }

    @Test
    public void testInitFail500() throws Exception {
        doTest(SC_INTERNAL_SERVER_ERROR);
    }

    private void doTest(int status) throws Exception {
        createMocks(status);

        ModelAndView mav = controller.handle(request, response, o, be);
        Assert.assertNotNull(mav);

        org.duracloud.duradmin.config.DuradminConfig config = new org.duracloud.duradmin.config.DuradminConfig();

        String storeHost = config.getDuraStoreHost();
        String storePort = config.getDuraStorePort();
        String storeCtxt = config.getDuraStoreContext();
        String serviceHost = config.getDuraServiceHost();
        String servicePort = config.getDuraServicePort();
        String serviceCtxt = config.getDuraServiceContext();
        String url = config.getAmaUrl();

        if (status == SC_OK) {
            Assert.assertNotNull(storeHost);
            Assert.assertNotNull(storePort);
            Assert.assertNotNull(storeCtxt);
            Assert.assertNotNull(serviceHost);
            Assert.assertNotNull(servicePort);
            Assert.assertNotNull(serviceCtxt);
            Assert.assertNotNull(url);

            Assert.assertEquals(durastoreHost, storeHost);
            Assert.assertEquals(durastorePort, storePort);
            Assert.assertEquals(durastoreContext, storeCtxt);
            Assert.assertEquals(duraserviceHost, serviceHost);
            Assert.assertEquals(duraservicePort, servicePort);
            Assert.assertEquals(duraserviceContext, serviceCtxt);
            Assert.assertEquals(amaUrl, url);

        } else {
            Assert.assertNull(storeHost);
            Assert.assertNull(storePort);
            Assert.assertNull(storeCtxt);
            Assert.assertNull(serviceHost);
            Assert.assertNull(servicePort);
            Assert.assertNull(serviceCtxt);
            Assert.assertNull(url);
        }

    }

    private void createMocks(int status) throws IOException {
        request = EasyMock.createMock("HttpServletRequest",
                                      HttpServletRequest.class);
        EasyMock.expect(request.getMethod()).andReturn("POST");

        ServletInputStream configStream = createConfigStream(status);
        EasyMock.expect(request.getInputStream()).andReturn(configStream);

        response = EasyMock.createMock("HttpServletResponse",
                                       HttpServletResponse.class);
        response.setStatus(status);
        EasyMock.expectLastCall();

        EasyMock.replay(request, response);
    }

    private ServletInputStream createConfigStream(int status) {

        final InputStream in;
        String xml;
        if (status == SC_OK) {
            xml = createConfigXml(createDuradminConfig());
            in = new AutoCloseInputStream(new ByteArrayInputStream(xml.getBytes()));

        } else if (status == SC_INTERNAL_SERVER_ERROR) {
            xml = "junk";
            in = new AutoCloseInputStream(new ByteArrayInputStream(xml.getBytes()));

        } else {
            in = null;
        }

        ServletInputStream stream = new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return in.read();
            }
        };

        if (status == SC_BAD_REQUEST) {
            stream = null;
        }

        return stream;
    }

    private String createConfigXml(DuradminConfig duradminConfig) {
        return DuradminInitDocumentBinding.createDocumentFrom(duradminConfig);
    }

    private DuradminConfig createDuradminConfig() {
        DuradminConfig config = new DuradminConfig();

        String p = DuradminConfig.QUALIFIER + ".";

        Map<String, String> props = new HashMap<String, String>();
        props.put(p + DuradminConfig.duraStoreHostKey, durastoreHost);
        props.put(p + DuradminConfig.duraStorePortKey, durastorePort);
        props.put(p + DuradminConfig.duraStoreContextKey, durastoreContext);
        props.put(p + DuradminConfig.duraServiceHostKey, duraserviceHost);
        props.put(p + DuradminConfig.duraServicePortKey, duraservicePort);
        props.put(p + DuradminConfig.duraServiceContextKey, duraserviceContext);
        props.put(p + DuradminConfig.amaUrlKey, amaUrl);

        config.load(props);

        return config;
    }
}
