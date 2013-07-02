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

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.OutputStream;
import javax.swing.event.EventListenerList;

import org.apache.log4j.Logger;

/**
 * Thread created to send message to the remote peer. Hold a queue for outgoing
 * messages
 * 
 * @author Baptiste Dubuis
 * @version 0.1
 * 
 */
public class MessageSender extends Thread {
	
	private static Logger logger = Logger.getLogger(MessageSender.class);
	
    private OutputStream os = null;
    private LinkedBlockingQueue<Message> outgoingMessage = null;
    @SuppressWarnings("unused")
	private long lmst = 0;
    private boolean run = true;
    private final EventListenerList listeners = new EventListenerList();


    public MessageSender(String id, OutputStream os) {
        //this.setName("MS_"+id);
        this.os = os;
        this.outgoingMessage = new LinkedBlockingQueue<Message>();
    }

    public void addOutgoingListener(OutgoingListener listener) {
        listeners.add(OutgoingListener.class, listener);
    }

    public void removeOutgoingListener(OutgoingListener listener) {
       listeners.remove(OutgoingListener.class, listener);
   }

    public OutgoingListener[] getOutgoingListeners() {
        return listeners.getListeners(OutgoingListener.class);
    }

    /**
     * Called when the connection to the peer has been closed. Advertise the
     * DownloadTask that there is no more connection to the remote peer.
     */
    protected void fireConnectionClosed() {
        for (OutgoingListener listener : getOutgoingListeners()) {
            listener.connectionClosed();
        }
    }

    /**
     * Called when a keep-alive message has been sent. This happens about every
     * 2 minutes if there has not been any other messages sent to avoid the
     * connection to be closed by the remote peer
     */
    protected void fireKeepAliveSent() {
    	
        for (OutgoingListener listener : getOutgoingListeners()) {
            listener.keepAliveSent();
        }
    }

    /**
     * Puts the message in parameter in the queue, waiting to be sent
     * @param m Message
     */
    public synchronized void addMessageToQueue(Message m){
        this.outgoingMessage.add(m);
        this.lmst = System.currentTimeMillis();
        //this.notify();
    }

    /**
     * Sends messages from the queue. While the queue is not empty, polls message
     * from it and sends it to the remote peer. If the queue is empty for more than
     * 2 minutes, a keep-alive message is sent and the DownloadTask is advertised
     */
    public void run() {
        Message out = null;
        byte[] keep = new Message_PP(PeerProtocol.KEEP_ALIVE).generate();
        try {
            while (this.run) {
                if(this.outgoingMessage != null && this.os != null)
                	out = outgoingMessage.poll(120000, TimeUnit.MILLISECONDS);
                if(out != null && this.run){
                    os.write(out.generate());
                    this.lmst = System.currentTimeMillis();
                    out = null;
                }else if(this.run){
                	logger.warn("MessegeSender out == null");
                    os.write(keep);
                    this.fireKeepAliveSent();
                }
            }
        } catch (InterruptedException ie) {
        } catch(IOException ioe){
        	logger.warn("MessageSender IOException: " + ioe);
            this.fireConnectionClosed();
        } catch(Exception e){
        	logger.warn("MessageSender Exception: " + e);
            this.fireConnectionClosed();
        }

        if(this.outgoingMessage != null)
            this.outgoingMessage.clear();
        this.outgoingMessage = null;
        try{
            this.os.close();
            this.os = null;
            this.notify();
        }catch(Exception e){}

    }

    /**
     * Sets the 'run' variable to false, causing the thread to stop on its next
     * loop.
     */
    public void stopThread(){
        this.run = false;
    }

}
