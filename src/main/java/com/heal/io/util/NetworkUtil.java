package com.heal.io.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Utility class for network operations
 */
public class NetworkUtil {

    /**
     * Get the local network IP address (not localhost)
     * @return The local IP address, or "localhost" if not found
     */
    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    // Skip loopback addresses
                    if (address.isLoopbackAddress()) {
                        continue;
                    }
                    
                    // Return the first non-loopback IPv4 address
                    if (address.getHostAddress().indexOf(':') < 0) { // IPv4 address
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            // If we can't determine the IP, return localhost
        }
        
        return "localhost";
    }
}




