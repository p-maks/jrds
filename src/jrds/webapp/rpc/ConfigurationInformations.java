package jrds.webapp.rpc;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import jrds.HostInfo;
import jrds.webapp.Configuration;
import jrds.webapp.rpc.JrdsRequestProcessorFactoryFactory.InitializableRequestProcessor;
import jrds.webapp.rpc.Role.RoleList;

import org.apache.xmlrpc.XmlRpcException;

/**
 * @author bacchell
 *
 */
public class ConfigurationInformations implements InitializableRequestProcessor {
	/**
	 * The name for the xml-rpc handler
	 */
	static final public String REMOTENAME = "configurationinformations";

	Configuration config;

	@Role(RoleList.USER)
	public Object[] getHostsName() {
		Collection<HostInfo> hostsList = config.getHostsList().getHosts();
		Set<String> hosts = new HashSet<String>(hostsList.size());
		for(HostInfo host: hostsList) {
			hosts.add(host.getName());
		}
		return hosts.toArray();
	}

	public void init(Configuration config) throws XmlRpcException {
		this.config = config;
	}
}
