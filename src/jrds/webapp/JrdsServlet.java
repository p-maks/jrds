package jrds.webapp;

import java.util.Collections;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import jrds.HostsList;
import jrds.PropertiesManager;

public abstract class JrdsServlet extends HttpServlet {
	static final private Logger logger = Logger.getLogger(JrdsServlet.class);

	protected Configuration getConfig() {
		ServletContext ctxt = getServletContext();
		return (Configuration) ctxt.getAttribute(Configuration.class.getName());
	}
	
	protected void setConfig(Configuration config) {
        ServletContext ctxt = getServletContext();
        ctxt.setAttribute(Configuration.class.getName(), config);
	}
	
	protected HostsList getHostsList() {
		return getConfig().getHostsList();
	}

	protected PropertiesManager getPropertiesManager() {
		return getConfig().getPropertiesManager();
	}

	protected ParamsBean getParamsBean(HttpServletRequest request, String... restPath) {
		return new ParamsBean(request, getHostsList(), restPath);
	}
	
	protected boolean allowed(ParamsBean params, Set<String> roles) {
		if(getPropertiesManager().security) {
			if(roles.contains("ANONYMOUS"))
				return true;
			if(logger.isTraceEnabled()) {
				logger.trace("Checking if roles " + params.getRoles() + " in roles " + roles);
				logger.trace("Disjoint: " +  Collections.disjoint(roles, params.getRoles()));
			}
			return ! Collections.disjoint(roles, params.getRoles());
		}
		return true;
	}
	
	protected boolean allowed(ParamsBean params, ACL acl, HttpServletRequest req, HttpServletResponse res) {
		if(getPropertiesManager().security) {				
			boolean allowed = acl.check(params);
			logger.trace(jrds.Util.delayedFormatString("Looking if ACL %s allow access to %s", acl, req.getServletPath()));
			if(! allowed) {
				res.setStatus(HttpServletResponse.SC_FORBIDDEN);
				return false;
			}
		}
		return true;
	}
	
}
