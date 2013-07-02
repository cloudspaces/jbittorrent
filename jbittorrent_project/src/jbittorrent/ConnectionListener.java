/*
 * jbittorrent library is an implementation in Java language of BiTorrent protocol.
 *
 * It is based on the Java Bittorrent API of Baptiste Dubuis, Artificial Inteligency Laboratory, EPFL.
 * @version 1.0
 * @author Baptiste Dubuis
 * To contact the author:
 * email: baptiste.dubuis@gmail.com
 *
 * More information about Java Bittorrent API:
 * http://sourceforge.net/projects/bitext/
 *
 * New contribution are:
 * 1. Optimization of process establishement of conecctions betwen the peers of the swarm.
 * 2. Improvements in the Choking Algorithm.
 * 3. Improvements in the Optimistic Unchoking implementation.
 * 4. Implementation of Rarest First algorithm .
 * 5. Implementation of End Game Strategy.
 *
 * This project contains three packs:
 * 1. jbittorrent is the "client" part, i.e. it implements all classes needed to publish files, share them and download them.
 * 2. trackerBT is the "tracker" part, i.e. it implements all classes needed to run a Bittorrent tracker that coordinates peers exchanges.
 * 3. test contains example classes on how a developer could create new applications and new .torrent file.
 *
 * Copyright (C) 2013 Sandra Ferrer, AST Research Group
 *
 * jbittorrent is free software; you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published 
 * by the Free Software Foundation; either version 2 of the License, 
 * or (at your option) any later version.
 *
 * jbittorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty 
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  @version 1.0
 *  @author Sandra Ferrer Celma <sandra.ferrer@urv.cat>
 * 
 */

package jbittorrent;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

import javax.swing.event.EventListenerList;

import org.apache.log4j.Logger;

/**
 * Thread that can listen for remote peers connection tries to this client
 *
 * @author Baptiste Dubuis
 * @version 0.1
 */
public class ConnectionListener extends Thread {
	
	private static Logger logger = Logger.getLogger(ConnectionListener.class);
	
    private ServerSocket ss = null;
    private int minPort = -1;
    private int maxPort = -1;
    private int connectedPort = -1;
    private final EventListenerList listeners = new EventListenerList();
    private boolean acceptConnection = true;
    
    private String ipAddress = null;

    public ConnectionListener() {}
    public ConnectionListener(int minPort, int maxPort){
        this.minPort = minPort;
        this.maxPort = maxPort;
    }

    /**
     * Returns the port this client is listening on
     * @return int
     */
    public int getConnectedPort(){
        return this.connectedPort;
    }

    /**
     * Returns the minimal port number this client will try to listen on
     * @return int
     */
    public int getMinPort(){
        return this.minPort;
    }

    /**
     * Returns the maximal port number this client will try to listen on
     * @return int
     */
    public int getMaxPort(){
        return this.maxPort;
    }

    /**
     * Sets the minimal port number this client will try to listen on
     * @param minPort int
     */
    public void setMinPort(int minPort){
        this.minPort = minPort;
    }

    /**
     * Sets the minimal port number this client will try to listen on
     * @param maxPort int
     */
    public void setMaxPort(int maxPort){
        this.maxPort = maxPort;
    }
    
    
    /**
     * Return IP addresses for this node 
     * @return String IP addresses
     */
    public static String getIpAddress(){
    	
    	String ip = null;
    	
    	Enumeration<NetworkInterface> e;
		try {
			e = NetworkInterface.getNetworkInterfaces();
			 while(e.hasMoreElements())
	            {
	                NetworkInterface n=(NetworkInterface) e.nextElement();
	                Enumeration<InetAddress> ee = n.getInetAddresses();
	                while(ee.hasMoreElements())
	                {
	                    InetAddress iA= (InetAddress) ee.nextElement();
	                    if(!iA.isLoopbackAddress() && !iA.getHostAddress().contains("192.168.") && !iA.getHostAddress().contains(":")){
	                    	ip = iA.getHostAddress();
	                    }
	                }
	            }
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		return ip;
    }

    /**
     * Try to create a server socket for remote peers to connect on within the
     * specified port range
     * @param minPort The minimal port number this client should listen on
     * @param maxPort The maximal port number this client should listen on
     * @return boolean
     */
    public boolean connect(int minPort, int maxPort){
        this.minPort = minPort;
        this.maxPort = maxPort;
        for(int i = minPort; i <= maxPort; i++)
            try {
                this.ss = new ServerSocket(i);
                this.connectedPort = i;
                
            	this.ipAddress = ConnectionListener.getIpAddress();
            	logger.info("PEER IP_ADDRESS: "+this.ipAddress);
            	
                this.setDaemon(true);
                this.start();               
                return true;
            } catch (IOException ioe) {}
        return false;
    }

    /**
     * Try to create a server socket for remote peers to connect on within current
     * port range
     * @return boolean
     */
    public boolean connect(){
        if(this.minPort != -1 && this.maxPort != -1)
            return this.connect(this.minPort, this.maxPort);
        else
            return false;
    }

    public void run() {
        byte[] b = new byte[0];
        Socket connection = null;
        try {
            while (true) {
                if(this.acceptConnection){
                	
                	connection = ss.accept();
                	
                    this.fireConnectionAccepted(connection);
                    sleep(1000);
                }else{
                    synchronized(b){
                        System.out.println("No more connection accepted for the moment...");
                        b.wait();
                    }
                }
            }
        } catch (IOException ioe) {
            System.err.println("Error in connection listener: "+ioe.getMessage());
            System.err.flush();
        } catch(InterruptedException ie){

        }
    }

    /**
     * Decides if the client should accept or not future connection
     * @param accept true if it should accept, false otherwise
     */
    public synchronized void setAccept(boolean accept){
        this.acceptConnection = accept;
        this.notifyAll();
    }


    public void addConListenerInterface(ConListenerInterface listener) {
        listeners.add(ConListenerInterface.class, listener);
    }

    public void removeConListenerInterface(ConListenerInterface listener) {
        listeners.remove(ConListenerInterface.class, listener);
    }

    public ConListenerInterface[] getConListenerInterfaces() {
        return listeners.getListeners(ConListenerInterface.class);
    }

    /**
     * Method used to send message to all object currently listening on this thread
     * when a new connection has been accepted. It provides the socket the connection
     * is bound to.
     *
     * @param s Socket
     */
    protected void fireConnectionAccepted(Socket s) {
        for (ConListenerInterface listener : getConListenerInterfaces()) {
            listener.connectionAccepted(s);
        }
    }

    public String getIPaddress(){    	
    	return this.ipAddress;
    }
}
