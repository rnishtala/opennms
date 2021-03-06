package org.opennms.web.springframework.security;

import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import org.opennms.netmgt.config.api.UserConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;

public class OpenNMSLoginModule implements LoginModule, LoginHandler {
    private static final transient Logger LOG = LoggerFactory.getLogger(OpenNMSLoginModule.class);

    private static transient volatile UserConfig m_userConfig;
    private static transient volatile SpringSecurityUserDao m_springSecurityUserDao;

    protected Subject m_subject;
    protected CallbackHandler m_callbackHandler;
    protected Map<String, ? super Object> m_sharedState;
    protected Map<String, ? super Object> m_options;

    protected String m_user;
    protected Set<Principal> m_principals = new HashSet<Principal>();

    @Override
    public void initialize(final Subject subject, final CallbackHandler callbackHandler, final Map<String, ?> sharedState, final Map<String, ?> options) {
        LOG.info("OpenNMS Login Module initializing.");
        m_subject = subject;
        m_callbackHandler = callbackHandler;
    }

    @Override
    public boolean login() throws LoginException {
        return LoginModuleUtils.doLogin(this);
    }

    @Override
    public boolean abort() throws LoginException {
        LOG.debug("Aborting {} login.", m_user);
        m_user = null;
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        LOG.debug("Logging out user {}.", m_user);
        m_subject.getPrincipals().removeAll(m_principals);
        m_principals.clear();
        return true;
    }

    public static synchronized UserConfig getUserConfig() {
        return m_userConfig;
    }

    public static synchronized void setUserConfig(final UserConfig userConfig) {
        m_userConfig = userConfig;
    }

    public static synchronized SpringSecurityUserDao getSpringSecurityUserDao() {
        return m_springSecurityUserDao;
    }

    public static synchronized void setSpringSecurityUserDao(final SpringSecurityUserDao userDao) {
        m_springSecurityUserDao = userDao;
    }

    @Override
    public boolean commit() throws LoginException {
        final Set<Principal> principals = principals();
        if (principals.isEmpty()) {
            return false;
        }
        m_subject.getPrincipals().addAll(principals);
        return true;
    }

    public CallbackHandler callbackHandler() {
        return m_callbackHandler;
    }

    @Override
    public UserConfig userConfig() {
        return m_userConfig;
    }

    @Override
    public SpringSecurityUserDao springSecurityUserDao() {
        return m_springSecurityUserDao;
    }

    @Override
    public String user() {
        return m_user;
    }

    @Override
    public void setUser(final String user) {
        m_user = user;
    }

    public Set<Principal> createPrincipals(final GrantedAuthority authority) {
        return Collections.singleton(new AuthorityPrincipal(authority));
    }

    @Override
    public Set<Principal> principals() {
        return m_principals;
    }

    @Override
    public void setPrincipals(final Set<Principal> principals) {
        m_principals = principals;
    }
}
