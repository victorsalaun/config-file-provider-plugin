
package org.jenkinsci.plugins.configfiles.properties.security;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.configfiles.maven.security.MavenServerIdRequirement;
import org.jenkinsci.plugins.configfiles.maven.security.ServerCredentialMapping;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CredentialsHelper {

    private static final Logger LOGGER = Logger.getLogger(CredentialsHelper.class.getName());

    /**
     * hide constructor
     */
    private CredentialsHelper() {
    }

    public static Map<String, StandardUsernameCredentials> resolveCredentials(Run<?, ?> build, final List<PropertiesCredentialMapping> propertiesCredentialMappings, TaskListener listener) {
        Map<String, StandardUsernameCredentials> propertiesCredentialsMap = new HashMap<>();
        for (PropertiesCredentialMapping propertiesCredentialMapping : propertiesCredentialMappings) {
            final String credentialsId = propertiesCredentialMapping.getCredentialsId();
            final String propertyKey = propertiesCredentialMapping.getPropertyKey();

            List<DomainRequirement> domainRequirements = Collections.emptyList();
            if (StringUtils.isNotBlank(propertyKey)) {
                domainRequirements = Collections.singletonList(new PropertyKeyRequirement(propertyKey));
            }

            final StandardUsernameCredentials c = CredentialsProvider.findCredentialById(credentialsId, StandardUsernameCredentials.class, build, domainRequirements);

            if (c != null) {
                propertiesCredentialsMap.put(propertyKey, c);
            } else {
                listener.getLogger().println("Could not find credentials ‘" + credentialsId + "’ for " + build);
            }
        }
        return propertiesCredentialsMap;
    }

    public static String fillAuthentication(String propertiesContent, final Boolean isReplaceAllPropertyDefinitions,
                                            Map<String, StandardUsernameCredentials> propertiesCredentialsMap) {
        String content = propertiesContent;

        List<String> propertiesArray = new ArrayList<>();
        propertiesArray.addAll(Arrays.asList(content.split("[\\r\\n]+")));

        //
        int index;
        for (index = 0; index < propertiesArray.size(); index++) {
            String property = propertiesArray.get(index);
            if (!property.contains("=")) {
                continue;
            }
            String[] propertyParts = property.split("=");
            if (propertiesCredentialsMap.containsKey(propertyParts[0])) {
                if (isReplaceAllPropertyDefinitions) {
                    final StandardUsernameCredentials credential = propertiesCredentialsMap.get(propertyParts[0]);
                    propertiesArray.set(index, createCredentialBasedProperty(propertyParts[0], credential));
                } else {
                    propertiesCredentialsMap.remove(propertyParts[0]);
                }
            }
        }

        for (String propertyKey : propertiesCredentialsMap.keySet()) {
            propertiesArray.add(createCredentialBasedProperty(propertyKey, propertiesCredentialsMap.get(propertyKey)));
        }

        content = String.join("\r\n", propertiesArray);

        StringWriter writer = new StringWriter();
        writer.write(content);
        content = writer.toString();

        return content;
    }

    private static String createCredentialBasedProperty(final String propertyKey, final StandardUsernameCredentials credential) {
        String propertyValue = "";
        if (credential instanceof StandardUsernamePasswordCredentials) {
            StandardUsernamePasswordCredentials usernamePasswordCredentials = (StandardUsernamePasswordCredentials) credential;
            propertyValue = usernamePasswordCredentials.getPassword().getPlainText();
        } else if (credential instanceof SSHUserPrivateKey) {
            SSHUserPrivateKey sshUserPrivateKey = (SSHUserPrivateKey) credential;
            List<String> privateKeys = sshUserPrivateKey.getPrivateKeys();
            if (privateKeys.isEmpty()) {
                LOGGER.log(Level.WARNING, "Property {0}: not private key defined in {1}, skip", new Object[]{propertyKey, sshUserPrivateKey.getId()});
            } else if (privateKeys.size() == 1) {
                LOGGER.log(Level.FINE, "Property {0}: use {1}", new Object[]{propertyKey, sshUserPrivateKey.getId()});
                propertyValue = privateKeys.get(0);
            } else {
                LOGGER.log(Level.WARNING, "Property {0}: more than one ({1}) private key defined in {1}, use first private key", new Object[]{propertyKey, privateKeys.size(), sshUserPrivateKey.getId()});
                propertyValue = privateKeys.get(0);
            }
        } else {
            LOGGER.log(Level.WARNING, "Property {0}: credentials type of {1} not supported: {2}",
                    new Object[]{propertyKey, credential == null ? null : credential.getId(), credential == null ? null : credential.getClass()});
        }
        return propertyKey + "=" + propertyValue;
    }

}
