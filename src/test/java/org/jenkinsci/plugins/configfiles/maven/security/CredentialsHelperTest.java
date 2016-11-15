
package org.jenkinsci.plugins.configfiles.maven.security;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CredentialsHelperTest {

    final static String PWD     = "MY_NEW_PWD";
    final static String PWD_2   = "{COQLCE6DU6GtcS5P=}";

    @Rule
    public JenkinsRule  jenkins = new JenkinsRule();

    @Test
    public void testIfServerAuthIsReplacedWithinSettingsXmlWhenReplaceTrue() throws Exception {

        Map<String, StandardUsernameCredentials> serverId2Credentials = new HashMap<String, StandardUsernameCredentials>();
        serverId2Credentials.put("my.server", new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "my*", "some desc", "peter", PWD));
        serverId2Credentials.put("encoded_pwd", new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "*pwd", "some desc2", "dan", PWD_2));

        final String settingsContent = IOUtils.toString(CredentialsHelperTest.class.getResourceAsStream("/settings_test.xml"));

        List<String> tempFiles = new ArrayList<String>();
        final String replacedContent = CredentialsHelper.fillAuthentication(settingsContent, Boolean.TRUE, serverId2Credentials, jenkins.jenkins.createPath("tmp"), tempFiles);

        Assert.assertTrue("replaced settings.xml must contain new password", replacedContent.contains(PWD));

        // ensure it is still a valid XML document
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(replacedContent)));
        // locate the node(s)
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList nodes = (NodeList) xpath.evaluate("/settings/servers/server", doc, XPathConstants.NODESET);
        // the 'settings_test.xml' actually contains 4 server tags, but we remove all and only inject the ones managed by the plugin
        Assert.assertEquals("there is not the same number of server tags anymore in the settings.xml", 2, nodes.getLength());

        Node server = (Node) xpath.evaluate("/settings/servers/server[id='my.server']", doc, XPathConstants.NODE);
        Assert.assertEquals("password is not at the correct location", PWD, xpath.evaluate("password", server));
        Assert.assertEquals("username is not set correct", "peter", xpath.evaluate("username", server));

        Node server2 = (Node) xpath.evaluate("/settings/servers/server[id='encoded_pwd']", doc, XPathConstants.NODE);
        Assert.assertEquals("password is not set correct", PWD_2, xpath.evaluate("password", server2));
        Assert.assertEquals("username is not set correct", "dan", xpath.evaluate("username", server2));

    }

    @Test
    public void testIfServerAuthIsReplacedWithinSettingsXmlWhenReplaceFalse() throws Exception {

        Map<String, StandardUsernameCredentials> serverId2Credentials = new HashMap<String, StandardUsernameCredentials>();
        serverId2Credentials.put("my.server", new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "my*", "some desc", "peter", PWD));
        serverId2Credentials.put("encoded_pwd", new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "*pwd", "some desc2", "dan", PWD_2));

        final String settingsContent = IOUtils.toString(CredentialsHelperTest.class.getResourceAsStream("/settings_test.xml"));
        List<String> tempFiles = new ArrayList<String>();
        final String replacedContent = CredentialsHelper.fillAuthentication(settingsContent, Boolean.FALSE, serverId2Credentials, jenkins.jenkins.createPath("tmp"), tempFiles);

        Assert.assertTrue("replaced settings.xml must contain new password", replacedContent.contains(PWD));

        // ensure it is still a valid XML document
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(replacedContent)));
        // locate the node(s)
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList nodes = (NodeList) xpath.evaluate("/settings/servers/server", doc, XPathConstants.NODESET);
        // the 'settings_test.xml' actually contains 4 server tags, but we remove all and only inject the ones managed by the plugin
        Assert.assertEquals("there is not the same number of server tags anymore in the settings.xml", 4, nodes.getLength());

    }

    @Test
    public void testSettingsXmlIsNotChangedWithoutCredentialsWhenReplaceTrue() throws Exception {

        Map<String, StandardUsernameCredentials> serverId2Credentials = new HashMap<String, StandardUsernameCredentials>();

        final String settingsContent = IOUtils.toString(CredentialsHelperTest.class.getResourceAsStream("/settings_test.xml"));
        List<String> tempFiles = new ArrayList<String>();
        final String replacedContent = CredentialsHelper.fillAuthentication(settingsContent, Boolean.TRUE, serverId2Credentials, jenkins.jenkins.createPath("tmp"), tempFiles);

        Assert.assertEquals("no changes should have been made to the settings", settingsContent, replacedContent);

    }

    @Test
    public void testSettingsXmlIsNotChangedWithoutCredentialsWhenReplaceFalse() throws Exception {

        Map<String, StandardUsernameCredentials> serverId2Credentials = new HashMap<String, StandardUsernameCredentials>();

        final String settingsContent = IOUtils.toString(CredentialsHelperTest.class.getResourceAsStream("/settings_test.xml"));
        List<String> tempFiles = new ArrayList<String>();
        final String replacedContent = CredentialsHelper.fillAuthentication(settingsContent, Boolean.FALSE, serverId2Credentials, jenkins.jenkins.createPath("tmp"), tempFiles);

        Assert.assertEquals("no changes should have been made to the settings", settingsContent, replacedContent);

    }
}
