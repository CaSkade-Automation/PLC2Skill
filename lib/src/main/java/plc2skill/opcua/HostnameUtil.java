package plc2skill.opcua;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class to get all hostnames. 
 * Copied from Eclipse Milo (https://github.com/eclipse/milo/blob/master/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/util/HostnameUtil.java)
 */
public class HostnameUtil {

	private final static Logger logger = LoggerFactory.getLogger(HostnameUtil.class);

	/**
	 * @return the local hostname, if possible. Failure results in "localhost".
	 */
	public static String getHostname() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return "localhost";
		}
	}

	/**
	 * Given an address resolve it to as many unique addresses or hostnames as can be found.
	 *
	 * @param address the address to resolve.
	 * @return the addresses and hostnames that were resolved from {@code address}.
	 */
	public static Set<String> getHostnames(String address) {
		return getHostnames(address, true);
	}

	/**
	 * Given an address resolve it to as many unique addresses or hostnames as can be found.
	 *
	 * @param address         the address to resolve.
	 * @param includeLoopback if {@code true} loopback addresses will be included in the returned set.
	 * @return the addresses and hostnames that were resolved from {@code address}.
	 */
	public static Set<String> getHostnames(String address, boolean includeLoopback) {
		Set<String> hostnames = new HashSet<>();

		try {
			InetAddress inetAddress = InetAddress.getByName(address);

			if (inetAddress.isAnyLocalAddress()) {
				try {
					Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();

					for (NetworkInterface ni : Collections.list(nis)) {
						Collections.list(ni.getInetAddresses()).forEach(ia -> {
							if (ia instanceof Inet4Address) {
								if (includeLoopback || !ia.isLoopbackAddress()) {
									hostnames.add(ia.getHostName());
									hostnames.add(ia.getHostAddress());
									hostnames.add(ia.getCanonicalHostName());
								}
							}
						});
					}
				} catch (SocketException e) {
					logger.warn("Failed to NetworkInterfaces for bind address: {}", address, e);
				}
			} else {
				if (includeLoopback || !inetAddress.isLoopbackAddress()) {
					hostnames.add(inetAddress.getHostName());
					hostnames.add(inetAddress.getHostAddress());
					hostnames.add(inetAddress.getCanonicalHostName());
				}
			}
		} catch (UnknownHostException e) {
			logger.warn("Failed to get InetAddress for bind address: {}", address, e);
		}

		return hostnames;
	}

}
